package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.benmanes.caffeine.cache.Cache;
import com.hmdp.config.BloomFilterInitializer;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisData;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.unit.DistanceUnit;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private BloomFilterInitializer bloomFilterInitializer;

    //这里需要声明一个线程池，因为下面缓存击穿问题，我们需要新建一个线程来完成重构缓存
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    @Resource
    private Cache<String,Object> caffeineCache;

    @Resource
    private RestHighLevelClient restHighLevelClient;


    @Override
    public Result queryById(Long id) {
        Object o = caffeineCache.getIfPresent(CACHE_SHOP_KEY + id);
        if(Objects.nonNull(o)){
            log.info("从Caffeine中查询到数据...");
            System.out.println(o);
            return Result.ok( o);
        }
//        System.out.println("-----------");
        Shop shop = querywithchuantou(id);
        //利用互斥锁解决缓存击穿的代码逻辑
//        Shop shop = querywithjichuan_mutex(id);
//       Shop shop = queryWithLogicalExpire(id);

        if(shop != null){
            log.info("从Redis中查到数据");
            caffeineCache.put(CACHE_SHOP_KEY+id,shop);
        }
        if (shop == null) {

            return Result.fail("店铺不存在！！");
        }
        return Result.ok(shop);
    }

    //解决缓存穿透的代码
    public Shop querywithchuantou(Long id) {
        //布隆过滤器校验
        if(!bloomFilterInitializer.mightContain(id)) {
            //一定不存在，返回null
            System.out.println("zzy");
            return null;
        }
            //先从Redis中查，这里的常量值是固定的前缀 + 店铺id
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        //如果不为空（查询到了），则转为Shop类型直接返回
        if (StrUtil.isNotBlank(shopJson)) {
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }

        //如果这个数据不存在，将这个数据写入到Redis中，并且将value设置为空字符串，然后设置一个较短的TTL，返回错误信息。
        // 当再次发起查询时，先去Redis中判断value是否为空字符串，如果是空字符串，则说明是刚刚我们存的不存在的数据，直接返回错误信息

        //如果查询到的是空字符串，则说明是我们缓存的空数据
        if (shopJson!=null) {
            return  null;
        }

        //否则去数据库中查
        Shop shop = getById(id);

        //查不到，则将空字符串写入Redis
        if (shop == null) {
            //这里的常量值是2分钟
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, null, CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }

        //查到了则转为json字符串
        String jsonStr = JSONUtil.toJsonStr(shop);
        //并存入redis,并设置TTL，防止存了错的缓存
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, jsonStr,CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //最终把查询到的商户信息返回给前端
        return shop;
    }

    //互斥锁解决缓存击穿
    public Shop querywithjichuan_mutex(Long id) {
        //先从Redis中查，这里的常量值是固定的前缀 + 店铺id
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        //如果不为空（查询到了），则转为Shop类型直接返回
        if (StrUtil.isNotBlank(shopJson)) {
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }

        //如果查询到的是空字符串“”，则说明是我们缓存的空数据
        if (shopJson!=null) {
            return  null;
        }

        //实现在高并发的情况下缓存重建
        Shop shop = null;
        try {
            //1.获取互斥锁
            boolean flag = tryLock(LOCK_SHOP_KEY + id);
//        2.失败，则休眠并重试
            while (!flag) {
                Thread.sleep(50);
                return querywithjichuan_mutex(id);
            }
            //3.获取成功->读取数据库，重建缓存
            //查不到，则将空值写入Redis
            shop = getById(id);
            if (shop == null) {
                stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            //查到了则转为json字符串
            String jsonStr = JSONUtil.toJsonStr(shop);
            //并存入redis，设置TTL
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, jsonStr, CACHE_SHOP_TTL, TimeUnit.MINUTES);
            //最终把查询到的商户信息返回给前端
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            unlock(LOCK_SHOP_KEY + id);
        }
        return shop;
    }

    //逻辑过期解决缓存击穿
    public Shop queryWithLogicalExpire(Long id) {
        //1. 从redis中查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        //2. 如果未命中，则返回空
        if (StrUtil.isBlank(json)) {
            return null;
        }
        //3. 命中，将json反序列化为对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        //3.1 将data转为Shop对象
        JSONObject shopJson = (JSONObject) redisData.getData();
        Shop shop = JSONUtil.toBean(shopJson, Shop.class);
        //3.2 获取过期时间
        LocalDateTime expireTime = redisData.getExpireTime();
        //4. 判断是否过期
        if (LocalDateTime.now().isBefore(expireTime)) {
            //5. 未过期，直接返回商铺信息
            return shop;
        }
        //6. 过期，尝试获取互斥锁
        boolean flag = tryLock(LOCK_SHOP_KEY + id);
        //7. 获取到了锁
        if (flag) {
            //8. 开启独立线程
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    this.saveShop2Redis(id, 20L);//此处的expirSeconds应该为物品的活动时间,设置为20只为测试
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    unlock(LOCK_SHOP_KEY + id);
                }
            });
            //9. 直接返回商铺信息
            return shop;
        }
        //10. 未获取到锁，直接返回商铺信息
        return shop;
    }

    @Override
    @Transactional
    public Result update(Shop shop) {
//        首先先判一下空
        if (shop.getId() == null){
            return Result.fail("店铺id不能为空！！");
        }
        //先修改数据库
        updateById(shop);
        // @TODO 现在不再需要删除缓存了，由canal监听数据库的变化，然后更新缓存
        //再删除缓存
//        stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());
        return Result.ok();
    }

    //下面用来解决热点高并发访问中的缓存击穿问题

    //获取锁的代码逻辑
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        //避免返回值为null，我们这里使用了BooleanUtil工具类
        return BooleanUtil.isTrue(flag);
    }

    //释放锁
    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }

    //-------------------------------------------------------------
    //逻辑过期实现缓存击穿问题->热点问题的数据预热
    public void saveShop2Redis(Long id, Long expirSeconds) throws InterruptedException {
        Shop shop = getById(id);
        Thread.sleep(200); //模拟上面取数据的时间

        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expirSeconds));
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }

    /**
     * es实现地理位置查询
     * 使用search_after实现深度分页
     * @param typeId
     * @param current
     * @return
     */
    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) throws IOException {
        // 判断是否需要根据坐标查询
        if(x == null || y == null){
            // 根据类型分页查询
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            // 返回数据
            return Result.ok(page.getRecords());
        }
        // 计算分页参数
        int from = (current - 1)*SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;

        //查询es
        //构建搜索请求
        SearchRequest request = new SearchRequest(SystemConstants.ES_SHOP_INDEX);
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        //构建搜索条件
        sourceBuilder.query(
                QueryBuilders.boolQuery()
                        .filter(QueryBuilders.termQuery("typeId", typeId))
        );
        //添加距离排序
        sourceBuilder.sort(
                SortBuilders.geoDistanceSort("location",y,x)
                        .order(SortOrder.ASC)
                        .unit(DistanceUnit.KILOMETERS)
        );
        //设置分页
        sourceBuilder.from(from);
        sourceBuilder.size(SystemConstants.DEFAULT_PAGE_SIZE);
        //执行查询
        request.source(sourceBuilder);
        SearchResponse response = restHighLevelClient.search(request, RequestOptions.DEFAULT);

        List<Shop> shops = new ArrayList<>();
        for(SearchHit hit : response.getHits().getHits()){
            Map<String, Object> sourceAsMap = hit.getSourceAsMap();
            Object[] sortValues = hit.getSortValues();
            Shop shop = JSONUtil.toBean(hit.getSourceAsString(), Shop.class);
            String[] location = sourceAsMap.get("location").toString().split(",");
            shop.setX(Double.valueOf(location[1]));
            shop.setY(Double.valueOf(location[0]));
            double distance = hit.getSortValues().length > 0 ?
                    ((Number) hit.getSortValues()[0]).doubleValue():0;
            shop.setDistance(distance);
            shops.add(shop);
        }
        return Result.ok(shops);
    }
