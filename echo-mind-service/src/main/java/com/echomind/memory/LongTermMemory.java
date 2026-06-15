package com.echomind.memory;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.echomind.entity.UserProfile;
import com.echomind.mapper.UserProfileMapper;
import com.echomind.memory.ChromaDBClient.ChromaDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Level 3: 长期记忆 (Long-Term Memory)
 *
 * 存储:
 *   - ChromaDB Collection "echomind_long_term" — 用户画像向量索引
 *   - MySQL tb_user_profile — 偏好标签与行为统计持久化
 * 内容: 用户偏好标签 (cuisine, location 等)、交互统计、最近意图
 * 触发: 每次会话结束后增量更新
 * 召回: 通过 userId 在 ChromaDB 和 MySQL 中检索
 */
@Slf4j
@Component
public class LongTermMemory {

    private static final String COLLECTION_NAME = "echomind_long_term";

    private final ChromaDBClient chromaClient;
    private final UserProfileMapper userProfileMapper;

    public LongTermMemory(ChromaDBClient chromaClient, UserProfileMapper userProfileMapper) {
        this.chromaClient = chromaClient;
        this.userProfileMapper = userProfileMapper;

        try {
            chromaClient.getOrCreateCollection(COLLECTION_NAME);
            log.info("LTM 长期记忆初始化完成, collection={}", COLLECTION_NAME);
        } catch (Exception e) {
            log.warn("LTM ChromaDB 集合初始化跳过: {}", e.getMessage());
        }
    }

    // ==================== 读取 ====================

    /**
     * 通过 userId 获取 MySQL 用户画像
     */
    public UserProfile getUserProfile(Long userId) {
        return userProfileMapper.selectOne(
                new LambdaQueryWrapper<UserProfile>()
                        .eq(UserProfile::getUserId, userId));
    }

    /**
     * 获取用户偏好 JSON 字符串
     */
    public String getUserPreference(Long userId) {
        UserProfile profile = getUserProfile(userId);
        if (profile != null) return profile.getPreferences();
        return null;
    }

    /**
     * 构建长期记忆上下文文本（供 LLM Prompt 注入）
     */
    public String getContextString(Long userId) {
        UserProfile profile = getUserProfile(userId);
        if (profile == null) return "";

        StringBuilder sb = new StringBuilder("\n--- 用户画像 ---\n");

        String prefs = profile.getPreferences();
        if (prefs != null && !prefs.equals("{}") && !prefs.isBlank()) {
            sb.append("偏好: ").append(prefs).append("\n");
        }

        sb.append("总对话次数: ").append(profile.getInteractionCount() != null ?
                profile.getInteractionCount() : 0).append("\n");

        if (profile.getLastIntent() != null) {
            sb.append("最近意图: ").append(profile.getLastIntent()).append("\n");
        }

        sb.append("---\n");
        return sb.toString();
    }

    // ==================== 写入 ====================

    /**
     * 会话结束后增量更新用户画像
     * 1. 更新 MySQL tb_user_profile
     * 2. 更新 ChromaDB 向量索引
     */
    public void updateProfile(Long userId, String message, String intent) {
        String extractedPrefs = extractPreferences(message);

        // 1. 更新 MySQL
        UserProfile existing = userProfileMapper.selectOne(
                new LambdaQueryWrapper<UserProfile>()
                        .eq(UserProfile::getUserId, userId));

        if (existing == null) {
            existing = new UserProfile();
            existing.setUserId(userId);
            existing.setPreferences(extractedPrefs);
            existing.setInteractionCount(1);
            existing.setLastIntent(intent);
            userProfileMapper.insert(existing);
            log.info("LTM 用户画像新建: userId={}, prefs={}", userId, extractedPrefs);
        } else {
            String mergedPrefs = mergePreferences(existing.getPreferences(), extractedPrefs);
            existing.setPreferences(mergedPrefs);
            existing.setInteractionCount(existing.getInteractionCount() != null ?
                    existing.getInteractionCount() + 1 : 1);
            existing.setLastIntent(intent);
            userProfileMapper.updateById(existing);
            log.info("LTM 用户画像更新: userId={}, prefs={}", userId, mergedPrefs);
        }

        // 2. 更新 ChromaDB
        updateChromaProfile(userId, extractedPrefs);
    }

    private void updateChromaProfile(Long userId, String preferences) {
        try {
            chromaClient.deleteDocuments(COLLECTION_NAME, Map.of("userId", String.valueOf(userId)));

            String summary = "用户 " + userId + " 偏好: " + preferences;
            Map<String, String> metadata = new HashMap<>();
            metadata.put("userId", String.valueOf(userId));
            metadata.put("type", "long-term-profile");

            ChromaDocument doc = new ChromaDocument(
                    "ltm-" + userId, summary, metadata, null);
            chromaClient.addDocuments(COLLECTION_NAME, List.of(doc));
        } catch (Exception e) {
            log.warn("LTM ChromaDB 画像更新跳过: {}", e.getMessage());
        }
    }

    // ==================== 画像提取 ====================

    private String extractPreferences(String message) {
        JSONObject prefs = new JSONObject();
        if (message == null) return prefs.toString();

        if (message.contains("辣") || message.contains("川菜") || message.contains("麻辣") || message.contains("火锅")) {
            prefs.put("cuisine", "spicy");
        } else if (message.contains("甜") || message.contains("甜品") || message.contains("蛋糕")) {
            prefs.put("cuisine", "sweet");
        } else if (message.contains("日料") || message.contains("日式") || message.contains("日本")) {
            prefs.put("cuisine", "japanese");
        }

        if (message.contains("朝阳")) {
            prefs.put("location", "chaoyang");
        } else if (message.contains("海淀")) {
            prefs.put("location", "haidian");
        } else if (message.contains("东城")) {
            prefs.put("location", "dongcheng");
        } else if (message.contains("西城")) {
            prefs.put("location", "xicheng");
        }

        return prefs.toString();
    }

    private String mergePreferences(String oldPrefs, String newPrefs) {
        JSONObject merged = new JSONObject();
        if (oldPrefs != null && !oldPrefs.isBlank()) {
            try { merged = JSON.parseObject(oldPrefs); } catch (Exception ignored) {}
        }
        if (newPrefs != null && !newPrefs.isBlank()) {
            try {
                JSONObject newObj = JSON.parseObject(newPrefs);
                for (String key : newObj.keySet()) {
                    merged.put(key, newObj.get(key));
                }
            } catch (Exception ignored) {}
        }
        return merged.toString();
    }
}
