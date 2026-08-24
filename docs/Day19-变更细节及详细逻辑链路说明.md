# Day19-变更细节及详细逻辑链路说明

## 1. Day19 这一阶段真正要解决的是什么

Day19 不是继续拆新服务，也不是补新的业务功能。

Day18 之后，项目已经具备了最小微服务主链：

- `nginx -> gateway -> sky-server -> product-service`

但这时会立刻出现一个新的工程问题：

- 同一次请求会经过多个服务
- 日志被打散在多个容器里
- 一旦某一段报错，只靠时间戳去逐个翻日志，定位成本会明显上升

所以 Day19 真正要解决的是：

- 给每次请求分配或沿用一个统一的 `traceId`
- 让这个 `traceId` 能沿着主链一路传下去
- 让不同服务里的日志都带上同一个请求标识

一句话概括：

- Day18 解决“服务能不能拆开并互相调用”
- Day19 解决“拆开以后出了问题，能不能快速知道是哪一段出了问题”

---

## 2. 为什么 Day19 必须接在 Day18 后面做

Day18 之前，项目更像单体，问题定位通常只需要：

- 找 backend 日志
- 看接口有没有报错

但 Day18 之后，最小跨服务调用已经成立：

- 用户侧加购动作会进入 `sky-server`
- `sky-server` 会再去调用 `product-service`

这意味着后续排障时，至少要同时面对：

- `gateway`
- `sky-server`
- `product-service`

如果没有统一标识，同一次请求在三个服务里的日志会变成：

- 能看到很多日志
- 但不知道哪几条属于同一笔请求

所以 Day19 的价值不在于“多加一个请求头”，而在于：

- **把原本分散的日志，变成一条可检索、可串联的请求链。**

---

## 3. Day19 这次到底改了哪几类代码

这一阶段的关键代码主要分成五类。

### 3.1 公共常量与工具

新增：

- [TraceConstant.java](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-common/src/main/java/com/sky/constant/TraceConstant.java)
- [TraceIdUtil.java](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-common/src/main/java/com/sky/utils/TraceIdUtil.java)

它们负责：

- 统一 `traceId` 的 key
- 统一 `X-Trace-Id` 请求头名称
- 统一“读入后规范化 / 为空时生成”的逻辑

这一步的意义是：

- 避免 gateway、服务端 Filter、Feign 配置各写各的字符串常量
- 让后续跨模块逻辑保持一致

### 3.2 Gateway 入口层

新增：

- [TraceIdGlobalFilter.java](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-gateway/src/main/java/com/sky/gateway/filter/TraceIdGlobalFilter.java)

它负责：

- 优先读取外部请求头中的 `X-Trace-Id`
- 如果没有，则在 gateway 生成新的 `traceId`
- 把这个值写回转发请求头
- 同时写入响应头
- 在 gateway 日志中打印开始/结束日志

### 3.3 服务端日志上下文接入

新增：

- [sky-server TraceIdFilter.java](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-server/src/main/java/com/sky/filter/TraceIdFilter.java)
- [product-service TraceIdFilter.java](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/product-service/src/main/java/com/sky/product/filter/TraceIdFilter.java)

它们负责：

- 从请求头读取 `traceId`
- 放入本服务当前线程的 `MDC`
- 请求结束后清理 `MDC`

这一步的作用是：

- 让当前服务内部的日志自动带上 `traceId=...`

### 3.4 Feign 下游透传

新增：

- [FeignTraceConfiguration.java](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-server/src/main/java/com/sky/config/FeignTraceConfiguration.java)

它负责：

- 在 `sky-server` 调用下游服务时
- 从当前线程 `MDC` 中读取 `traceId`
- 主动塞入 Feign 请求头 `X-Trace-Id`

这是 Day19 真正的关键补丁，因为它决定了：

- `traceId` 不是只停在 gateway 和 `sky-server`
- 而是真的能继续透传到 `product-service`

### 3.5 日志格式补齐

修改：

- [sky-gateway application.yml](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-gateway/src/main/resources/application.yml)
- [sky-server application.yml](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-server/src/main/resources/application.yml)
- [product-service application.yml](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/product-service/src/main/resources/application.yml)

这一步的目标是：

- 让日志格式里显式出现 `traceId=...`

否则即便 `MDC` 里已经有值，日志里也看不出来。

---

## 4. Gateway 入口这一层到底干了什么

核心文件：

- [TraceIdGlobalFilter.java](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-gateway/src/main/java/com/sky/gateway/filter/TraceIdGlobalFilter.java)

这层的动作可以拆成 4 步。

### 4.1 先看请求里有没有现成的 `traceId`

代码先读：

- `exchange.getRequest().getHeaders().getFirst("X-Trace-Id")`

这说明 Day19 不是无脑覆盖外部传进来的标识，而是：

- 有就沿用
- 没有才自己生成

这样做的好处是：

- 如果外部调用方自己已经生成了请求标识，就不会被网关改掉

### 4.2 没有就生成新的 `traceId`

这里最终调用的是：

- `UUID.randomUUID().toString().replace("-", "")`

也就是说，gateway 是 Day19 的统一入口兜底层：

