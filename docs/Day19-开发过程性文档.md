# Day19-开发过程性文档

## 1. 今天这一阶段到底在做什么

Day19 不是继续拆新服务，也不是补新的业务功能。

这一天真正要解决的是：

- 在 Day18 已经形成多服务主链之后
- 补上请求级唯一标识 `traceId`
- 让 `gateway -> sky-server -> product-service` 的日志能够被同一个标识串起来

一句话概括：

- Day18 解决“服务能不能拆开并互相调用”
- Day19 解决“拆开以后出了问题，能不能快速知道是哪一段出了问题”

---

## 2. 今天要补的核心能力

当前项目在 Day18 之后已经具备：

- `Nacos` 注册发现
- `Gateway` 统一入口
- `OpenFeign` 跨服务调用
- `product-service` 独立承接商品查询能力

但服务一多，新的问题就出现了：

- 同一次请求会经过多个服务
- 如果某一段报错，日志会散落在不同容器里
- 只靠人工按时间逐个翻日志，定位成本会明显上升

所以 Day19 的核心目标是：

- 给请求分配或沿用同一个 `traceId`
- 在入口层、服务层和 Feign 出口把它传下去
- 让日志能够按同一个 `traceId` 串出完整主链

---

## 3. 今天最终做成了什么

### 3.1 Gateway 入口接入 `traceId`

已完成：

- [TraceIdGlobalFilter.java](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-gateway/src/main/java/com/sky/gateway/filter/TraceIdGlobalFilter.java)

这一步负责：

- 优先读取请求头里的 `X-Trace-Id`
- 如果没有则在 Gateway 生成新的 `traceId`
- 回写到请求头和响应头
- 在网关日志中打印请求开始/结束记录

### 3.2 服务内日志接入 `MDC`

已完成：

- [sky-server TraceIdFilter.java](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-server/src/main/java/com/sky/filter/TraceIdFilter.java)
- [product-service TraceIdFilter.java](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/product-service/src/main/java/com/sky/product/filter/TraceIdFilter.java)

这一步负责：

- 从请求头读取 `traceId`
- 放入 `MDC`
- 让业务日志自动带上 `traceId=...`

### 3.3 Feign 调用继续透传 `traceId`

已完成：

- [FeignTraceConfiguration.java](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-server/src/main/java/com/sky/config/FeignTraceConfiguration.java)

这一步的作用是：

- 在 `sky-server -> product-service` 的 Feign 调用中
- 读取当前线程 `MDC` 中的 `traceId`
- 主动塞进下游请求头

这也是 Day19 最关键的一步，因为它决定了：

- `traceId` 不是只停在网关层
- 而是真的能跨服务继续传下去

### 3.4 三个服务日志格式补齐

已完成：

- [sky-gateway application.yml](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-gateway/src/main/resources/application.yml)
- [sky-server application.yml](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-server/src/main/resources/application.yml)
- [product-service application.yml](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/product-service/src/main/resources/application.yml)

目标是让日志里显式出现：

- `traceId=...`

这样后续查日志时，才能直接按同一个标识检索整条链路。

### 3.5 Day19 最容易混淆、但最重要的源码理解点

这一阶段除了“把 `traceId` 做出来”，还有一个很重要的认知要站住：

- `Feign` 不是网络中间独立存在的一层
- 而是调用方 `sky-server` 内部的远程调用组件

这意味着：

- `sky-server -> product-service` 之所以需要 `Feign`
- 是因为 `sky-server` 这一侧需要**主动构造并发出请求**

而 `product-service -> sky-server` 返回响应时，并不是再新发起一次请求。

它做的是：

- 沿着原来的 HTTP 连接
- 把处理结果写回给调用方

所以 Day19 这里非常值得记住的一句话是：

- **Feign 只出现在请求发起方；响应返回方只是复用原连接回包。**

### 3.6 为什么 `traceId` 不会在 Feign 这一层丢掉

这也是 Day19 的源码复盘主轴。

完整过程是：

1. `gateway` 入口先接入或生成 `traceId`
2. 请求进入 `sky-server` 后，由服务端 Filter 把 `traceId` 放进 `MDC`
3. 当 `sky-server` 里业务代码调用 `ProductClient` 时，`Feign` 会准备发送下游 HTTP 请求
4. 这时 [FeignTraceConfiguration.java](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-server/src/main/java/com/sky/config/FeignTraceConfiguration.java) 里的 `RequestInterceptor` 会：
   - 从当前线程 `MDC` 读取 `traceId`
   - 主动写入下游请求头 `X-Trace-Id`
5. `product-service` 收到请求后，再由自己的 `TraceIdFilter` 从请求头取出 `traceId` 放进 `MDC`
6. 所以下游日志里也能打印出同一个 `traceId`

