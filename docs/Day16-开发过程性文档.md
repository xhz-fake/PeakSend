# Day16-开发过程性文档

## 1. 今天这一阶段到底在做什么

Day16 不是继续给 Day15 叠页面。

这一天真正要解决的是两个更像真实交易系统的问题：

- 抢购成功后，主链路能不能不要同步直落数据库
- 待支付订单能不能在超时后自动关闭

所以 Day16 的核心目标是：

- 在 `PeakSend` 里正式接入 `RocketMQ`
- 把 Day15 的限量套餐抢购链升级成：
  - Redis 先抢资格
  - MQ 异步削峰
  - 消费端异步落库
- 把订单链补成：
  - 下单后发送延迟消息
  - 到期后自动关闭未支付订单
- 最后必须在真实 Docker 环境里把两条链都跑通

一句话概括：

- Day15 解决“谁能抢到”
- Day16 解决“抢到之后怎么异步落库，以及订单超时怎么自动收口”

---

## 2. 今天最终做成了什么

### 2.1 RocketMQ 基础能力落地

已完成：

- 后端工程补齐 `RocketMQ` 依赖与业务配置
- Docker 本地环境补齐 `RocketMQ NameServer` 和 `Broker`
- 新增抢购异步落库消息生产/消费链
- 新增订单延迟关单消息生产/消费链
- 本地 Docker 环境支持通过配置切换延迟等级，便于联调验证

### 2.2 Day15 抢购链升级为“Redis + MQ + DB”

已完成：

- 用户抢购成功后，不再同步写 MySQL
- 主链路改为：
  - Redis Lua 原子判定并预扣库存
  - 生产者发送抢购落库消息
  - 消费端异步扣活动表库存
  - 消费端异步插入抢购记录
- 消费端补齐幂等判断
- 生产失败时仍保留 Redis 回补逻辑

### 2.3 订单域延迟关单链落地

已完成：

- 用户提交订单后发送延迟关单消息
- 消费端收到延迟消息后再次校验订单状态
- 仅当订单仍为“待付款 + 未支付”时，才执行自动取消
- 自动写入：
  - `status = 6`
  - `cancelReason = 支付超时，系统自动取消`
  - `cancelTime`

### 2.4 真实验证闭环

已完成：

- 用户端 JWT 手工生成并真实调用受保护接口
- 管理端 JWT 手工生成并真实创建测试活动
- 抢购异步落库链真实跑通
- 延迟关单链真实跑通
- Redis / MySQL / RocketMQ / 后端日志四侧证据对齐

---

## 3. 今天最关键的工程决策

### 3.1 MQ 接进来后，主链路只做“抢资格 + 发消息”

今天最大的主线变化不是“加了一个中间件”。

真正的变化是：

- 抢购接口不再承担最终落库全流程

而是收敛成：

- Redis 先做高并发资格判定
- 如果成功，立即发送 MQ 消息
- 后续数据库副作用交给消费端异步完成

这样做的价值是：

- 主链路更短
- 峰值写库压力更容易被削平
- Day15 的 Redis 抢资格方案，终于和 MQ 异步化真正串起来了

### 3.2 消费端业务不能直接全堆在 listener 里

Day16 两条链第一次都踩到了同一个坑：

- MQ 位点前进了
- 但业务副作用没有真正落地

最后采取的正式修法是：

- listener 只负责接消息和打接收日志
- 真正带事务的业务逻辑拆进独立 Service

这一步很关键，因为它把：

- 消息接收职责
- 事务落库职责

明确拆开了，也让后续排障和日志观察都清晰很多。

### 3.3 延迟关单联调阶段，只改运行态延迟等级，不改业务语义

默认延迟等级太长，不适合本地联调等待。

所以最终没有去改业务代码里的“超时关单这个设计”，而是：

- 只在 Docker 本地环境变量里把 `SKY_ROCKETMQ_ORDER_DELAY_LEVEL` 调成 `3`

这样做的价值是：

- 本地验证更快
- 业务代码语义不被联调动作污染
- 后面切回正式延迟等级也更自然

---

## 4. 今天遇到的关键问题与处理