//    @Override
//    public Result deepQueryShopByType(Integer typeId, Integer current, Double x, Double y,
//                                      Object[] lastSortValues) throws IOException {
//        // 判断是否需要根据坐标查询
//        if(x == null || y == null){
//            // 根据类型分页查询
//            Page<Shop> page = query()
//                    .eq("type_id", typeId)
//                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
//            // 返回数据
//            return Result.ok(page.getRecords());
//        }
//        // 计算分页参数
//        int from = (current - 1)*SystemConstants.DEFAULT_PAGE_SIZE;
//        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;
//
//        //查询es
//        //构建搜索请求
//        SearchRequest request = new SearchRequest(SystemConstants.ES_SHOP_INDEX);
//        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
//        //构建搜索条件
//        sourceBuilder.query(
//                QueryBuilders.boolQuery()
//                        .filter(QueryBuilders.termQuery("typeId", typeId))
//        );
//        //添加距离排序
//        sourceBuilder.sort(
//                SortBuilders.geoDistanceSort("location",y,x)
//                        .order(SortOrder.ASC)
//                        .unit(DistanceUnit.KILOMETERS)
//        );
//        //设置search_after参数
//        if(lastSortValues !=null){
//            sourceBuilder.searchAfter(lastSortValues);
//        }
//
//        //执行查询
//        request.source(sourceBuilder);
//        SearchResponse response = restHighLevelClient.search(request, RequestOptions.DEFAULT);
//
//        List<Shop> shops = new ArrayList<>();
//        SearchHit[] hits = response.getHits().getHits();
//        for(SearchHit hit : hits){
//            Map<String, Object> sourceAsMap = hit.getSourceAsMap();
//            Object[] sortValues = hit.getSortValues();
//            Shop shop = JSONUtil.toBean(hit.getSourceAsString(), Shop.class);
//            String[] location = sourceAsMap.get("location").toString().split(",");
//            shop.setX(Double.valueOf(location[1]));
//            shop.setY(Double.valueOf(location[0]));
//            double distance = hit.getSortValues().length > 0 ?
//                    ((Number) hit.getSortValues()[0]).doubleValue():0;
//            shop.setDistance(distance);
//            shops.add(shop);
//        }
//        Object[] nextSortValues = hits.length>0 ? hits[hits.length-1].getSortValues():null;
//        SearchResult searchResult = new SearchResult(shops, nextSortValues);
//        return Result.ok(searchResult);
//    }
//    // 查询结果封装
//    public static class SearchResult {
//        private final List<?> data;
//        private final Object[] nextSortValues;
//
//        public SearchResult(List<?> data, Object[] nextSortValues) {
//            this.data = data;
//            this.nextSortValues = nextSortValues;
//        }
//    }


