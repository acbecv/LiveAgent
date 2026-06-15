package com.echomind.memory;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 用户画像提取器（异步外观）
 * 会话结束后异步调用 LongTermMemory 更新用户画像
 */
@Slf4j
@Component
public class UserProfileExtractor {

    private final LongTermMemory longTermMemory;

    public UserProfileExtractor(LongTermMemory longTermMemory) {
        this.longTermMemory = longTermMemory;
    }

    @Async
    public void extractAndSave(Long userId, String message, String intent) {
        longTermMemory.updateProfile(userId, message, intent);
    }
}
