package com.hmdp;

import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson.JSON;
import com.hmdp.entity.Shop;
import com.hmdp.entity.ShopDoc;
import com.hmdp.service.IShopService;
import org.apache.http.HttpHost;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.common.xcontent.XContentType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.List;

@SpringBootTest
public class EsTest {

    private RestHighLevelClient client;

    @Resource
    private IShopService shopService;

    @BeforeEach
    void setUp() {
        this.client = new RestHighLevelClient(RestClient.builder(
                HttpHost.create("http://192.168.21.128:9200")
        ));
    }

    @Test
    void testConnect() {
        System.out.println(client);
    }

    @AfterEach
    void tearDown() throws IOException {
        this.client.close();
    }

    /**
     * 判断索引库是否存在
     * @throws IOException
     */
    @Test
    void testGetHotelIndex() throws IOException {
        GetIndexRequest request = new GetIndexRequest("tb_shop");
        boolean exists = client.indices().exists(request, RequestOptions.DEFAULT);
        System.out.println(exists ? "索引库已存在" : "索引库不存在");
    }

    @Test
    void testAddDocument() throws IOException {
            BulkRequest request = new BulkRequest();
            List<Shop> shopList = shopService.list();
            for (Shop shop : shopList) {
                ShopDoc shopDoc = new ShopDoc(shop);
                String doc = JSONUtil.toJsonStr(shopDoc);
                request.add(new IndexRequest("tb_shop").
                        id(shopDoc.getId().toString()).
                        source(doc, XContentType.JSON));
            }
            client.bulk(request, RequestOptions.DEFAULT);
    }
    @Test
    void testIndexDocument() throws IOException {
        // 1.根据id查询商品数据
        List<Shop> shopList = shopService.list();
        shopList.forEach(shop -> {
            // 2.转换为文档类型
            ShopDoc itemDoc = new ShopDoc(shop);
            // 3.将ItemDTO转json
            String doc = JSONUtil.toJsonStr(itemDoc);

            // 1.准备Request对象
            IndexRequest request = new IndexRequest("tb_shop").id(itemDoc.getId().toString());
            // 2.准备Json文档
            request.source(doc, XContentType.JSON);
            // 3.发送请求
            try {
                client.index(request, RequestOptions.DEFAULT);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    void testGetDocumentById() throws IOException {
        // 1. 准备request对象
        GetRequest request = new GetRequest("tb_shop").id("14");
        // 2. 发送请求，得到结果
        GetResponse response = client.get(request, RequestOptions.DEFAULT);
        // 3. 解析结果
        String jsonStr = ((GetResponse) response).getSourceAsString();
        ShopDoc hotelDoc = JSON.parseObject(jsonStr, ShopDoc.class);
        System.out.println(hotelDoc);
    }

    @Test
    void testSearchByLocation(){

    }



}