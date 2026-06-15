# LLSP - 智能点评平台

基于 Spring Boot 2.3 的企业级智能点评平台，集成 AI 智能客服、多级缓存、分布式锁、消息队列、全文搜索等特性。

## 项目概览

LLSP 是一个前后端分离的点评平台，包含主业务服务和 AI 智能客服两个核心服务。主服务提供商户管理、用户系统、博客/点评、优惠券秒杀等核心业务功能；AI 服务通过意图识别、RAG 知识库、多级记忆管理等技术，为用户提供智能客服体验。

## 系统架构

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              用户请求层                                      │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐    │
│  │   Web 前端    │  │  移动端 H5   │  │  微信小程序   │  │  API 调用方   │    │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘    │
│         │                 │                 │                 │             │
│         └─────────────────┴────────┬────────┴─────────────────┘             │
│                                    │                                        │
│                           ┌────────▼────────┐                               │
│                           │   Nginx 网关     │  反向代理 + 静态资源           │
│                           └────────┬────────┘                               │
│                                    │                                        │
├────────────────────────────────────┼────────────────────────────────────────┤
│                              应用服务层                                      │
│                                    │                                        │
│              ┌─────────────────────┼─────────────────────┐                  │
│              │                     │                     │                  │
│  ┌───────────▼───────────┐  ┌──────▼──────┐  ┌──────────▼───────────┐     │
│  │   主业务服务 (8081)    │  │             │  │  AI 智能客服 (8082)   │     │
│  │   Spring Boot 2.3     │  │  Sidecar    │  │  Spring AI           │     │
│  │                       │  │  模式       │  │                      │     │
│  │  ├─ 商户管理          │  │             │  │  ├─ 意图识别引擎      │     │
│  │  ├─ 用户系统          │  │  共享       │  │  ├─ Agent 路由编排    │     │
│  │  ├─ 博客/点评         │  │  Redis/DB   │  │  ├─ RAG 知识库       │     │
│  │  ├─ 优惠券秒杀        │  │             │  │  ├─ 多级记忆管理      │     │
│  │  ├─ 关注/粉丝         │  │             │  │  ├─ 评测系统         │     │
│  │  └─ 签到/UV统计       │  │             │  │  └─ 实时监控         │     │
│  └───────────┬───────────┘  └──────┬──────┘  └──────────┬───────────┘     │
│              │                     │                     │                  │
├──────────────┼─────────────────────┼─────────────────────┼──────────────────┤
│              │              中间件/基础设施层              │                  │
│              │                     │                     │                  │
│  ┌───────────▼───────────┐  ┌──────▼──────┐  ┌──────────▼───────────┐     │
│  │      MySQL 5.7        │  │  Redis 7    │  │   RabbitMQ 3        │     │
│  │  主数据存储            │  │  缓存/分布式 │  │  消息队列            │     │
│  │  binlog → Canal       │  │  锁/计数器  │  │  异步解耦            │     │
│  └───────────────────────┘  └─────────────┘  └──────────────────────┘     │
│                                                                            │
│  ┌───────────────────────┐  ┌─────────────┐  ┌──────────────────────┐     │
│  │  Elasticsearch 7.12   │  │  ChromaDB   │  │  Canal 1.2.1        │     │
│  │  全文搜索              │  │  向量数据库  │  │  binlog 监听         │     │
│  │  商户搜索              │  │  AI 语义检索 │  │  缓存异步删除        │     │
│  └───────────────────────┘  └─────────────┘  └──────────────────────┘     │
└────────────────────────────────────────────────────────────────────────────┘
```

## 技术栈

| 分类 | 技术 | 版本 | 用途 |
|------|------|------|------|
| **后端框架** | Spring Boot | 2.3.12 | 主应用框架 |
| | Spring AI | 1.x | AI 服务框架 |
| | MyBatis-Plus | 3.4.3 | ORM 框架 |
| **数据库** | MySQL | 5.7 | 主数据存储 |
| | Elasticsearch | 7.12.1 | 全文搜索引擎 |
| | ChromaDB | latest | AI 向量数据库 |
| **缓存** | Redis | 7 | 分布式缓存、计数器、分布式锁 |
| | Caffeine | 2.9.2 | 本地缓存（L1） |
| **消息队列** | RabbitMQ | 3 | 异步消息、死信队列 |
| **数据同步** | Canal | 1.2.1 | MySQL binlog 监听 |
| **AI 服务** | 通义千问 (Qwen) | qwen3.5-flash | LLM 大语言模型 |
| | Spring AI | - | AI 应用框架 |
| **工具库** | Hutool | 5.7.17 | Java 工具类库 |
| | Lombok | 1.18.20 | 代码简化 |
| | Guava | 22.0 | 令牌桶限流 |
| | Redisson | 3.13.6 | 分布式锁 |
| | FastJSON | 1.2.83 | JSON 序列化 |
| **容器化** | Docker + Docker Compose | 3.8 | 容器编排 |

## 核心功能详解

### 一、主业务服务 (端口 8081)

#### 1. 商户管理模块

- **多级缓存架构**: Caffeine (L1) → Redis (L2) → MySQL (L3)
- **缓存穿透解决方案**: 缓存空值 + 布隆过滤器
- **缓存击穿解决方案**: 互斥锁 (Redis SETNX) + 逻辑过期
- **缓存一致性**: Canal 监听 MySQL binlog → 发送 MQ 消息 → 异步删除缓存
- **幂等性保证**: 消息 MD5 去重 + 重试机制 + 死信队列
- **商户搜索**: Elasticsearch 全文搜索 + 附近商户 GeoHash 查询

**缓存更新流程**:
```
MySQL 变更 → Canal 监听 → 发送 MQ 消息 → CacheDeleteConsumer 消费
→ 删除 Redis 缓存 + Caffeine 缓存 → 下次查询重新加载
```

#### 2. 用户系统模块

- **登录认证**: 手机号 + 验证码登录，Token 存储在 Redis
- **Token 刷新**: 双拦截器设计（RefreshTokenInterceptor + LoginInterceptor）
- **用户签到**: Redis Bitmap 实现，支持连续签到统计
- **UV 统计**: Redis HyperLogLog 实现独立访客统计

#### 3. 博客/点评模块

- **发布博客**: 支持图片上传，自动推送到粉丝收件箱
- **点赞功能**: Redis ZSet 实现，支持点赞排行榜
- **滚动查询**: 基于时间戳的无限滚动（替代传统分页）
- **粉丝推送**: 发布博客时写入粉丝的 Feed 队列（Redis ZSet）

#### 4. 优惠券秒杀模块

- **Lua 脚本原子操作**: 库存扣减 + 一人一单检查在 Redis 中原子执行
- **令牌桶限流**: Guava RateLimiter 控制请求速率
- **分布式锁**: 自定义 `@Lock` 注解 + Redisson 实现，支持 SpEL 表达式
- **异步下单**: 秒杀成功后通过 MQ 异步创建订单
- **消息确认**: RabbitMQ Publisher Confirm 机制保证消息可靠投递

#### 5. 分布式锁模块

- **自定义注解**: `@Lock(name, waitTime, leaseTime, lockType, lockStrategy)`
- **SpEL 表达式**: 支持动态锁名，如 `@Lock(name = "lock:order:#{#voucherOrder.userId}")`
- **锁策略**: 可重入锁、公平锁、读写锁
- **AOP 切面**: 方法执行前加锁，执行后自动/手动释放

#### 6. 限流模块

- **自定义注解**: `@Limit(key, period, count, type)`
- **限流策略**: Guava 令牌桶 + Redis 滑动窗口
- **AOP 切面**: 方法执行前检查限流，超限抛出异常

### 二、AI 智能客服 (端口 8082)

#### 1. 意图识别引擎

**三路融合识别架构**:
```
用户输入 → LLM 识别 (权重 0.5) ─┐
              Embedding 识别 (权重 0.3) ─┼→ 加权投票融合 → 最终意图
              Pattern 规则识别 (权重 0.2) ─┘
