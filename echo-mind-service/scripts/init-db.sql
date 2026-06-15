-- ============================================
-- EchoMind 智能客服 - 数据库初始化脚本
-- 与主项目共享 MySQL 实例，使用独立表
-- ============================================

-- 会话表
CREATE TABLE IF NOT EXISTS tb_conversation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL UNIQUE COMMENT '会话ID',
    user_id BIGINT NOT NULL COMMENT '用户ID (关联tb_user)',
    title VARCHAR(256) COMMENT '会话标题 (自动生成)',
    status TINYINT DEFAULT 1 COMMENT '状态: 0-已结束 1-进行中',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_session_id (session_id)
) COMMENT='客服会话表';

-- 消息表
CREATE TABLE IF NOT EXISTS tb_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL COMMENT '会话ID',
    role VARCHAR(16) NOT NULL COMMENT '角色: user/assistant/system',
    content TEXT NOT NULL COMMENT '消息内容',
    intent VARCHAR(64) COMMENT '意图类别',
    intent_confidence DECIMAL(4,3) COMMENT '意图置信度',
    agent_used VARCHAR(32) COMMENT '使用的Agent',
    tokens_used INT COMMENT 'Token消耗',
    latency_ms INT COMMENT '响应延迟(ms)',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_session_id (session_id),
    INDEX idx_created_at (created_at)
) COMMENT='对话消息表';

-- 用户画像表
CREATE TABLE IF NOT EXISTS tb_user_profile (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE COMMENT '用户ID (关联tb_user)',
    preferences JSON COMMENT '偏好标签: {"cuisine":"spicy","location":"chaoyang"}',
    interaction_count INT DEFAULT 0 COMMENT '交互次数',
    satisfaction_avg DECIMAL(3,2) COMMENT '平均满意度',
    last_intent VARCHAR(64) COMMENT '最近意图',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT='用户画像表';

-- 意图模板表
CREATE TABLE IF NOT EXISTS tb_intent_template (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    intent_code VARCHAR(64) NOT NULL COMMENT '意图编码',
    intent_name VARCHAR(128) NOT NULL COMMENT '意图名称',
    description TEXT COMMENT '意图描述',
    keywords JSON COMMENT '关键词列表',
    examples JSON COMMENT '示例语句',
    priority INT DEFAULT 0 COMMENT '优先级',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_intent_code (intent_code)
) COMMENT='意图模板表';

-- 评测记录表
CREATE TABLE IF NOT EXISTS tb_eval_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    eval_batch VARCHAR(64) NOT NULL COMMENT '评测批次',
    message_id BIGINT COMMENT '关联消息ID',
    question TEXT COMMENT '用户问题',
    answer TEXT COMMENT 'AI回答',
    reference TEXT COMMENT '参考答案',
    accuracy_score DECIMAL(3,2) COMMENT '准确性得分',
    relevance_score DECIMAL(3,2) COMMENT '相关性得分',
    completeness_score DECIMAL(3,2) COMMENT '完整性得分',
    friendliness_score DECIMAL(3,2) COMMENT '友好度得分',
    overall_score DECIMAL(3,2) COMMENT '综合得分',
    judge_reason TEXT COMMENT '裁判理由',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_eval_batch (eval_batch)
) COMMENT='评测记录表';

-- Agent 性能指标表
CREATE TABLE IF NOT EXISTS tb_agent_metrics (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    agent_code VARCHAR(32) NOT NULL COMMENT 'Agent编码',
    metric_date DATE NOT NULL COMMENT '统计日期',
    metric_hour INT COMMENT '统计小时 (可选, 用于小时级统计)',
    request_count INT DEFAULT 0 COMMENT '请求数',
    avg_latency_ms DECIMAL(8,2) COMMENT '平均延迟',
    p95_latency_ms DECIMAL(8,2) COMMENT 'P95延迟',
    satisfaction_score DECIMAL(4,3) COMMENT '满意度',
    resolution_rate DECIMAL(4,3) COMMENT '解决率',
    error_rate DECIMAL(4,3) COMMENT '错误率',
    routing_weight DECIMAL(4,3) COMMENT '路由权重',
    total_tokens BIGINT COMMENT '总Token消耗',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_agent_date_hour (agent_code, metric_date, metric_hour)
) COMMENT='Agent性能指标表';

-- ============================================
-- 初始化意图模板数据
-- ============================================
INSERT INTO tb_intent_template (intent_code, intent_name, description, keywords, examples, priority) VALUES
('shop_query', '商户查询', '查询商户基本信息', '["商户","店铺","地址","电话","评分"]', '["这家店地址在哪？","查一下XX商户的电话"]', 10),
('shop_recommend', '商户推荐', '推荐商户给用户', '["推荐","附近","好吃","热门"]', '["推荐一家川菜馆","附近有什么好吃的？"]', 10),
('voucher_query', '优惠券查询', '查询可用优惠券', '["优惠券","优惠","券","折扣"]', '["有什么优惠券？","这家店有优惠吗？"]', 10),
('voucher_use', '优惠券使用', '使用优惠券相关', '["怎么用","使用","规则","满减"]', '["这个券怎么用？","满减规则是什么？"]', 8),
('user_profile', '用户信息', '用户信息查询', '["我的","信息","账号","积分"]', '["我的信息","查一下我的积分"]', 5)
ON DUPLICATE KEY UPDATE intent_name = VALUES(intent_name);

-- ============================================
-- 初始化示例用户画像
-- ============================================
INSERT INTO tb_user_profile (user_id, preferences, interaction_count, last_intent) VALUES
(10001, '{"cuisine":"spicy","location":"chaoyang"}', 0, NULL),
(10002, '{"cuisine":"sweet","location":"haidian"}', 0, NULL)
ON DUPLICATE KEY UPDATE preferences = VALUES(preferences);
