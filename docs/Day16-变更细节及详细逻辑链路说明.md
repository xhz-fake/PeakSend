# Day16-变更细节及详细逻辑链路说明

## 1. Day16 这一阶段真正要解决的是什么

Day16 的问题，已经不是“能不能把抢购接口写通”。

Day15 其实已经完成了：

- Redis Lua 原子抢资格
- 一人一单
- 管理端创建活动
- 小程序真实抢购
- MySQL 同步落库

但如果继续停在 Day15，交易链上还缺两类非常典型的真实项目问题：

- 高峰请求为什么还要同步写库
- 待支付订单为什么要靠人工或定时扫描收尾

所以 Day16 选择同时解决两条非常经典的 MQ 题：

- 抢购链的削峰异步落库
- 订单链的延迟关单

一句话概括：

- Day15 解决了 Redis 原子抢资格
- Day16 解决了 MQ 如何承接后半段副作用

---

## 2. Day16 为什么把两条链放在一起做

这一天不是随便把 `RocketMQ` 接进来找地方硬塞。

而是因为项目里刚好存在两条非常适合接 MQ 的业务：

### 2.1 限量套餐抢购链

它的特点是：

- 前半段是高并发资格争抢
- 后半段是数据库最终持久化

这里非常适合做成：

- Redis 前置抢资格
- MQ 削峰异步落库

### 2.2 订单超时关单链

它的特点是：

- 当前不一定立刻执行
- 但到了某个未来时刻必须再处理一次

这里非常适合做成：

- 下单时预约一条未来消息
- 到点后再消费消息并执行状态校验

所以这一天做的不是两个孤立小功能，而是：

- 用一个 MQ，把“异步削峰”和“延迟处理”两种典型交易题一起打通

---

## 3. 抢购异步落库链是怎么改造的

核心业务文件：

- [FlashSaleSetmealActivityServiceImpl.java](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-server/src/main/java/com/sky/service/impl/FlashSaleSetmealActivityServiceImpl.java)

### 3.1 Day15 的原始链路

Day15 的主链大致是：

1. Java 侧拿到活动 id 和用户 id
2. 调 Lua 脚本完成 Redis 原子校验和预扣
3. 如果 Redis 成功：
   - 同步扣 MySQL 活动表库存
   - 同步插抢购记录
4. 如果同步持久化失败：
   - 回补 Redis

这条链能用，但问题也很明显：

- 抢购接口返回时间仍然要被数据库写入拖住
- 峰值请求会继续把 MySQL 顶到主链上

### 3.2 Day16 的目标链路

Day16 改造成：

1. Java 侧拿到活动 id 和用户 id
2. 调 Lua 脚本完成 Redis 原子校验和预扣
3. 如果 Redis 成功：
   - 立即发送抢购异步落库消息
   - 接口直接返回抢购成功结果
4. 消费端收到消息后：
   - 幂等判断
   - 扣 MySQL 活动表库存
   - 插抢购记录

这样主链路真正留下的事情只剩两件：

- 在 Redis 里抢资格
- 把持久化任务交给 MQ

---

## 4. 抢购消息体、生产者和消费者怎么设计

### 4.1 消息体

文件：

- [FlashSaleOrderCreateMessage.java](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-server/src/main/java/com/sky/mq/message/FlashSaleOrderCreateMessage.java)

消息里放的是：

- `activityId`
- `userId`
- `orderNo`
- `reservedTime`

这个设计的关键点是：

- 不直接把整份活动对象塞进消息
- 只传消费端真正需要的业务定位字段

这样消息更轻，也更稳定。

### 4.2 生产者

文件：

- [RocketMqFlashSaleOrderCreateMessagePublisher.java](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-server/src/main/java/com/sky/mq/publisher/RocketMqFlashSaleOrderCreateMessagePublisher.java)

发送逻辑里最关键的变化是：

- 使用 `MessageBuilder.withPayload(message).build()`

而不是直接裸发对象。

这一步的实际价值是：

- 让发送形式和项目里另一条 MQ 链保持一致
- 避免第一轮联调里那种“消息看起来发了，但消费侧业务没真正落好”的不稳定状态继续扩大

### 4.3 消费者

文件：

