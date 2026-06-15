package com.echomind.intent;

import com.echomind.dto.IntentResult;

/**
 * 意图识别接口 - 三路融合策略
 */
public interface IntentRecognizer {
    IntentResult recognize(String userId, String message, String sessionId);
}
