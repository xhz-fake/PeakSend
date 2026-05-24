# Day5-变更细节及详细逻辑链路说明

## 1. Day5 这一阶段到底做了什么

Day5 当前主线是：

- 店铺营业状态

这一天和 Day3、Day4 很不一样。

它不是继续补一个多表 CRUD 模块，而是第一次把一个中间件真正接进项目里，用来承接一个全局共享状态：

- 店铺营业中
- 店铺打烊中

最终我们真正打通了这三条链：

- 管理端查询店铺营业状态
- 管理端修改店铺营业状态
- 用户端查询店铺营业状态

并且这次不只是接口返回成功，而是同时完成了：

- Redis 环境接通
- VirtualBox 端口转发验证
- APIFox 联调验证
- 后端日志验证

## 2. 为什么 Day5 不再是传统 CRUD

前面的 Day3、Day4 主要练的是：

- 数据库建模
- 主表和子表/关系表联动
- 事务
- 删除前校验
- 状态启停的业务规则

但 Day5 的重点变成了：

- 全局状态存储
- 中间件接入
- 管理端和用户端共享同一份状态源

这意味着我们第一次把注意力从：

- “表怎么设计”

转向：

- “这个状态应该放在哪里更合适”

最终这次选择的是：

- Redis

而不是额外新建一张数据库表。

## 3. 为什么这里用 Redis 而不是 MySQL

这次店铺营业状态本质上只是一个非常轻量的全局值：

- `1`：营业中
- `0`：打烊中

它有几个特点：

- 数据量极小
- 会被频繁读取
- 管理端要读和改
- 用户端也要读

因此它更像一个“全局共享配置”，而不是复杂的业务明细数据。

所以这次用 Redis 很合适，因为：

- 读写轻量
- 访问速度快
- key-value 模型非常适合保存单个状态值

这里真正存进去的 key 是：

- `SHOP_STATUS`

## 4. Day5 代码结构是怎么搭起来的

这次 Day5 没有继续补 Service、Mapper、XML，而是直接从 Controller 起一个最小闭环。

### 管理端

管理端入口在：

- `peaksend-backend/sky-server/src/main/java/com/sky/controller/admin/ShopController.java`

里面实现了两个接口：

- `GET /admin/shop/status`
- `PUT /admin/shop/{status}`

它们分别负责：

- 从 Redis 读取当前店铺状态
- 把新的店铺状态写入 Redis

### 用户端

用户端入口在：

- `peaksend-backend/sky-server/src/main/java/com/sky/controller/user/ShopController.java`

里面实现了一个接口：

- `GET /user/shop/status`

它负责：

- 从 Redis 读取当前店铺状态

### 配置

Redis 连接配置写在：

- `peaksend-backend/sky-server/src/main/resources/application-dev.yml`

这次项目通过本机的：

- `127.0.0.1:6379`

去连接 Redis。

但这里有一个关键前提：

- 这个 `6379` 不是 Windows 本机直接装的 Redis
- 而是通过 VirtualBox 端口转发，连到 Ubuntu 虚拟机里的 Redis

## 5. 管理端查询营业状态这条链是怎么走的

请求是：

- `GET /admin/shop/status`

整体链路很短，但非常典型：

1. 前端或 APIFox 发起 GET 请求
2. Spring MVC 命中管理端 `ShopController`
3. Controller 调用 `redisTemplate.opsForValue().get("SHOP_STATUS")`
4. 如果 Redis 里还没有这个 key，就兜底返回 `0`
5. 最终把状态作为 `Result<Integer>` 返回给前端

这里我们特意加了一个兜底逻辑：

- 如果 Redis 中没有 `SHOP_STATUS`
- 默认返回：
  - `0`

也就是：

- 默认按“打烊中”处理

这样做的意义是：

- 第一次启动项目时，不会因为 Redis 里还没有值就返回 `null`
- 前端拿到的是一个明确的业务状态

## 6. 管理端修改营业状态这条链是怎么走的

请求是：

- `PUT /admin/shop/1`

这里的：

- `1`

是通过：

- `@PathVariable`

从路径里拿到的。

整个写入链非常直接：

1. 请求进入管理端 `ShopController`
2. Controller 从路径中拿到 `status`
3. 调用：
   - `redisTemplate.opsForValue().set("SHOP_STATUS", status)`