### 4.1 受保护接口不能直接打，先把 token 收掉

问题现象：

- 用户端活动接口和订单接口都受 JWT 拦截保护
- 直接打接口会 `401`

处理方式：

- 回读 `application.yml`、`UserController`、`JwtUtil`
- 明确用户端和管理端 token 的密钥、claim 和请求头名字
- 直接按项目真实规则手工生成 JWT

结果：

- 用户端接口成功打通
- 管理端接口成功打通
- 后续 Day16 真实验证入口全部可用

### 4.2 创建活动第一次 400，是 PowerShell 请求体和时间格式双重问题

问题现象：

- 一次是 JSON body 被 PowerShell/curl 组合弄坏
- 一次是 `LocalDateTime` 格式传成了秒级

处理方式：

- 改用 `Invoke-RestMethod`
- 回读 `JacksonObjectMapper`
- 把时间格式修正为：
  - `yyyy-MM-dd HH:mm`

结果：

- 成功创建测试活动：
  - `activityId = 3`

### 4.3 第一次抢购异步落库验证失败：消息发出去了，但数据库没变

问题现象：

- 抢购接口返回成功
- Redis 库存和用户集合已经变化
- 生产者日志显示消息发送成功
- MQ topic 有消息，consumer progress 也前进了
- 但 MySQL 库存和抢购记录没有变化
- 也没有消费成功业务日志

最终判断：

- 不是 broker、路由或连接问题
- 是消费逻辑虽然被框架接到了，但真正事务副作用没有稳定落下来

处理方式：

- 新增 `FlashSaleAsyncPersistenceService`
- consumer 改成只收消息和调 service
- publisher 发送方式统一成 `MessageBuilder.withPayload(...)`

结果：

- 第二轮复测成功
- 抢购链完成真实异步落库闭环

### 4.4 第一次延迟关单验证失败：位点前进了，但订单没取消

问题现象：

- 下单成功
- 延迟消息发送成功
- topic 位点前进
- 但十几秒后订单仍是待付款
- 没有消费成功业务日志

处理方式：

- 新增 `OrderDelayCloseHandleService`
- listener 改成只接消息、打日志、调 service

结果：

- 第二轮新订单复测成功
- 订单成功自动取消

### 4.5 重启后管理端接口 502：不是 JWT 问题，而是 nginx 还握着旧 backend IP

问题现象：

- Docker 容器重启后，`curl` 调管理端活动列表接口返回 `502`
- nginx 日志里显示：
  - `connect() failed (111: Connection refused)`
  - upstream 仍在转发到旧的 backend 容器 IP

排查过程：

- 先确认 backend 容器本身已正常启动并监听 `8080`
- 再确认当前 backend 容器真实 IP 已变化
- 最后回看 nginx 生效配置，发现 upstream 写的是：
  - `server backend:8080;`
- 但 nginx 运行态解析结果还停留在旧 IP

处理方式：

- 直接重启 `peaksend-nginx`
- 让 nginx 重新解析 `backend` 对应的新容器地址

结果：

- 管理端接口恢复正常
- 新生成的管理员 token 也成功打通活动列表接口

这一步的工程结论是：

- 容器重建后，即使 nginx 配置里写的是服务名，运行态也可能还握着旧解析结果
- 联调里遇到这类 `502`，不能只怀疑 JWT 或后端代码，要先检查 upstream 指向的容器地址是不是已经漂移

### 4.6 Redis 活动缓存重启后丢失，但请求可自动触发回源重建

问题现象：

- 重启后再次检查 `flashsale:activity:5` 相关 key，发现：
  - 活动 Hash 不存在
  - 用户集合也不存在
- 这时如果只看 Redis，会误以为活动状态已经丢失

验证过程：

- 再次请求：
  - `POST /user/flashSaleSetmealActivity/seize/5`
- 接口没有报“活动不存在”
- 而是正确返回：
  - `限量套餐活动已结束`

进一步确认：

- 再查 Redis，发现：
  - `flashsale:activity:5` 已被重新创建
  - `stock = 1`
  - `status = 1`
  - 用户集合 `flashsale:activity:5:users` 也已恢复
  - 集合基数为 `1`

