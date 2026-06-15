package com.hmdp.config;

import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
public class EsConfig {
    @Bean
    public RestHighLevelClient restHighLevelClient(){
        HttpHost httpHost = HttpHost.create("http://192.168.21.128:9200");
        return new RestHighLevelClient(RestClient.builder(httpHost));
    }
}
