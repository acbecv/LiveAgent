package com.echomind.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Token 计数器 (简易估算)
 * 中文约 1.5 token/字，英文约 0.3 token/字母
 */
@Slf4j
@Component
public class TokenCounter {

    public int count(String text) {
        if (text == null || text.isBlank()) return 0;

        int chineseChars = 0;
        int englishChars = 0;

        for (char ch : text.toCharArray()) {
            if (Character.isIdeographic(ch)) {
                chineseChars++;
            } else if (Character.isLetter(ch)) {
                englishChars++;
            }
        }

        return (int) (chineseChars * 1.5 + englishChars * 0.3);
    }
}