```

- **LLM 识别**: 调用大语言模型进行语义理解
- **Embedding 识别**: 向量相似度匹配预定义意图模板
- **Pattern 识别**: 正则表达式/关键词快速匹配
- **加权投票融合**: 三路结果按权重融合，输出最终意图和置信度

#### 2. Agent 路由编排

**两层路由架构**:
```
用户输入 → 意图识别 → 意图路由 → 选择 Agent ─┐
                                          ├→ 执行 Agent → 返回结果
                              降级保护 ←───┘
```

- **Agent 注册中心**: 支持多种专业 Agent（商户推荐、通用问答等）
- **降级策略**: Agent 执行失败时自动降级到备用 Agent
- **安全过滤**: 输入输出安全检查，防止恶意输入

#### 3. RAG 知识库

**多路并行检索架构**:
```
用户输入 → LLM 多Query扩展 ─┐
              向量检索 (ChromaDB) ─┼→ RRF 融合 → Top-K 知识
              BM25 关键词检索 ──┘
```

- **多 Query 扩展**: LLM 生成多个相关查询，提高召回率
- **向量检索**: ChromaDB 语义相似度检索
- **BM25 检索**: 关键词精确匹配
- **RRF 融合**: 多路结果使用 Reciprocal Rank Fusion 算法融合
- **知识注入**: 检索结果注入 Prompt 上下文，辅助 LLM 回答

#### 4. 多级记忆管理

**三级记忆架构**:
```
L1 短期记忆 (当前对话) → 实时上下文
L2 中期记忆 (会话摘要) → 滑动窗口压缩
L3 长期记忆 (用户画像) → ChromaDB 持久化
```

- **短期记忆**: 当前会话的完整对话历史
- **中期记忆**: 超过阈值时自动压缩为摘要
- **长期记忆**: 用户画像、偏好、历史行为，存储在 ChromaDB
- **自动压缩**: 空闲 30 分钟后触发压缩，节省 Token

#### 5. 评测系统

- **准确性评估**: 回答与标准答案的匹配度
- **相关性评估**: 回答与问题的相关程度
- **完整性评估**: 回答是否覆盖了问题的所有方面
- **友好度评估**: 回答的语气和表达方式
- **LLM Judge**: 使用 LLM 作为裁判进行综合评估
- **回归检测**: 检测新版本是否出现能力退化

#### 6. 实时监控

- **指标采集**: 响应时间、意图识别准确率、Agent 使用率
- **告警管理**: 异常指标自动告警
- **权重调整**: 根据历史数据动态调整意图识别权重
- **决策引擎**: 基于指标自动优化系统参数

## 项目结构

```
LLSP/
├── src/                                    # 主业务服务
│   ├── main/java/com/hmdp/
│   │   ├── cache/handler/                  # 缓存处理
│   │   │   ├── ShopCanalHandler.java       # Canal 监听 Shop 表变更
│   │   │   ├── ShopHandler.java            # 商户缓存处理器
│   │   │   ├── ShopRedisHandler.java       # Redis 缓存操作
│   │   │   └── CaffeinConfig.java          # Caffeine 本地缓存配置
│   │   ├── config/                         # 配置类
│   │   │   ├── BloomFilterInitializer.java # 布隆过滤器初始化
│   │   │   ├── EsConfig.java               # Elasticsearch 配置
│   │   │   ├── MqConfig.java               # RabbitMQ 配置
│   │   │   ├── RedissonConfig.java         # Redisson 配置
│   │   │   └── RabbitMQTopicConfig.java    # MQ Topic 配置
│   │   ├── controller/                     # 控制器层
│   │   │   ├── ShopController.java         # 商户接口
│   │   │   ├── UserController.java         # 用户接口
│   │   │   ├── BlogController.java         # 博客接口
│   │   │   ├── VoucherController.java      # 优惠券接口
│   │   │   └── VoucherOrderController.java # 订单接口
│   │   ├── service/                        # 业务逻辑层
│   │   ├── mapper/                         # 数据访问层
│   │   ├── entity/                         # 实体类
│   │   ├── dto/                            # 数据传输对象
│   │   ├── rabbitmq/                       # 消息队列
│   │   │   ├── CacheDeleteConsumer.java    # 缓存删除消费者
│   │   │   ├── CacheDeleteDlqConsumer.java # 死信队列消费者
│   │   │   ├── MQSender.java               # 消息发送器
│   │   │   └── MQReceiver.java             # 消息接收器
│   │   ├── redisson/                       # 分布式锁
│   │   │   ├── annotations/Lock.java       # 分布式锁注解
│   │   │   ├── aspect/LockAspect.java      # 分布式锁 AOP
│   │   │   └── enums/                      # 锁策略枚举
│   │   ├── limit/                          # 限流模块
│   │   │   ├── aop/LimitAspect.java        # 限流 AOP 切面
│   │   │   ├── manager/LimiterManager.java # 限流管理器
│   │   │   └── manager/RedisLimiter.java   # Redis 限流实现
│   │   └── utils/                          # 工具类
│   │       ├── CacheClient.java            # 通用缓存客户端
│   │       ├── RedisIdWorker.java          # Redis 全局 ID 生成器
│   │       └── MailUtils.java              # 邮件发送工具
│   └── main/resources/
│       ├── application.yaml                # 主配置文件
│       ├── seckill.lua                     # 秒杀 Lua 脚本
│       └── db/llsp.sql                     # 数据库初始化脚本
│
├── echo-mind-service/                      # AI 智能客服
│   ├── src/main/java/com/echomind/
│   │   ├── agent/                          # Agent 路由编排
│   │   │   ├── AgentRouter.java            # Agent 路由器
│   │   │   ├── IntentRouter.java           # 意图路由器
│   │   │   ├── FallbackStrategy.java       # 降级策略
│   │   │   └── BusinessTools.java          # 业务工具 (Function Calling)
│   │   ├── intent/                         # 意图识别
│   │   │   ├── IntentRecognitionService.java # 三路融合入口
│   │   │   ├── LlmIntentRecognizer.java    # LLM 识别器
│   │   │   ├── EmbeddingIntentRecognizer.java # Embedding 识别器
│   │   │   ├── PatternIntentRecognizer.java   # Pattern 识别器
│   │   │   └── VotingIntentFuser.java      # 加权投票融合器
│   │   ├── knowledge/                      # RAG 知识库
│   │   │   ├── MultiPathRAGRetriever.java  # 多路并行检索
│   │   │   ├── MultiQueryExpander.java     # 多 Query 扩展
│   │   │   ├── KeywordRetriever.java       # BM25 关键词检索
│   │   │   └── KnowledgeIngestor.java      # 知识注入器
│   │   ├── memory/                         # 多级记忆管理
│   │   │   ├── MemoryManager.java          # 记忆管理器
│   │   │   ├── ShortTermMemory.java        # 短期记忆
│   │   │   ├── MidTermMemory.java          # 中期记忆
│   │   │   ├── LongTermMemory.java         # 长期记忆
│   │   │   ├── AutoCompressor.java         # 自动压缩器
│   │   │   └── ChromaDBClient.java         # ChromaDB 客户端
│   │   ├── eval/                           # 评测系统
│   │   │   ├── EvalService.java            # 评测服务
│   │   │   ├── AccuracyEvaluator.java      # 准确性评估
│   │   │   ├── RelevanceEvaluator.java     # 相关性评估
│   │   │   ├── CompletenessEvaluator.java  # 完整性评估
│   │   │   ├── FriendlinessEvaluator.java  # 友好度评估
│   │   │   └── LlmJudge.java              # LLM 裁判
│   │   ├── monitor/                        # 实时监控
│   │   │   ├── MonitorService.java         # 监控服务
│   │   │   ├── MetricsCollector.java       # 指标采集器
│   │   │   ├── AlertManager.java           # 告警管理器
│   │   │   └── WeightAdjuster.java         # 权重调整器
│   │   ├── integration/                    # 外部服务集成
│   │   │   ├── DianpingClient.java         # 主服务客户端
│   │   │   └── SharedRedisAccessor.java    # 共享 Redis 访问
│   │   └── controller/                     # API 控制器
│   │       ├── ChatController.java         # 聊天接口
│   │       ├── EvalController.java         # 评测接口
│   │       └── MonitorController.java      # 监控接口
│   └── src/main/resources/
│       ├── application.yml                 # AI 服务配置
│       └── application-prod.yml            # 生产环境配置
│
├── docker-compose.yml                      # Docker 编排文件
├── .env.example                            # 环境变量模板
├── pom.xml                                 # Maven 配置
└── README.md                               # 项目文档
```

## 快速开始

### 环境要求

- JDK 1.8+
- Maven 3.6+
- Docker & Docker Compose (可选，用于一键启动依赖)

### 方式一：Docker Compose 一键启动

```bash
# 1. 复制环境变量模板
cp .env.example .env