结论：

- `ensureActivityCacheLoaded(activityId)` 这条兜底逻辑真实生效了
- 即使 Redis 活动缓存丢失，系统也能在请求到来时自动回源 MySQL 重建缓存
- 重建后的库存、状态、用户集合和 MySQL 最终落库状态保持一致

---

## 5. 今天完成的真实联调闭环

### 5.1 抢购异步落库链

已验证：

- 管理端新建活动：
  - `activityId = 4`
  - 活动名：`Day16 MQ复测活动`
  - 库存：`2`
- 用户调用：
  - `POST /user/flashSaleSetmealActivity/seize/4`
- 接口立即返回成功：
  - `orderNo = FS441786878087343`
- Redis 立刻变化：
  - `flashsale:activity:4.stock = 1`
  - `flashsale:activity:4:users` 包含用户 `4`
- MySQL 随后变化：
  - `flash_sale_setmeal_order` 记录数 `0 -> 1`
  - `flash_sale_setmeal_activity.stock` `2 -> 1`
- 后端日志出现完整闭环：
  - 发送成功
  - 收到消息
  - 消费成功

### 5.2 延迟关单链

已验证：

- 先通过“再来一单”回填购物车
- 再提交新订单：
  - `orderId = 13`
  - `orderNumber = 1786878546959`
- 下单后立即查询：
  - `status = 1`
  - `pay_status = 0`
- 等待约 12 秒后再次查询：
  - `status = 6`
  - `pay_status = 0`
  - `cancel_reason = 支付超时，系统自动取消`
- 后端日志出现完整闭环：
  - 发送延迟消息
  - 收到延迟消息
  - 自动取消成功

---

## 6. 今天拿到的最关键证据

### 6.1 RocketMQ 侧证据

已验证：

- 抢购异步落库 topic 存在
- 订单延迟关单 topic 存在
- consumer connection 在线
- consumer progress 会前进

这说明：

- 消息确实进了 broker
- 不是“压根没发出去”

### 6.2 Redis 侧证据

已验证：

- 抢购成功后 Redis 先发生变化
- `flashsale:activity:4.stock` 由 `2 -> 1`
- `flashsale:activity:4:users` 写入用户 `4`

这说明：

- Day15 的 Redis 原子抢资格链仍然成立
- Day16 只是把后面的持久化阶段异步化了

### 6.3 MySQL 侧证据

已验证：

- 抢购异步消费后：
  - 活动库存表真正减一
  - 抢购记录表真正插入一条记录
- 延迟关单消费后：
  - 订单状态真正改成已取消
  - 取消原因和取消时间真正落库

### 6.4 日志侧证据

已验证：

- 两条链都能看到：
  - 发送成功日志
  - 收到消息日志
  - 业务成功日志

这点很重要，因为它说明：

- 现在不是只有 offset 在动
- 而是消息消费后的业务副作用也真的发生了

### 6.5 今日重启后的补充量化结果

今天在整机重启、容器重新拉起后，又补拿到了 Day16 当前版本更适合写进简历的量化和一致性数据：

#### 6.5.1 延迟关单耗时

以订单：

- `orderId = 14`
- `orderNumber = 1786938624333`

为例，MySQL 实际查询结果为：

- `order_time = 2026-08-17 11:50:24`
- `cancel_time = 2026-08-17 11:50:34`
- `delay_seconds = 10`

说明：

- 当前本地 Docker 联调环境下，待付款订单会在 **约 10 秒** 后自动取消
- 这个结果与我们将本地延迟等级调小用于联调的设计一致

#### 6.5.2 抢购异步链消息投递到落库成功耗时

以抢购单号：

- `FS541786882218615`

对应日志为：

- `20:10:18.623` 发送限量套餐异步落库消息成功
- `20:10:18.630` Consumer 收到消息
- `20:10:18.643` 异步落库消费成功

可以得到：

- 消息发送 -> 消费收到：约 **7ms**
- 消息发送 -> MySQL 落库成功：约 **20ms**

说明：

- 当前本地环境下，抢购资格确认后的异步持久化几乎是瞬时完成
- RocketMQ 接入后，主链路不需要再等待同步写库

