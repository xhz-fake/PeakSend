# 限量套餐抢购异步落库量化留档

## 1. 这组数字对应什么能力

- `Redis + Lua` 先完成高并发抢资格
- `RocketMQ` 承接后半段异步落库
- 抢购主链从“同步写库”收敛为“先返回，再异步持久化”

## 2. 版本边界

- 当前稳定版本：`Day16`
- 对应链路：
  - `FlashSaleSetmealActivityServiceImpl.seize(...)`
  - `RocketMqFlashSaleOrderCreateMessagePublisher`
  - `FlashSaleOrderCreateConsumer`
  - `FlashSaleAsyncPersistenceService`
- 当前代表性样本：
  - `activityId=5`
  - `userId=4`
  - `orderNo=FS541786882218615`

## 3. 测量方法

- 先由用户端真实发起抢购请求，确认接口成功返回订单号
- 再按同一 `orderNo` 对齐三类时间点：
  - 生产者发送成功日志时间
  - 消费者收到消息日志时间
  - 异步落库成功日志时间
- 最后用 Redis 与 MySQL 对账，确认不是“只有日志，没有最终落库”

## 4. 原始数据

代表性日志时间点：

- 发送抢购异步落库消息成功：`2026-08-16 20:10:18.623`
- 收到限量套餐异步落库消息：`2026-08-16 20:10:18.630`
- 限量套餐异步落库消费成功：`2026-08-16 20:10:18.643`

由此得到当前稳定版本下的代表性结果：

- 发送消息 -> 消费收到：约 `7ms`
- 发送消息 -> MySQL 落库成功：约 `20ms`

链路对账结果：

- Redis 侧：`flashsale:activity:5` 库存从 `2` 变为 `1`
- MySQL 侧：
  - `flash_sale_setmeal_activity.id=5` 的 `stock=1`
  - `flash_sale_setmeal_order` 中 `activity_id=5 and user_id=4` 只有 `1` 条记录
- 运行态补证：
  - Redis 重启后活动缓存丢失
  - 再次请求后由 `ensureActivityCacheLoaded(activityId)` 自动回源重建
  - 重建后 Redis 与 MySQL 状态仍可对上

## 5. 最终结论

- 当前 Day16 稳定版本已经形成“Redis 抢资格 -> RocketMQ 异步落库 -> MySQL 最终持久化”的完整闭环
- 在当前代表性样本中，异步链从发消息到落库完成约 `20ms`
- 这组数据已经足够作为当前阶段简历与面试表达的代表性结果，后续如补更大样本压测，可继续在本文件追加

## 6. 可写进简历/话术库的句子

- 基于 `Redis + Lua + RocketMQ` 重构限量套餐抢购链路，将主链收敛为“抢资格 + 发消息”，异步落库代表性时延约 `20ms`，并通过 Redis、MySQL、日志三侧对账保证最终一致性。
