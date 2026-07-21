// src/main/java/com/hmdp/service/ILikedRecordService.java
package com.hmdp.service;

import com.hmdp.entity.LikedRecord;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;
import java.util.Set;

public interface ILikedRecordService extends IService<LikedRecord> {
    void addLikeRecord(Long blogId, boolean liked);
    Set<Long> isBlogLiked(List<Long> blogIds);
}