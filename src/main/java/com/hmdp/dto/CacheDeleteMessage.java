package com.hmdp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 缓存删除消息DTO
 * 用于Canal监听数据库变更后，发送缓存删除消息到MQ
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CacheDeleteMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 数据库表名
     */
    private String tableName;

    /**
     * 操作类型：INSERT、UPDATE、DELETE
     */
    private String operationType;

    /**
     * 需要删除的缓存Key列表
     */
    private List<String> cacheKeys;

    /**
     * 数据ID
     */
    private Long dataId;

    /**
     * 数据内容（JSON格式，用于重建缓存）
     */
    private String dataJson;

    /**
     * 是否需要延迟双删
     */
    private Boolean needDelayDelete;

    /**
     * 延迟删除时间（毫秒）
     */
    private Long delayTime;
}