# 2. 编辑 .env 文件，配置你的密钥
# 至少需要配置: DB_PASSWORD, AI_API_KEY

# 3. 启动所有服务
docker-compose up -d

# 4. 查看日志
docker-compose logs -f
```

### 方式二：本地开发

#### 1. 启动依赖服务

```bash
# 使用 Docker 启动 MySQL, Redis, RabbitMQ, Elasticsearch
docker-compose up -d mysql redis rabbitmq elasticsearch
```

#### 2. 配置环境变量

复制 `.env.example` 为 `.env`，并修改对应配置：

```bash
DB_HOST=localhost
DB_PASSWORD=your_password
REDIS_HOST=localhost
RABBITMQ_HOST=localhost
AI_API_KEY=your_api_key_here
```

#### 3. 初始化数据库

```bash
mysql -h localhost -u root -p < src/main/resources/db/llsp.sql
```

#### 4. 启动主应用

```bash
mvn clean package -DskipTests
java -jar target/llsp-dianping-0.0.1-SNAPSHOT.jar
```

#### 5. 启动 AI 服务 (可选)

```bash
cd echo-mind-service
mvn clean package -DskipTests
java -jar target/echo-mind-service-0.0.1-SNAPSHOT.jar
```

## 配置说明

### 环境变量

所有敏感配置（密码、密钥）均通过环境变量配置，详见 `.env.example`。

| 变量名 | 说明 | 默认值 |
|--------|------|--------|
| `DB_HOST` | MySQL 地址 | localhost |
| `DB_PORT` | MySQL 端口 | 3306 |
| `DB_NAME` | 数据库名 | llsp |
| `DB_USERNAME` | 数据库用户名 | root |
| `DB_PASSWORD` | 数据库密码 | your_password |
| `REDIS_HOST` | Redis 地址 | localhost |
| `REDIS_PORT` | Redis 端口 | 6379 |
| `REDIS_PASSWORD` | Redis 密码 | (空) |
| `RABBITMQ_HOST` | RabbitMQ 地址 | localhost |
| `RABBITMQ_USERNAME` | RabbitMQ 用户名 | guest |
| `RABBITMQ_PASSWORD` | RabbitMQ 密码 | guest |
| `AI_API_KEY` | AI API 密钥 | your_api_key_here |
| `AI_BASE_URL` | AI API 地址 | https://dashscope.aliyuncs.com/compatible-mode |
| `AI_MODEL` | AI 模型 | qwen3.5-flash |
| `AI_EMBEDDING_MODEL` | Embedding 模型 | text-embedding-v3 |
| `CHROMADB_URL` | ChromaDB 地址 | http://localhost:8000 |
| `MAIL_USER` | 邮件发送账号 | - |
| `MAIL_PASSWORD` | 邮件发送密码 | - |
| `CANAL_SERVER` | Canal 服务地址 | 127.0.0.1:11111 |
| `CANAL_DESTINATION` | Canal 目标 | example |

### 安全提醒

⚠️ **请勿将 `.env` 文件提交到版本控制系统！**

项目已将所有敏感信息移至环境变量，`.env` 文件已加入 `.gitignore`。

## API 接口

### 主业务服务

| 模块 | 接口 | 方法 | 说明 |
|------|------|------|------|
| 商户 | `/shop/{id}` | GET | 查询商户详情 |
| 商户 | `/shop/type/list` | GET | 查询商户类型列表 |
| 商户 | `/shop/of/type` | GET | 分页查询商户 |
| 商户 | `/shop/nearby` | GET | 查询附近商户 |
| 用户 | `/user/code` | POST | 发送验证码 |
| 用户 | `/user/login` | POST | 验证码登录 |
| 用户 | `/user/me` | GET | 获取当前用户信息 |
| 用户 | `/user/sign` | POST | 签到 |
| 用户 | `/user/sign/count` | GET | 连续签到天数 |
| 博客 | `/blog/hot` | GET | 热门博客 |
| 博客 | `/blog/{id}` | GET | 博客详情 |
| 博客 | `/blog/likes/{id}` | GET | 点赞列表 |
| 博客 | `/blog/like/{id}` | PUT | 点赞/取消点赞 |
| 博客 | `/blog/of/follow` | GET | 关注的人的动态 |
| 关注 | `/follow/or/not/{id}` | GET | 是否关注 |
| 关注 | `/follow/{id}` | PUT | 关注/取关 |
| 关注 | `/follow/of/me` | GET | 共同关注 |
| 优惠券 | `/voucher/list` | GET | 优惠券列表 |
| 优惠券 | `/voucher/seckill/{id}` | PUT | 秒杀优惠券 |
| 订单 | `/voucher/order/list` | GET | 订单列表 |
| 订单 | `/voucher/order/{id}` | DELETE | 取消订单 |

### AI 智能客服

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/chat` | POST | 智能对话 |
| `/api/eval` | POST | 触发评测 |
| `/api/health` | GET | 健康检查 |
| `/api/monitor/metrics` | GET | 监控指标 |

## 常见问题

### 1. 如何修改 AI 模型？

在 `.env` 中修改 `AI_MODEL` 即可：

```bash
AI_MODEL=qwen-plus
```

### 2. Canal 配置

Canal 用于监听 MySQL binlog 实现缓存异步删除。确保 MySQL 开启了 binlog：

```ini
[mysqld]
log-bin=mysql-bin
binlog-format=ROW
server-id=1
```

### 3. 数据库连接失败

确保 MySQL 服务已启动，且 `.env` 中的 `DB_PASSWORD` 配置正确：

```bash
# 检查 MySQL 是否运行
docker ps | grep mysql

# 或本地检查
mysql -h localhost -u root -p
```

### 4. 生产环境部署

- 使用 `application-prod.yml` 配置
- 通过环境变量注入所有敏感信息
- 建议使用外部 Redis/RDS 服务
- 关闭 debug 日志

## License

MIT