- [FlashSaleOrderCreateConsumer.java](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-server/src/main/java/com/sky/mq/consumer/FlashSaleOrderCreateConsumer.java)

消费者最终只保留两件事：

- 打“收到消息”日志
- 调事务 service

也就是说，consumer 本身不再承担完整业务事务。

---

## 5. 为什么要把消费业务拆进独立事务 Service

这是 Day16 最关键的一次修正。

新增文件：

- [FlashSaleAsyncPersistenceService.java](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-server/src/main/java/com/sky/service/FlashSaleAsyncPersistenceService.java)

### 5.1 第一次失败时的真实现象

第一次联调时，现象非常诡异：

- 抢购接口返回成功
- Redis 库存已经减了
- Redis 用户集合已经写了
- producer 发送成功日志出现了
- RocketMQ topic 里也能看到消息
- consumer progress 也前进了

但是：

- MySQL 没插抢购记录
- MySQL 活动库存没减
- 没有任何消费成功业务日志

### 5.2 这说明了什么

这说明不能只看：

- topic 有没有消息
- offset 有没有前进

因为这只能证明：

- 框架层消息流转发生过

但不能证明：

- 你的业务事务真的成功了

### 5.3 最终修法

最后采用的正式修法是：

- listener 只负责接消息
- 真正事务逻辑放进独立 service
- service 里打幂等、查活动、扣库存、插记录、打成功日志

这样拆开之后，职责非常明确：

- consumer 负责“收”
- service 负责“落”

这也是第二轮复测能真正成功的关键。

---

## 6. 抢购异步消费侧到底做了哪些事

`FlashSaleAsyncPersistenceService.persistOrder(...)` 核心做了四件事。

### 6.1 幂等判断

先查：

- `activityId + userId`

如果数据库里已经存在抢购记录，就直接跳过。

这一步的意义是：

- 防止 RocketMQ 重试或重复消费时再次产生副作用

### 6.2 活动存在性校验

再查活动主记录是否还存在。

如果活动都没了，还继续落抢购记录就没有业务意义了，所以这里直接抛异常。

### 6.3 数据库库存扣减

调用：

- [FlashSaleSetmealActivityMapper.java](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-server/src/main/java/com/sky/mapper/FlashSaleSetmealActivityMapper.java)

里的：

- `decreaseStock(...)`

这个 SQL 只在 `stock > 0` 时才会成功扣减。

这一步很重要，因为它说明：

- Redis 前面虽然已经预扣了资格
- MySQL 这里仍然保留最终落库层的库存兜底判断

### 6.4 插入抢购记录

调用：

- [FlashSaleSetmealOrderMapper.java](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-server/src/main/java/com/sky/mapper/FlashSaleSetmealOrderMapper.java)

插入抢购成功记录，带上：

- 活动名
- 套餐名
- 图片
- 价格
- 抢购单号
- 创建时间

这样用户后续查“我的抢购记录”时，就不需要再做太重的二次拼装。

---

## 7. 订单延迟关单链是怎么设计的

核心业务文件：

- [OrderServiceImpl.java](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-server/src/main/java/com/sky/service/impl/OrderServiceImpl.java)

### 7.1 为什么它适合用延迟消息

超时关单这种场景有一个典型特点：

- 订单创建时什么都不用立刻取消
- 但未来某个固定时间点必须重新检查一次

这类问题如果只靠定时轮询，通常会有两个问题：

- 精度不够细
- 扫描会产生很多无效数据库压力

所以 Day16 选择的是：

- 订单创建成功后，就预约一条未来消息
- 到点后再处理

### 7.2 生产者发送了什么

消息体文件：

- [OrderDelayCloseMessage.java](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-server/src/main/java/com/sky/mq/message/OrderDelayCloseMessage.java)

里面包含：

- `orderId`
- `userId`
- `orderNumber`
- `orderTime`

生产者文件：

- [RocketMqOrderDelayCloseMessagePublisher.java](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-server/src/main/java/com/sky/mq/publisher/RocketMqOrderDelayCloseMessagePublisher.java)

发送时除了消息本体，还会带上延迟等级。

这意味着：

- 延迟多久，不需要硬编码在业务主链里
- 可以交给配置控制

### 7.3 本地联调为什么改延迟等级

当前 Docker 本地环境里：