- 前端没传也没关系
- gateway 会保证每次请求至少有一个可用的 `traceId`

### 4.3 继续往后传，并回写给调用方

它做了两件事：

- 改写转发请求头
- 给响应头也带上 `X-Trace-Id`

这也是为什么你后来在：

- `curl -i`
- 浏览器 Network

里都能看到同一个 `X-Trace-Id`

### 4.4 在 gateway 自己的日志里打点

入口层还做了：

- 请求开始日志
- 请求结束日志

这样后续查同一个 `traceId` 时，gateway 不再只是转发黑盒，而是：

- 也能参与整条链的检索

---

## 5. `MDC` 在 Day19 里到底是什么

这是 Day19 最容易反复混淆，但又必须讲透的点。

可以先把 `MDC` 理解成：

- **当前线程随身带着的一小份日志上下文**

你往里面放一个：

- `traceId`

只要日志格式里配置了 `%X{traceId}` 一类占位符，当前线程后续打印日志时，就会自动带上：

- `traceId=xxxx`

所以 `MDC` 的职责不是：

- 跨服务传值

而是：

- **让当前服务内部的日志自动带值**

这一步非常重要，因为它让 Day19 不需要在每一行业务日志里手动写：

- `log.info("traceId={}, ...", traceId)`

而是只要请求进来时放一次，后面整条链路的日志都能自动带。

---

## 6. 为什么 `MDC` 不会自己跨服务同步

这一点如果不站住，Day19 后面一定会混。

`MDC` 只存在于：

- 当前 JVM
- 当前服务
- 当前线程

它不会自己穿过网络，自动跑到下游服务里。

所以 Day19 里必须再补一刀：

- 在 Feign 发下游请求前
- 从本地 `MDC` 里把 `traceId` 取出来
- 手动写入请求头

一句话记忆：

- **请求头负责跨服务传值**
- **`MDC` 负责服务内日志自动带值**

---

## 7. Feign 在 Day19 里为什么这么关键

### 7.1 Feign 不是独立服务

这里必须先把一个特别容易错的理解纠正过来：

- `Feign` 不是链路中独立存在的一层服务

更准确地说是：

- `sky-server` **借助 `Feign`** 去调用 `product-service`

所以：

- `Feign` 属于请求发起方
- 不是网络里一个独立的中间站

### 7.2 为什么请求发起方需要 Feign，而响应返回方不需要

因为：

- 发起请求的一侧，要解决：
  - 请求打到哪
  - 参数怎么带
  - 请求头怎么带
  - 响应回来后怎么解析

这正是 `Feign` 在帮调用方做的事。

但 `product-service` 返回结果时，不是在“再发一次新请求”，而是在：

- 沿着同一条 HTTP 连接回包

所以返回响应的一侧不需要 Feign。

这也是今天特别值得记住的一句话：

- **Feign 只出现在请求发起方；响应返回方只是复用原连接回包。**

### 7.3 Day19 为什么刚好要在 Feign 这一层补 `traceId`

因为跨服务调用恰好就发生在：

- `sky-server` 借助 Feign 调 `product-service`

如果这里不补：

- 从 gateway 带进来的 `traceId`
- 在 `sky-server` 这里就会断掉

所以 `FeignTraceConfiguration` 做的事，本质上就是：

- 把 `sky-server` 当前线程里已有的 `traceId`
- 主动再交给下一跳服务

---

## 8. `traceId` 为什么不会在 `sky-server -> product-service` 这段丢掉

这也是 Day19 最核心的一条执行主线。

完整接力顺序是：

1. `gateway` 从请求头读取或生成 `traceId`
2. `gateway` 把它写入转发请求头
3. `sky-server` 的 Filter 从请求头取出 `traceId`
4. `sky-server` 把它放入自己的 `MDC`
5. `sky-server` 里业务代码调用 `ProductClient`
6. Feign 的 `RequestInterceptor` 从 `MDC` 读取 `traceId`
7. Feign 把它写入下游请求头
8. `product-service` 的 Filter 从请求头再取出 `traceId`
9. `product-service` 再放入自己的 `MDC`
10. 下游日志继续打印同一个 `traceId`

所以真正准确的说法不是：

- `traceId` 自动传播了

而是：

- **`traceId` 在“请求头”和“各服务自己的 MDC”之间不断接力传播。**

你可以把这条链压成一句话：

- **`gateway请求头 -> sky-server的MDC -> Feign请求头 -> product-service的MDC`**

---

## 9. Day19 的完整执行链路到底怎么走

我们用一笔真实用户侧加购来讲。

### 9.1 入口阶段

用户侧请求先进入：

- `nginx`
- `gateway`

这时 gateway 会：

- 读取或生成 `traceId`
- 写回请求头 / 响应头
- 打 gateway 开始日志

### 9.2 主业务服务阶段

请求进入 `sky-server` 后：

- `TraceIdFilter` 从请求头取到 `traceId`
- `MDC.put("traceId", traceId)`
- `sky-server` 内部日志自动带上同一个 `traceId`

### 9.3 下游跨服务调用阶段

如果业务逻辑需要查商品信息，例如：