#### 6.5.3 MySQL 最终一致性结果

以活动：

- `activityId = 5`

为例，MySQL 查询结果为：

- `flash_sale_setmeal_activity.stock = 1`
- `flash_sale_setmeal_order` 中 `activity_id = 5 and user_id = 4` 的订单数为 `1`

说明：

- 活动库存只被真正扣减了 1 次
- 抢购订单只真正落库了 1 条
- 当前版本下没有出现重复订单副作用

#### 6.5.4 Redis 缓存重建后的最终一致性结果

在缓存丢失后再次请求活动 `5`，Redis 被自动回源重建，最终查得：

- `flashsale:activity:5` 已存在
- `stock = 1`
- `status = 1`
- `flashsale:activity:5:users` 已存在
- `SCARD = 1`

说明：

- Redis 活动库存、状态、用户集合与 MySQL 最终状态重新对齐
- 当前系统已具备“缓存丢失后自动恢复”的运行态韧性

#### 6.5.5 关于今天这些量化结果的正确口径

今天补拿到的这些数字，本质上都是：

- 当前本地 Docker 联调环境下的真实验证结果

它们的价值不在于“伪装成正式压测报告”，而在于：

1. 证明链路已经真实生效
2. 形成简历可写、面试可讲的代表性结果
3. 给后续如果真要做多轮统计时提供第一版基线

所以 Day16 当前可以沉淀的表达口径是：

- 抢购异步落库链：
  - 代表性结果约 `20ms`
  - 更适合写成“异步落库时延约 20ms / 毫秒级”
- 延迟关单链：
  - 代表性结果约 `10s`
  - 更适合写成“延迟关单触发耗时约 10 秒 / 秒级”

真正支撑这些数字可信度的，不是“我测了多少轮”，而是今天已经补齐的多侧证据链：

- 日志时间点
- Redis 状态
- MySQL 最终结果
- MQ 消费成功日志

---

## 7. 今天最值得沉淀的高价值点

### 7.1 MQ 的价值不是“会发消息”，而是缩短主链路

Day16 真正要讲清楚的不是 API 怎么调用 `RocketMQTemplate`。

而是：

- 为什么同步链路不能把所有事情都自己做完
- 为什么抢购主链应该尽快返回
- 为什么落库副作用更适合异步处理

### 7.2 消费端最怕“看起来消费了，其实业务没生效”

这一天两个最关键的排障案例都在说明同一件事：

- MQ 位点前进，不等于业务已经成功

真正可信的证据一定要回到：

- 数据库有没有变化
- 业务成功日志有没有出现
- Redis / DB / 日志三侧是否一致

### 7.3 延迟消息适合做“到点再处理”的交易类动作

超时关单这件事，如果一直靠定时扫描，会有两个天然问题：

- 精度不够
- 会产生很多无效扫描

延迟消息更适合这种场景，因为它更像：

- 订单创建时就预约一次未来处理动作
- 到点后再做一次状态校验

这也是 Day16 非常适合拿来讲交易系统设计的地方。

### 7.4 `onMessage(...)` 不是业务代码显式调用，而是 RocketMQ 监听容器自动回调

今天复盘源码时，补充确认了一个很适合面试被追问的基础点：

- `FlashSaleOrderCreateConsumer.onMessage(...)`
- `OrderDelayCloseConsumer.onMessage(...)`

这两个方法都不是在业务代码里手写调用的。

它们之所以会执行，是因为：

1. Spring 启动时先把 consumer 注册成 Bean
2. `@RocketMQMessageListener` 告诉 RocketMQ Spring 组件：
   - 这个类要监听哪个 topic
   - 使用哪个 consumerGroup
3. RocketMQ 为它创建监听容器
4. topic 来消息后，由监听容器自动回调 `onMessage(...)`

所以这里更准确的理解是：

- 不是“谁调用了这个方法”
- 而是“框架在消息到达时自动回调了这个方法”

这个点虽然基础，但面试里很容易被问来区分：

- 你是只会照着写 consumer
- 还是理解它其实是消息监听容器驱动的回调模型

