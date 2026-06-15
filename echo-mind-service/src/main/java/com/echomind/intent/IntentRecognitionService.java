package com.echomind.intent;

import com.echomind.dto.IntentResult;
import com.echomind.mapper.IntentTemplateMapper;
import com.echomind.entity.IntentTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 意图识别服务 - 三路融合入口
 * 同时启动 LLM + Embedding + Pattern 三路识别，由 VotingIntentFuser 融合
 */
@Slf4j
@Service
public class IntentRecognitionService {

    private final List<IntentRecognizer> recognizers;
    private final VotingIntentFuser fuser;
    private final IntentTemplateMapper intentTemplateMapper;

    public IntentRecognitionService(List<IntentRecognizer> recognizers,
                                    VotingIntentFuser fuser,
                                    IntentTemplateMapper intentTemplateMapper) {
        this.recognizers = recognizers;
        this.fuser = fuser;
        this.intentTemplateMapper = intentTemplateMapper;
    }

    /**
     * 三路融合意图识别
     */
    public IntentResult recognize(String userId, String message, String sessionId) {
        log.info("开始三路意图识别: userId={}, message={}", userId, message);

        // 三路并行识别
        List<IntentResult> results = recognizers.parallelStream()
                .map(r -> r.recognize(userId, message, sessionId))
                .toList();

        // 加权投票融合
        IntentResult fused = fuser.fuse(results);

        log.info("意图识别结果: category={}, confidence={}, source={}",
                fused.getCategory(), fused.getConfidence(), fused.getSource());

        return fused;
    }

    public void refreshTemplates() {
        List<IntentTemplate> templates = intentTemplateMapper.selectList(null);
        log.info("已加载 {} 个意图模板", templates.size());
    }
}