也就是说，Day19 真正做成的不是“日志里多了个字段”，而是：

- **把入口层拿到的请求标识，借助 `MDC + Feign RequestInterceptor + 服务端 Filter`，稳定接力到了下游服务。**

### 3.7 `MDC` 在 Day19 里到底扮演什么角色

`MDC` 可以先理解成：

- 当前线程随身带着的一小份日志上下文

在 Day19 里，它负责的不是跨服务传值，而是：

- 让**当前服务内部**的日志自动带上 `traceId`

所以最准确的分工是：

- 请求头：负责跨服务传递 `traceId`
- `MDC`：负责当前服务内日志自动打印 `traceId`

这也是为什么：

- `sky-server` 先把请求头中的 `traceId` 放进自己的 `MDC`
- `product-service` 收到下游请求后，也要再放进**自己**的 `MDC`

因为 `MDC` 本质上只是本服务、当前线程的本地上下文，它不会自己穿过网络同步到下游服务。

---

## 4. 这次最关键的验证闭环

### 4.1 第一步：验证 `traceId` 能从请求头进入并回写响应头

验证请求：

- `GET /user/shop/status`
- 请求头：`X-Trace-Id: day19-verify-001`

第一次结果：

- 命中 `502 Bad Gateway`

定位结论：

- 不是 Day19 功能失败
- 而是 `gateway` 处在短暂未就绪窗口

重试后结果：

- 返回 `200`
- 响应头出现 `X-Trace-Id: day19-verify-001`

证明：

- `traceId` 已经能够从入口接入并回写

### 4.2 第二步：验证 `gateway` 与 `sky-server` 日志能否拿到同一个 `traceId`

验证结果：

- `gateway` 日志出现：
  - `gateway request start: traceId=day19-verify-001`
  - `gateway request end: traceId=day19-verify-001`
- `sky-server` 日志出现：
  - `traceId=day19-verify-001 request start`
  - `traceId=day19-verify-001 用户端获取店铺营业状态：营业中`

证明：

- `traceId` 不只存在于请求头
- 已经真正进入网关日志和后端业务日志

### 4.3 第三步：验证 `traceId` 能否通过 Feign 继续传到 `product-service`

第一次尝试：

- 使用购物车加购接口触发跨服务调用
- 返回 `401 Unauthorized`

定位结论：

- 不是 `traceId` 丢了
- 是旧用户端 JWT 过期

第二次尝试：

- 换新 token 后，请求成功
- 但 `product-service` 日志里搜不到 `day19-verify-002`

继续定位后确认：

- 这次命中的是“购物车已有记录，直接本地数量加一”的分支
- 没有触发 `Feign -> product-service`

修正方式：

- 先清空购物车
- 再用 `day19-verify-003` 从空购物车重新加购

最终结果：

- `product-service` 日志中出现：
  - `traceId=day19-verify-003 request start: method=GET, uri=/rpc/products/dishes/71`
  - `traceId=day19-verify-003 Parameters: 71(Long)`
  - `traceId=day19-verify-003 request end: method=GET, uri=/rpc/products/dishes/71, status=200`

最终证明：

- `traceId` 已经从 `gateway`
- 进入 `sky-server`
- 再通过 Feign 成功透传到 `product-service`

### 4.4 第四步：补做真实页面与小程序入口验证

前面的三步证明了：

- 后端命令行验证成立

但 Day19 不能只停在“命令行能通”，还要补一轮真实入口验证。

#### 管理端真实页面验证

验证方式：

- 浏览器访问 `http://localhost:8081`
- 登录管理端后点击“菜品管理”
- 在浏览器 Network 中检查响应头

验证结果：

- 响应头中拿到：
  - `x-trace-id: 0edb1e12e0284e04ba4a100d1e4be1db`
- 同时在日志中确认：
  - `gateway` 出现同一 `traceId`
  - `sky-server` 的 `/admin/dish/page` 业务日志出现同一 `traceId`

证明：

- 真实浏览器页面入口下
- `nginx -> gateway -> sky-server`
- 已经能够自动生成并串联同一个 `traceId`

#### 用户端真实跨服务验证

为了让用户侧真实请求也走完整 Day19 主链，这次还补了一个关键修正：