---

## 8. 今日高价值问题 / 踩坑点

- JWT 不收掉，后面的真实联调入口根本打不开
- PowerShell 下请求体和时间格式问题，非常容易把“接口 400”误判成后端逻辑错误
- MQ offset 前进，不等于业务就真的成功了
- listener 能收到消息，不等于事务逻辑已经正确落地
- 延迟关单联调最好改运行态延迟等级，不要直接污染业务代码语义
- 幂等不是“不让重复来”，而是“即使重复来，最终副作用也不能重复发生”
- RocketMQ 场景下，consumer 必须默认消息可能会重复投递或重复消费
- 简历和面试里真正该强调的不是“我测了几次”，而是“我如何通过日志 + Redis + MySQL 多侧对账得出这个代表性结果”

---

## 9. 今天最终完成度判断

Day16 现在已经不是“第一版方案”。

它已经完成了：

- RocketMQ 环境接入
- 抢购异步落库链实现
- 抢购异步落库链真实验证
- 延迟关单链实现
- 延迟关单链真实验证
- 两条链第一次失败后的定位与修复
- Docker 本地联调态优化
- 重启后的二次真实验证
- 核心量化结果补采
- Redis 缓存丢失后的自动恢复验证

所以 Day16 当前的正确状态是：

- **代码实现已完成**
- **真实链路已验证**
- **核心量化已补齐**
- **可以正式进入文档、话术和 Git 收口阶段**

---

## 10. 证据清单

| 类型 | 文件名/位置 | 对应内容 |
|---|---|---|
| 文档 | [Day16-开发过程性文档.md](file:///D:/ProgramFiles/CodeProjects/PeakSend/docs/Day16-%E5%BC%80%E5%8F%91%E8%BF%87%E7%A8%8B%E6%80%A7%E6%96%87%E6%A1%A3.md) | Day16 过程留痕主文件 |
| 文档 | [Day16-变更细节及详细逻辑链路说明.md](file:///D:/ProgramFiles/CodeProjects/PeakSend/docs/Day16-%E5%8F%98%E6%9B%B4%E7%BB%86%E8%8A%82%E5%8F%8A%E8%AF%A6%E7%BB%86%E9%80%BB%E8%BE%91%E9%93%BE%E8%B7%AF%E8%AF%B4%E6%98%8E.md) | Day16 详细逻辑链路文档 |
| 代码 | [FlashSaleAsyncPersistenceService.java](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-server/src/main/java/com/sky/service/FlashSaleAsyncPersistenceService.java) | 抢购异步消费事务落点 |
| 代码 | [FlashSaleOrderCreateConsumer.java](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-server/src/main/java/com/sky/mq/consumer/FlashSaleOrderCreateConsumer.java) | 抢购异步消费入口 |
| 代码 | [RocketMqFlashSaleOrderCreateMessagePublisher.java](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-server/src/main/java/com/sky/mq/publisher/RocketMqFlashSaleOrderCreateMessagePublisher.java) | 抢购异步消息发送端 |
| 代码 | [OrderDelayCloseHandleService.java](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-server/src/main/java/com/sky/service/OrderDelayCloseHandleService.java) | 延迟关单事务落点 |
| 代码 | [OrderDelayCloseConsumer.java](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-server/src/main/java/com/sky/mq/consumer/OrderDelayCloseConsumer.java) | 延迟关单消费入口 |
| 代码 | [OrderServiceImpl.java](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-server/src/main/java/com/sky/service/impl/OrderServiceImpl.java) | 订单提交后发送延迟消息切口 |
| 代码 | [FlashSaleSetmealActivityServiceImpl.java](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-server/src/main/java/com/sky/service/impl/FlashSaleSetmealActivityServiceImpl.java) | 抢购主链接入 MQ 的业务切口 |
| 配置 | [docker-compose.yml](file:///D:/ProgramFiles/CodeProjects/PeakSend/docker-compose.yml) | RocketMQ 环境与本地延迟等级配置 |
| 配置 | [application-docker.yml](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-server/src/main/resources/application-docker.yml) | Docker 环境下 RocketMQ 业务开关与配置 |
