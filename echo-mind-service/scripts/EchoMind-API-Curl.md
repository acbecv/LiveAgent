# EchoMind 智能客服 API 的 curl 参考

基础地址：`http://localhost:8082`

本文档将 `EchoMind-API-Tests.postman_collection.json` 中的接口整理为可直接复制的 `curl` 命令，方便本地联调与接口验证。

---

## 一、对话接口

### 1.1 商户推荐 - 川菜馆推荐

测试要点：
- 返回状态码 200
- `data.intent` 应为 `shop_recommend`
- `data.answer`、`data.agentUsed`、`data.sessionId` 不为空

```bash
curl -X POST "http://localhost:8082/api/chat" \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 10001,
    "message": "帮我推荐一家评分高的川菜馆",
    "sessionId": "session-test-001",
    "stream": false
  }'
```

### 1.2 商户查询 - 查商户信息

```bash
curl -X POST "http://localhost:8082/api/chat" \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 10001,
    "message": "查一下川味轩的地址和电话",
    "sessionId": "session-test-001",
    "stream": false
  }'
```

### 1.3 优惠券查询 - 查可用券

```bash
curl -X POST "http://localhost:8082/api/chat" \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 10001,
    "message": "这家店有优惠券可以用吗？",
    "sessionId": "session-test-002",
    "stream": false
  }'
```

### 1.4 秒杀查询 - 查秒杀活动

```bash
curl -X POST "http://localhost:8082/api/chat" \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 10001,
    "message": "秒杀活动什么时候开始？",
    "sessionId": "session-test-003",
    "stream": false
  }'
```

### 1.5 用户信息查询 - 查个人资料

```bash
curl -X POST "http://localhost:8082/api/chat" \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 10001,
    "message": "查一下我的个人信息",
    "sessionId": "session-test-004",
    "stream": false
  }'
```

### 1.6 投诉建议 - 用户投诉

```bash
curl -X POST "http://localhost:8082/api/chat" \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 10001,
    "message": "我要投诉，这家店的服务太差了",
    "sessionId": "session-test-005",
    "stream": false
  }'
```

### 1.7 闲聊 - 打招呼

```bash
curl -X POST "http://localhost:8082/api/chat" \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 10001,
    "message": "你好，今天天气怎么样？",
    "sessionId": "session-test-006",
    "stream": false
  }'
```

### 1.8 关注操作 - 用户关注

```bash
curl -X POST "http://localhost:8082/api/chat" \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 10001,
    "message": "怎么关注这个商户？",
    "sessionId": "session-test-007",
    "stream": false
  }'
```

### 1.9 多轮对话 - 后续追问

```bash
curl -X POST "http://localhost:8082/api/chat" \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 10001,
    "message": "那个评分最高的在哪条路上？",
    "sessionId": "session-test-001",
    "stream": false
  }'
```

---

## 二、流式对话接口（SSE）

### 2.1 SSE 流式对话

测试要点：
- 响应类型为 `text/event-stream`
- 返回内容应包含 `data:`
- 事件中应包含 `thinking`、`token`、`done`

```bash
curl -N -X POST "http://localhost:8082/api/chat/stream" \
  -H "Accept: text/event-stream" \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 10001,
    "message": "这家店还有优惠券吗？",
    "sessionId": "session-stream-001"
  }'
```

---

## 三、评测接口

### 3.1 执行评测 - 全维度

测试要点：
- 返回状态码 200
- `data.overallScore` 存在且不小于 1
- `data.dimensionScores` 包含 `accuracy`、`relevance`、`completeness`、`friendliness`
- `data.batchId` 以 `eval-` 开头

```bash
curl -X POST "http://localhost:8082/api/eval/run" \
  -H "Content-Type: application/json" \
  -d '{
    "dataset": "golden_answers_v1",
    "sampleSize": 3,
    "dimensions": ["accuracy", "relevance", "completeness", "friendliness"]
  }'
```

### 3.2 获取评测报告

把下面的 `{batchId}` 替换成实际批次号。

```bash
curl -X GET "http://localhost:8082/api/eval/report/eval-20240315-001"
```

---

## 四、监控接口

### 4.1 获取监控指标

测试要点：
- 返回状态码 200
- `data.system.totalRequests` 存在
- `data.system.overallSatisfaction` 存在
- `data.weights` 存在

```bash
curl -X GET "http://localhost:8082/api/monitor/metrics"
```

### 4.2 触发监控闭环

```bash
curl -X POST "http://localhost:8082/api/monitor/cycle"
```

---

## 五、健康检查

### 5.1 Actuator 健康检查

测试要点：
- 返回状态码 200
- `status` 应为 `UP`
- `service` 应为 `echo-mind-service`

```bash
curl -X GET "http://localhost:8082/actuator/health"
```

---

## 六、附加说明

### 6.1 常用请求头

如果你需要手动补充统一请求头，可以参考：

```bash
-H "Content-Type: application/json"
-H "Accept: text/event-stream"
```

### 6.2 本地启动前检查

请先确认：
- `echo-mind-service` 已启动在 `8082`
- Redis、MySQL、RabbitMQ 等依赖可用
- AI 配置已正确设置

### 6.3 目录说明

本文档来源于：
- `echo-mind-service/scripts/EchoMind-API-Tests.postman_collection.json`