- [miniProgram/utils/env.js](file:///D:/ProgramFiles/CodeProjects/PeakSend/miniProgram/project-rjwm-weixin-uniapp-develop-wsy/utils/env.js)

修正原因是：

- 小程序原来直连 `8080`
- 会绕过 `gateway`
- 这样不利于验证 Day19 的完整入口链路

所以本次将小程序入口统一改成：

- `http://localhost:8081`

真实验证动作：

- 在小程序中执行加购“王老吉”
- 操作时间点：`11:07` 左右

验证结果：

- 对应 `traceId`：
  - `0f3401067b5b4e86900e5d6f41639d23`
- `sky-server` 日志确认：
  - 收到 `POST /user/shoppingCart/add`
  - `ShoppingCartDTO(dishId=46, ...)`
  - 最终新增购物车记录，菜品名为“王老吉”
- `product-service` 日志确认：
  - 收到 `GET /rpc/products/dishes/46`
  - 状态 `200`
  - 同样带有一致的 `traceId`

最终证明：

- 真实用户入口下
- `小程序 -> nginx -> gateway -> sky-server -> product-service`
- 这条链也已经被同一个 `traceId` 串起来

---

## 5. 今天补做的正式量化

对应正式留档：

- [trace_observability.md](file:///D:/ProgramFiles/CodeProjects/PeakSend/docs/upgrade-metrics/trace_observability.md)

### 5.1 样本说明

- 验证链路：
  - `nginx(8081) -> gateway(8082) -> sky-server(8080) -> Feign -> product-service(8083)`
- 样本轮次：
  - 第 1 轮：`10` 次
  - 第 2 轮：`20` 次
- 合计：
  - `30` 次

### 5.2 业务主链成功率

- 第 1 轮：
  - `10/10`
- 第 2 轮：
  - `20/20`
- 合计：
  - `30/30`

### 5.3 `traceId` 串联完整率

- 第 1 轮：
  - `10/10`
- 第 2 轮：
  - `20/20`
- 合计：
  - `30/30`

### 5.4 当前最适合的表达口径

- 基于 `Gateway Filter + MDC + Feign RequestInterceptor` 实现 `traceId` 全链路透传，在购物车跨服务链路补测 `30` 次请求中，主链调用成功率与三段日志串联率均为 `100%`。

---

## 6. 今天最有价值的排障点

### 6.1 `502` 不等于链路设计失败

这次验证里第一步打到过 `502`，但后续定位说明：

- 问题来自运行态窗口期
- 不是 `traceId` 机制本身失败

这说明：

- 做链路追踪验证时，不能只看单次返回码
- 要先分清“基础设施未就绪”还是“链路设计有问题”

### 6.2 查不到下游日志，不一定是透传失败

`day19-verify-002` 第一次没在 `product-service` 日志里出现，容易误判为：

- Feign 没透传请求头
- `product-service` 没接到 `traceId`

但实际根因是：

- 这次业务根本没有触发跨服务调用

这说明：

- 可观测性验证不能只看日志有没有
- 还必须确认业务流程是否真的走到了那条链路

### 6.3 真实入口不一定天然经过 Gateway

这次补真实用户侧验证时，还发现了一个很容易忽略的问题：

- 小程序原来的 `baseUrl` 直连 `8080`

这意味着：

- 虽然业务本身能跑
- 但它会绕过 `gateway`
- 从而无法完整验证 Day19 的入口层 `traceId` 接入逻辑

这说明：

- 做链路追踪验证时，除了看接口成不成功
- 还要先确认真实入口是不是走了你设计的那条主链

---

## 7. 今天这一阶段的高价值点

### 7.1 工程价值

- Day19 把项目从“多服务能跑”推进到了“多服务可查”
- `traceId` 的价值不在于多加了一个请求头
- 而在于把原本分散的日志变成了一条可检索的请求链

### 7.2 面试价值

这一轮最值得讲的不是：

- “我会用 Feign”
- “我会写 Filter”

而是：

- 服务一多，排障成本会迅速上升
- 所以我在 Gateway、服务端 Filter 和 Feign 拦截器三个位置补了 `traceId`
- 让 `gateway -> sky-server -> product-service` 的日志能按同一个请求标识串起来

### 7.3 这次源码复盘里最值得沉淀的理解

除了 `traceId` 本身，这次还有两个非常高价值的理解点：

#### 1）`Feign` 只负责发请求的一侧

- `sky-server` 之所以需要 `Feign`
- 是因为它要主动调用 `product-service`

而 `product-service` 返回响应时：

- 只是沿着同一条 HTTP 连接回包
- 不需要再使用 `Feign`

#### 2）`MDC` 不是跨服务自动同步的

- `MDC` 只存在于当前服务当前线程上下文
- 它不会自己穿过网络到下游服务

所以 Day19 必须再补：

- `Feign RequestInterceptor`

主动把 `traceId` 从本地 `MDC` 写入请求头，才能真正传到下游服务

### 7.4 当前最适合的一句话总结

- Day19 我没有继续扩大战线，而是优先补了 `traceId` 全链路透传，让项目在完成最小微服务拆分后，进一步具备了多服务环境下的日志串联和问题定位能力；当前基于购物车跨服务链路补测 `30` 次，主链成功率与日志串联率都达到 `100%`。