//    @Override
//    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
//        //1. 判断是否需要根据距离查询
//        if (x == null || y == null) {
//            // 根据类型分页查询
//            Page<Shop> page = query()
//                    .eq("type_id", typeId)
//                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
//            // 返回数据
//            return Result.ok(page.getRecords());
//        }
////        以下是需要根据距离查询
//
//        //2. 计算分页查询参数
//        int from = (current - 1) * SystemConstants.MAX_PAGE_SIZE;
//        int end = current * SystemConstants.MAX_PAGE_SIZE;
//
//
//        String key = SHOP_GEO_KEY + typeId;
//        //3. 查询redis、按照距离排序、分页; 结果：shopId、distance
//        //GEOSEARCH key FROMLONLAT x y BYRADIUS 5000 m WITHDIST
//        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo().search(key,
//                GeoReference.fromCoordinate(x, y),
//                new Distance(5000),
//                RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end));
//
//        if (results == null) {
//            return Result.ok(Collections.emptyList());
//        }
//
//        //4. 解析出id
//        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
//
//        if (list.size() < from) {
//            //起始查询位置大于数据总量，则说明没数据了，返回空集合
//            return Result.ok(Collections.emptyList());
//        }
//
//        ArrayList<Long> ids = new ArrayList<>(list.size());
//        HashMap<String, Distance> distanceMap = new HashMap<>(list.size());
//        list.stream().skip(from).forEach(result -> {
//            String shopIdStr = result.getContent().getName();
//            ids.add(Long.valueOf(shopIdStr));
//            Distance distance = result.getDistance();
//            distanceMap.put(shopIdStr, distance);
//        });
//
//
//        //5. 根据id查询shop
//        String idsStr = StrUtil.join(",", ids);
//
//        List<Shop> shops = query().in("id", ids).last("ORDER BY FIELD( id," + idsStr + ")").list();
//        for (Shop shop : shops) {
//            //设置shop的举例属性，从distanceMap中根据shopId查询
//            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
//        }
//        //6. 返回
//        return Result.ok(shops);
//    }
}
