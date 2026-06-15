package com.echomind.util;

import org.springframework.stereotype.Component;

/**
 * 安全过滤器
 * 对 AI 生成的回答进行安全过滤，防止输出不当内容
 */
@Component
public class SafetyFilter {

    private static final String[] BLOCKED_WORDS = {
            "密码", "验证码", "银行卡", "身份证", "电话号码",
            "色情", "赌博", "毒品", "暴力", "恐怖"
    };

    /**
     * 过滤回答中的敏感内容
     */
    public String filter(String content) {
        if (content == null) return null;

        for (String word : BLOCKED_WORDS) {
            content = content.replaceAll("(?i)" + word, "***");
        }

        return content;
    }
}
