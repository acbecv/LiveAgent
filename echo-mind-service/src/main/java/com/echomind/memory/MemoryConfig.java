package com.echomind.memory;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "echomind.memory")
public class MemoryConfig {
    /** 短期记忆：最多保留的消息轮数 */
    private int shortTermMax = 20;

    /** 中期记忆触发压缩的消息数阈值（超过此值时触发） */
    private int compressThreshold = 20;

    /** 中期记忆触发压缩的空闲时间阈值（分钟） */
    private int idleCompressMinutes = 30;

    /** 中期记忆向量检索 Top-K */
    private int midTermTopK = 3;

    private String compressStrategy = "sliding-window";
}