4. 把新的营业状态写入 Redis
5. 返回成功响应

这里要特别记住一点：

- 真正完成“营业/打烊”状态切换的，不是虚拟机命令行
- 而是项目里的后端接口

命令行在这次开发里做的事情更多是：

- 启动 Redis
- 排查连接问题

而不是日常业务入口。

## 7. 用户端为什么能读到同一份状态

用户端请求是：

- `GET /user/shop/status`

它虽然走的是另一个 Controller，但读的仍然是 Redis 里的同一个 key：

- `SHOP_STATUS`

所以这次我们真正完成的是一条“共享状态源”链路：

```text
管理端修改状态
-> 写入 Redis
-> 管理端查询时读 Redis
-> 用户端查询时也读 Redis
-> 两端看到的是同一份状态
```

这就是 Day5 最值得理解的地方：

- 管理端不是只改自己页面上的某个值
- 而是在改一个对全系统都生效的共享状态

## 8. Day5 遇到的第一个真实问题：接口 500

刚开始调：

- `GET /admin/shop/status`

时，接口直接返回：

- `500 Internal Server Error`

这里一开始很容易误以为是：

- Controller 写错了
- Spring Boot 配置没生效
- RedisTemplate 写法有问题

但这次我们没有直接猜着改，而是按正确顺序排查：

1. 先确认 Redis 是否真的启动
2. 再确认虚拟机 IP 和端口转发是否可用
3. 再确认 Spring Boot 是否加载了 Redis 配置
4. 最后才定位到真正根因

最终确认根因不是 Java 代码，而是：

- Ubuntu 虚拟机里的 Redis 开启了 `protected mode`

在 VirtualBox 端口转发场景下，Redis 把来自宿主机后端的请求视为外部连接，因此直接拒绝访问。

后端日志里的核心报错是：

- `DENIED Redis is running in protected mode`

## 9. 这个 500 最后是怎么解决的

修复方式不是改业务代码，而是调整 Redis 运行状态。

我们在虚拟机里执行了：

```bash
redis-cli CONFIG SET protected-mode no
```

执行后再次请求：

- `GET /admin/shop/status`

接口立刻恢复正常，返回：

- `data = 0`

这一步非常有价值，因为它说明：

- 有些接口 500 的根因根本不在 Controller / Service / Mapper
- 而是在中间件环境和连接策略

这也是 Day5 和前面几天的最大区别之一：

- 你开始碰到“代码没错，但环境和中间件链路不通”的真实问题了

## 10. Day5 当前修改的关键文件

这次 Day5 关键修改集中在：

- `peaksend-backend/sky-server/src/main/java/com/sky/controller/admin/ShopController.java`
- `peaksend-backend/sky-server/src/main/java/com/sky/controller/user/ShopController.java`
- `peaksend-backend/sky-server/src/main/resources/application-dev.yml`

和前面 Day3、Day4 相比，这次最大的结构变化是：

- 没有继续新增 Mapper 和 XML
- 没有继续操作 MySQL 表
- 而是直接通过 Controller + RedisTemplate 完成一个状态型业务闭环

## 11. Day5 当前最值得记住的几条理解

如果现在只抓最核心的 6 条，先记这些：

- 店铺营业状态是一个全局共享状态，不是复杂业务明细
- 这次用 Redis，是因为 key-value 模型很适合保存轻量状态值
- 管理端负责修改和查询状态
- 用户端负责查询状态
- 管理端和用户端读的是 Redis 里的同一个 `SHOP_STATUS`
- 这次 500 的根因是 Redis `protected mode`，不是 Java 业务代码本身

## 12. Day5 当前阶段的真正收获

如果只从接口数量看，Day5 似乎只是补了三个非常简单的接口。

但从能力上看，这一天真正练到的是：

- 第一次把 Redis 作为项目中的状态存储接进来
- 第一次处理“管理端和用户端共享同一状态源”的业务
- 第一次碰到并解决中间件连接策略导致的 500
- 第一次真正理解“代码逻辑”和“运行环境”都可能是根因

所以 Day5 的关键不在于接口多不多，而在于：

- 你已经开始从“数据库业务开发”过渡到“中间件 + 状态型业务 + 环境联调”了

这一步非常重要，因为后面的 Day6 之后，项目会越来越多地进入：

- 用户端链路
- 缓存
- 下单流程
- 异步通知

Day5 就是这个转折点。
