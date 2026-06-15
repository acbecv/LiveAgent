package com.hmdp.config;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import com.hmdp.mapper.ShopMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import javax.annotation.PostConstruct;
import java.util.List;

@Component
public class BloomFilterInitializer {

    private final StringRedisTemplate stringRedisTemplate;
    private final ShopMapper shopMapper; // 假设你有ShopMapper用于查询所有商品ID
    private final BloomFilter<Long> shopIdBloomFilter; // 布隆过滤器实例

    // 构造函数注入依赖
    public BloomFilterInitializer(StringRedisTemplate stringRedisTemplate, ShopMapper shopMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.shopMapper = shopMapper;
        // 初始化布隆过滤器：预计存储100万商品ID，误判率0.01%
        this.shopIdBloomFilter = BloomFilter.create(
                Funnels.longFunnel(), // 针对Long类型的哈希函数
                1000000, // 预期元素数量
                0.0001 // 误判率
        );
    }

    // 项目启动时加载所有商品ID到布隆过滤器
    @PostConstruct
    public void init() {
        // 查询数据库中所有存在的商品ID
        List<Long> allShopIds = shopMapper.findAllShopIds();
        // 插入布隆过滤器
        for (Long shopId : allShopIds) {
            shopIdBloomFilter.put(shopId);
        }
    }

    // 提供外部调用的布隆过滤器校验方法
    public boolean mightContain(Long shopId) {
        return shopIdBloomFilter.mightContain(shopId);
    }

}