- 购物车加购时查菜品名称、价格、图片

那么 `sky-server` 会调用：

- `ProductClient`

这时 Feign 会：

- 先从 `MDC` 里拿当前 `traceId`
- 再把它写进下游请求头
- 然后向 `product-service` 发起 HTTP 请求

### 9.4 下游服务阶段

`product-service` 收到请求后：

- 自己的 `TraceIdFilter` 再从请求头里拿 `traceId`
- 再放进自己的 `MDC`
- 所以下游日志里也会出现同一个 `traceId`

然后它再正常执行：

- Spring MVC 接口参数绑定
- Service 调用
- MyBatis 查库
- 返回商品信息

### 9.5 响应返回阶段

响应再沿着原连接返回给调用方。

这里不需要再有“响应侧 Feign”，因为：

- 返回响应不是重新发起一个新请求
- 只是沿原 HTTP 连接回包

---

## 10. Day19 为什么不能只拿 `curl` 就算验证完成

这也是这次特别值钱的一次认知修正。

`curl` 的价值在于：

- 不依赖前端页面
- 可以快速验证后端主链
- 方便精确控制请求头和请求体

但 `curl` 只能证明：

- 后端链路设计本身成立

它不能替代：

- 浏览器真实页面验证
- 小程序真实入口验证

因为真实入口还可能额外暴露出：

- 请求是否真的经过 gateway
- 页面侧是否能拿到响应头
- 前端配置是否绕过你设计的链路

这也是为什么 Day19 最后必须补：

- 浏览器管理端验证
- 小程序用户侧验证

---

## 11. 这次真实入口验证，额外暴露出了什么问题

这次最值钱的一次补充发现是：

- 小程序原来的 `baseUrl` 直连 `8080`

这意味着：

- 业务本身虽然能跑
- 但它会绕过 `gateway`

如果不修这个问题，就会导致：

- 你以为自己验证了 Day19 的完整链路
- 实际上入口层 `traceId` 逻辑根本没被经过

所以这次把：

- [miniProgram/utils/env.js](file:///D:/ProgramFiles/CodeProjects/PeakSend/miniProgram/project-rjwm-weixin-uniapp-develop-wsy/utils/env.js)

统一改成：

- `http://localhost:8081`

它不只是“为了方便联调”，而是：

- **让真实用户入口和 Day19 的网关设计重新对齐。**

---

## 12. Day19 这次到底验证到了什么程度

### 12.1 命令行验证

已经验证通过：

- 请求头里的 `X-Trace-Id` 能进入链路
- 响应头能回写同一个 `traceId`
- `gateway` 与 `sky-server` 日志能串起来
- 购物车跨服务调用时，`product-service` 也能拿到同一个 `traceId`

### 12.2 真实浏览器页面验证

已验证：

- 浏览器点击“菜品管理”
- 响应头出现 `x-trace-id`
- `gateway` 和 `sky-server` 出现同一个 `traceId`

这证明：

- 真实管理端页面入口下
- `nginx -> gateway -> sky-server`
- 链路串联成立

### 12.3 真实小程序跨服务验证

已验证：

- 小程序真实加购“王老吉”
- `sky-server` 收到加购请求
- `product-service` 收到 `GET /rpc/products/dishes/46`
- 两边日志是同一个 `traceId`

这证明：

- 真实用户入口下
- `小程序 -> nginx -> gateway -> sky-server -> product-service`
- 也已经被同一个 `traceId` 串起来

---

## 13. Day19 这次最值得记住的高价值点

### 13.1 `traceId` 的价值不在于“多了个字段”

它真正的价值在于：

- 把原本散落在多个服务里的日志
- 变成一条能按单次请求检索的链

### 13.2 `MDC` 不是跨服务同步工具

它只负责：

- 本服务内日志自动带值

跨服务传播还得靠：

- 请求头继续接力

### 13.3 `Feign` 不是独立中间层

它只是：

- 调用方内部的远程调用工具

所以请求发起方需要 Feign，响应返回方不需要。

### 13.4 真实入口验证不能省

只做 `curl` 不够，因为：

- 真实入口有可能绕过 gateway
- 真实页面还会暴露出响应头、前端配置、实际路由等问题

### 13.5 验证链路是否成立，不能只看日志有没有

这次就出现过：

- 日志里一开始搜不到下游 `traceId`

如果只看现象，很容易误判成：

- 透传失败

但真正原因是：

- 业务根本没走到那条跨服务分支

所以验证时必须同时确认：

- 业务是否真的命中了对应链路

---

## 14. Day19 当前最准确的阶段结论

到 Day19 当前这一步，项目已经不只是：

- 多服务能跑

而是进一步具备了：

- 多服务日志可串联
- 请求链可检索
- 问题定位有统一抓手
- 真实浏览器 / 小程序入口都已完成验证

所以 Day19 最准确的一句话总结是：

- **在完成最小微服务拆分之后，项目进一步补齐了 `traceId` 全链路透传能力，让 `gateway -> sky-server -> product-service` 的日志能够按同一个请求标识串起来，真正把系统从“能跑”推进到了“可查、可定位”。**