- [docker-compose.yml](file:///D:/ProgramFiles/CodeProjects/PeakSend/docker-compose.yml)

把：

- `SKY_ROCKETMQ_ORDER_DELAY_LEVEL`

改成了：

- `3`

它的目的不是业务改版，而是：

- 让本地验证能在十几秒内拿到结果

这点一定要分清，因为它只是联调态优化。

---

## 8. 延迟关单消费端为什么也拆进独立事务 Service

新增文件：

- [OrderDelayCloseHandleService.java](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-server/src/main/java/com/sky/service/OrderDelayCloseHandleService.java)

### 8.1 第一次失败时的现象

第一次验证延迟关单时，现象和抢购异步链第一次失败几乎一模一样：

- 订单创建成功
- 延迟消息发送成功
- topic 位点前进
- 但订单状态没有改
- 也没有消费成功日志

这再次说明：

- MQ 这一层“收到消息”不等于业务副作用已经发生

### 8.2 最终修法

最后也采用完全同样的职责拆分：

- listener 只接消息和打日志
- 真正事务逻辑下沉到独立 service

对应 consumer 文件：

- [OrderDelayCloseConsumer.java](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-server/src/main/java/com/sky/mq/consumer/OrderDelayCloseConsumer.java)

这样两条链在工程结构上也统一了：

- 生产者负责发
- listener 负责收
- 事务 service 负责真正落业务

---

## 9. 延迟关单消费侧到底做了哪些事

`OrderDelayCloseHandleService.closeIfPending(...)` 核心做了三件事。

### 9.1 先按订单号查订单

如果订单已经不存在，就直接跳过。

这是一层最基础的健壮性保护。

### 9.2 二次校验订单状态

这里只允许处理：

- `status = PENDING_PAYMENT`
- `payStatus = UN_PAID`

如果订单已经支付、已取消或已经流转到别的状态，就直接跳过。

这一步就是 Day16 延迟关单链最关键的兜底逻辑，因为它决定了：

- 已支付订单不会被误取消

### 9.3 执行取消更新

如果仍符合待付款状态，才更新：

- `status = CANCELLED`
- `payStatus = UN_PAID`
- `cancelReason = 支付超时，系统自动取消`
- `cancelTime = now`

也就是说，延迟消息不是“到点无脑取消”，而是：

- 到点后再按当前真实状态做一次确认

---

## 10. Day16 两条链为什么都要强调幂等和二次校验

这一天的 MQ 设计里，有两个必须记住的关键词：

- 幂等
- 二次校验

### 10.1 抢购异步落库为什么要幂等

因为 MQ 消费天然可能出现：

- 重试
- 重投
- 重复消费

如果不先判断这条抢购记录是否已经存在，就可能：

- 重复扣库存
- 重复插记录

这里“幂等”最容易被误解成：

- 让请求绝对不要重复进来

但它真正的意思是：

- **同一个业务动作执行 1 次和执行多次，最终结果应该一致**

也就是说，幂等并不是“重复不会发生”，而是：

- **重复发生时，系统仍然不会产生额外副作用**

这也是为什么在 MQ 场景里，consumer 必须主动做幂等保护。因为消息系统更关心：

- 可靠投递

所以消费端必须默认：

- 消息可能重试
- 消息可能重投
- 消息可能重复消费

在 Day16 抢购链里，真正需要防止重复发生副作用的地方有两个：

- MySQL 活动库存扣减
- 抢购成功订单插入

如果没有幂等，重复消费一次，就可能：

- 多扣一次 MySQL 库存
- 多插一条抢购记录

所以 `FlashSaleAsyncPersistenceService.persistOrder(...)` 里先按：

- `activityId + userId`

查是否已存在记录，本质上就是：

- 让“同一个用户在同一个活动里的同一次成功抢购”，即使被重复消费，也只会在数据库里真正落一次

这和项目的业务规则也是对齐的，因为这条链本身就要求：

- 一人一单

### 10.2 延迟关单为什么要二次校验

因为订单状态是会变化的。

从发出延迟消息到真正消费之间，订单可能已经：

- 被支付
- 被手动取消
- 被其他流程处理

所以消费端一定要重新确认当前状态，而不是只信“下单时曾经是待付款”。

### 10.3 `onMessage(...)` 为什么没人显式调用却会自动执行

这个点在复盘源码时也值得专门记住，因为它很容易在面试里被追问。

以：

- [FlashSaleOrderCreateConsumer.java](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-server/src/main/java/com/sky/mq/consumer/FlashSaleOrderCreateConsumer.java)
- [OrderDelayCloseConsumer.java](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-server/src/main/java/com/sky/mq/consumer/OrderDelayCloseConsumer.java)

为例，`onMessage(...)` 都不是业务代码手写调用的。

真正触发它执行的是：

1. Spring 启动时先把 consumer 注册成 Bean
2. `@RocketMQMessageListener` 告诉 RocketMQ Spring 组件：
   - 监听哪个 topic
   - 用哪个 consumerGroup
3. RocketMQ 再为这个 consumer 创建监听容器
4. 只要对应 topic 来了消息
5. 监听容器就会自动回调 `onMessage(...)`

所以这里更准确的理解应该是：

- `onMessage(...)` 是框架回调入口
- 不是业务代码普通的手动调用方法

也正因为这样，后端启动日志里才会出现：

- `Register the listener to container`
- `running container: DefaultRocketMQListenerContainer...`

这些日志本质上就在证明：

- 监听器已经被 RocketMQ 容器接管
- 后续 topic 到消息时，容器会自动驱动 `onMessage(...)`

---

## 11. Day16 的真实验证链路到底证明了什么

### 11.1 抢购异步链证明了三件事

第一：

- Redis 仍然负责高并发资格争抢

第二：

- MQ 已经真正承接了数据库副作用

第三：

- Redis、MQ、MySQL 三层没有各做各的，而是被串成了一条完整业务链

### 11.2 延迟关单链证明了三件事

第一：

- 下单后已经能预约未来处理动作

第二：

- 到期消费后不会无脑改状态，而是会重新校验

第三：

- 订单最终能自动从“待付款”流转到“已取消”

---

## 12. Day16 最值得讲给面试官的点

### 12.1 为什么要在抢购链里引入 MQ

可以这样答：

- 因为抢购场景前半段更关心的是资格判定和快速返回，而不是把所有数据库写操作都压在接口主链上。所以我先用 Redis + Lua 抢资格，再通过 RocketMQ 把库存落库和抢购记录插入异步化，主链路只保留最关键的校验和消息投递，减轻了峰值写库压力。

### 12.2 为什么消息消费成功不能只看 offset

可以这样答：

- 因为 offset 前进只能说明消息流转发生过，不能说明业务事务已经真正成功。Day16 我就真实遇到过“消息发了、位点动了、但数据库没变化”的情况。后来我把 listener 和事务 service 拆开，并且把数据库变化和消费成功日志作为最终验证标准，这样证据链才完整。

### 12.3 为什么延迟关单消费侧一定要二次校验订单状态

可以这样答：

- 因为延迟消息从发出到消费中间存在时间差，这段时间里订单状态可能已经变化。如果消费端不重新检查订单是否仍为待付款，就可能把已经支付的订单误取消。所以我的实现里会先按订单号查当前状态，只有订单仍然是待付款且未支付时才执行取消。

---

## 13. Day16 最值得反复记住的几句话

- MQ 的价值不是“会发消息”，而是把不必同步完成的副作用移出主链路。
- offset 前进不等于业务成功，数据库变化和业务日志才是最终证据。
- listener 负责收消息，事务 service 负责落业务，这种职责拆分会让 MQ 链更稳。
- 延迟消息最适合解决“现在不处理，但未来某个时刻必须再处理”的交易类问题。
- 延迟关单不是到点无脑取消，而是到点后二次校验后再决定是否取消。

---

## 14. 重启后补做的运行态验证，进一步证明了 Day16 方案是稳的

Day16 第一次真实联调跑通之后，又补做了一轮“整机重启 + 容器重新拉起后的再验证”。

这轮验证很重要，因为它不再只是证明：

- 首次部署时能跑通

而是进一步证明：

- 重启之后主链还能不能继续正常工作
- 网关、缓存和 MQ 这些运行态组件在容器重建后会不会暴露新的问题

### 14.1 nginx 可能还握着旧 backend IP，导致接口 502

重启后，第一次打管理端活动列表接口时，现象是：

- 接口直接返回 `502`
- 但 backend 容器其实已经正常启动

最终排查发现：

- nginx upstream 写的是 `backend:8080`
- 但 nginx 运行态还在使用 backend 旧容器的解析结果
- backend 容器重建后 IP 已变化，nginx 仍然往旧地址转发

最终处理方式很简单：

- 直接重启 nginx

这一步的工程价值在于：

- 它说明容器重建后的问题，不一定来自后端代码，也可能来自运行态组件握着旧解析结果
- 以后遇到 `502`，排查顺序不能只盯着 JWT、接口路径或业务逻辑，还要检查 gateway/nginx 到上游容器的真实连接关系

### 14.2 Redis 活动缓存丢失后，`ensureActivityCacheLoaded` 能把活动重新拉回缓存

重启后再次看活动 `5` 的 Redis key，发现：

- `flashsale:activity:5` 不存在
- `flashsale:activity:5:users` 也不存在

这时候如果只从缓存层看，会误以为活动数据已经丢了。

但再次请求：

- `POST /user/flashSaleSetmealActivity/seize/5`

系统没有返回“活动不存在”，而是正确返回：

- `限量套餐活动已结束`

这说明请求进入 `seize(activityId)` 后，`ensureActivityCacheLoaded(activityId)` 这条兜底逻辑已经生效：

1. 先发现 Redis 里没有活动 key
2. 再回 MySQL 查询活动
3. 然后调用 `syncActivityCache(activity)` 重建活动 Hash 和用户集合
4. Lua 脚本再基于重建后的缓存执行校验

后续再次检查 Redis，也验证了这一点：

- `flashsale:activity:5` 已恢复存在
- `stock = 1`
- `status = 1`
- `flashsale:activity:5:users` 已恢复
- 用户集合基数为 `1`

也就是说，Day16 抢购链不只是“缓存存在时能工作”，而是：

- **缓存丢失后也能通过请求回源 DB 自动恢复**

### 14.3 这轮补验证顺手拿到了更适合写进简历的量化数字

#### 抢购异步链

以订单号：

- `FS541786882218615`

对应的日志时间为：

- `20:10:18.623` 发送异步落库消息
- `20:10:18.630` Consumer 收到消息
- `20:10:18.643` 异步落库成功

所以当前本地环境下可以得到：

- 消息发送 -> 消费收到：约 `7ms`
- 消息发送 -> MySQL 落库成功：约 `20ms`

#### 延迟关单链

以订单：

- `orderId = 14`
- `orderNumber = 1786938624333`

查询得到：

- `order_time = 11:50:24`
- `cancel_time = 11:50:34`
- `delay_seconds = 10`

所以当前本地环境下可以得到：

- 延迟关单执行耗时约 `10 秒`

#### 一致性结果

以活动：

- `activityId = 5`

MySQL 查询到：

- 活动库存 `stock = 1`
- 用户 `4` 的抢购记录数 `order_count = 1`

Redis 重建后查询到：

- 缓存库存 `stock = 1`
- 用户集合基数 `SCARD = 1`

这说明当前版本在“重启后缓存丢失 -> 请求触发回源 -> 缓存重建完成”的情况下，Redis 与 MySQL 最终仍然一致。

### 14.4 这些数字在文档、简历和面试里的正确定位

这轮补验证拿到的：

- 抢购异步落库约 `20ms`
- 延迟关单约 `10s`

它们的正确定位，不应该理解成：

- 一份严格意义上的正式性能压测报告

而应该理解成：

- 当前联调环境下，通过多侧对账拿到的代表性结果

Day16 里真正让这些数字站得住的，不是“测了多少轮”，而是：

1. 关键日志时间点可以对上
2. Redis 状态能对上
3. MySQL 最终结果能对上
4. 业务成功日志能对上

也就是说，Day16 当前更有价值的方法论是：

- **链路关键时间点采样**
- **Redis / MQ / MySQL 多侧交叉验证**

所以更适合对外表达成：

- 抢购异步落库时延约 `20ms`，达到毫秒级
- 延迟关单触发耗时约 `10s`，达到秒级

而不必执着于把它包装成一份“完整性能基线报告”。
