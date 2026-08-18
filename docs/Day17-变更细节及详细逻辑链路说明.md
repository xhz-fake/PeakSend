# Day17-变更细节及详细逻辑链路说明

## 1. Day17 这一阶段真正要解决的是什么

Day17 的核心问题，不是继续在单体里叠业务功能。

Day16 之后，项目已经具备：

- Redis 高并发抢资格
- RocketMQ 异步落库
- RocketMQ 延迟关单

但系统运行形态仍然偏单体：

- 外部请求基本还是：
  - `浏览器 -> nginx -> backend`

这意味着后续如果继续做微服务演进，会马上遇到三个问题：

1. 服务怎么统一进来
2. 服务之间怎么动态找彼此
3. 容器重建后地址变化时，谁来兜住寻址问题

所以 Day17 的真实目标是：

- 先把最小微服务基础设施搭起来
- 让服务注册发现、网关统一入口和 Docker 多服务编排先成立

一句话概括：

- Day17 不是“拆业务”
- 而是“先把微服务运行底座搭起来”

---

## 2. 为什么 Day17 先上 Nacos + Gateway，而不是直接拆服务

这一天最关键的选型，不是“用哪个组件”，而是“先做哪一步”。

如果一上来就拆：

- `order-service`
- `product-service`

会遇到两个非常具体的问题：

### 2.1 请求入口会变得混乱

如果没有统一入口，外部调用关系会很快变成：

- 某些请求打订单服务
- 某些请求打商品服务
- 某些请求打通知服务

这会让：

- 路由管理
- 鉴权
- 跨域
- WebSocket 入口

都开始分散。

### 2.2 服务寻址会继续停留在“写死地址”思路

只要服务真正拆开，就必须面对：

- 某个服务在哪
- 它重启后地址变了怎么办
- 调用方如何不感知这种漂移

所以 Day17 的最优顺序不是：

- 先拆业务，再补基础设施

而是：

- 先补：
  - `Nacos`
  - `Gateway`
- 再在这个底座上继续拆业务

---

## 3. Day17 关键代码入口有哪些

这一天最重要的文件主要有 5 个：

1. [docker-compose.yml](file:///D:/ProgramFiles/CodeProjects/PeakSend/docker-compose.yml)
2. [docker/nginx/nginx.conf](file:///D:/ProgramFiles/CodeProjects/PeakSend/docker/nginx/nginx.conf)
3. [sky-gateway/application.yml](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-gateway/src/main/resources/application.yml)
4. [sky-server/application-docker.yml](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-server/src/main/resources/application-docker.yml)
5. [SkyApplication.java](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-server/src/main/java/com/sky/SkyApplication.java)

它们分别解决的是：

- `docker-compose.yml`
  - 服务怎么一起拉起
  - 谁依赖谁
  - 容器里谁找谁
- `nginx.conf`
  - 浏览器请求先交给谁
- `sky-gateway/application.yml`
  - Gateway 该把什么请求转给谁
- `application-docker.yml`
  - backend 在 Docker 里该去哪里找 MySQL / Redis / RocketMQ / Nacos
- `SkyApplication.java`
  - backend 是否开启注册发现能力

---

## 4. Docker 编排层到底做了什么

### 4.1 新增了两个核心运行服务

在 [docker-compose.yml](file:///D:/ProgramFiles/CodeProjects/PeakSend/docker-compose.yml) 中，Day17 最核心的新服务是：

- `nacos`
- `gateway`

这两个服务的意义分别是：

- `nacos`
  - 服务注册发现中心
- `gateway`
  - Java 微服务体系内部统一入口

### 4.2 backend 的依赖关系发生了变化

Day17 之前，backend 的核心依赖更多是：

- MySQL
- Redis
- RocketMQ

Day17 之后，又多了一层：

- `Nacos`

这意味着 backend 启动时不仅要连业务依赖，还要：

- 把自己注册到 Nacos

### 4.3 “容器启动了”不等于“服务真的可用了”

这是 Day17 很值钱的一个工程结论。

`docker-compose.yml` 里后来专门给 `nacos` 加上了：

- `healthcheck`

并让：

- `backend`
- `gateway`

都依赖：

- `nacos: service_healthy`

这样做是因为：

- `container started`
- 不等于
- `service ready`

这也是 Day17 比较像真实工程环境的一步。

---

## 5. Nginx 在 Day17 里为什么没有消失

### 5.1 Nginx 仍然是最外层入口

[docker/nginx/nginx.conf](file:///D:/ProgramFiles/CodeProjects/PeakSend/docker/nginx/nginx.conf) 里，`Nginx` 仍然负责两类事：

1. 返回前端静态页面
2. 把动态请求转发出去

所以 Day17 之后它没有消失，只是职责被重新收瘦了。

### 5.2 它不再直接代理 backend，而是先代理到 gateway

Day17 之前更接近：

- `nginx -> backend`

Day17 之后变成：

- `nginx -> gateway`

这一步的意义是：

- `Nginx` 继续当最外层门卫
- 真正的 Java 微服务内部路由逻辑收口到 `Gateway`

也就是说：

- `Nginx` 负责“接外面的流量”
- `Gateway` 负责“内部该转给谁”

---

## 6. Gateway 这一层到底做了哪些事情

核心文件：

- [sky-gateway/application.yml](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-gateway/src/main/resources/application.yml)

### 6.1 Gateway 自己也是一个服务

它的配置里有：

- `server.port = 8082`
- `spring.application.name = sky-gateway`

这说明：

- `Gateway` 不是某种抽象概念
- 它在当前项目里就是一个真实运行的 Spring Boot 服务

### 6.2 Gateway 通过 Nacos 做服务发现

最关键的配置是：

- `spring.cloud.nacos.discovery`

这说明：

- `Gateway` 自己会注册到 Nacos
- 它也会从 Nacos 查别的服务

### 6.3 Gateway 路由的是“服务名”，不是“固定 IP”

管理端路由里最关键的语句是：

- `uri: lb://sky-server`

这里的关键点不是语法，而是思想：

- `lb://sky-server`
- 表示按服务名找 `sky-server`
- 不再写死某个 IP 地址

这就是 Day17 服务发现真正落地的地方。

### 6.4 管理端路径不是硬改后端，而是在 Gateway 做兼容改写

Gateway 管理端路由当前做的是：

- 外部仍走 `/api/**`
- 内部转给 backend 前，改写成 `/admin/**`

具体做法是：

- `StripPrefix=1`
- `PrefixPath=/admin`

所以：

- 外部请求：
  - `/api/flashSaleSetmealActivity/list`
- 进入 backend 前会被改成：
  - `/admin/flashSaleSetmealActivity/list`

这一步的价值非常高，因为它说明：

- Day17 没有为了接 Gateway 去推翻原有 Controller 路径
- 而是在网关层做了兼容改写

---

## 7. backend 为什么“只加一点配置”就具备了注册发现能力

核心文件：

- [SkyApplication.java](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-server/src/main/java/com/sky/SkyApplication.java)
- [application-docker.yml](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-server/src/main/resources/application-docker.yml)

### 7.1 启动类打开了服务发现能力

`SkyApplication` 上新增了：

- `@EnableDiscoveryClient`

这说明 backend 不再只是普通 Spring Boot 服务，而是：

- 具备接入注册发现体系的能力

### 7.2 Docker 运行态配置告诉 backend 去哪个 Nacos 报到

`application-docker.yml` 里最关键的新增是：

- `spring.cloud.nacos.discovery.enabled`
- `spring.cloud.nacos.discovery.server-addr`

这说明 backend 在 Docker 环境下启动时，会去：

- `peaksend-nacos:8848`

注册自己。

### 7.3 backend 其他基础设施依赖也都切成了服务名寻址

Day17 这份配置不只是补了 Nacos。

它同时明确了 backend 连接下面这些依赖时，也都按服务名走：

- `mysql`
- `redis`
- `rocketmq-namesrv`
- `peaksend-nacos`

这一步看起来只是配置变化，实际上背后是整个系统运行心智在变：

- 从“默认本机 localhost”
- 变成“默认 Docker 网络中的服务名通信”

---

## 8. 一次真实请求在 Day17 里是怎么走完的

我们用这条请求做例子：

- `http://localhost:8081/api/flashSaleSetmealActivity/list`

完整链路是：

1. 浏览器把请求打到 `Nginx`
2. `Nginx` 识别到它命中 `/api/**`
3. `Nginx` 把请求转给 `gateway:8082`
4. `Gateway` 命中 `admin-route`
5. `Gateway` 把路径从 `/api/**` 改写成 `/admin/**`
6. `Gateway` 去 `Nacos` 查：
   - `sky-server` 在哪
7. `Nacos` 返回当前 `sky-server` 实例地址
8. `Gateway` 把请求转给 `sky-server`
9. `sky-server` 正常进入鉴权和业务处理逻辑

这条链最值得记的，不是“跳了几层”，而是：

- 外层入口和内部服务路由已经分层
- 服务发现已经从固定地址变成动态服务名寻址

---

## 9. 为什么 Day17 验证里拿到 `401` 反而是好证据

Day17 当前基础设施验证里，一个很典型的代表性结果是：

- 外部访问 `/api/flashSaleSetmealActivity/list`
- 返回 `401`

这个结果为什么值钱？

因为它证明的不是：

- “鉴权失败了”

而是：

- 如果链路没通，更可能看到：
  - `502`
  - `404`
  - 连接失败
- 现在返回 `401`
  - 说明请求已经真的进入了 backend 的鉴权层

所以 Day17 这一阶段看 `401`，不能机械理解成失败，而要理解成：

- **基础设施链路成功，业务鉴权正常拦截**

---

## 10. Day17 当前必须同步到文档口径的高价值点

### 10.1 当前只完成了 Nacos 注册发现，不要把配置中心提前写成已完成

这是今天文档复查发现的最重要同步问题。

当前真实状态是：

- `Nacos` 注册发现已落地
- `Gateway` 已落地
- **配置中心未落地**

所以 Day17 当前正确表达必须是：

- Spring Cloud Alibaba 最小微服务骨架
- Nacos 注册发现
- Gateway 网关统一入口

而不能写成：

- “注册中心 + 配置中心 + 网关已全部完成”

### 10.2 服务不等于类文件，服务是运行中的进程实例

这点在 Day17 很重要，因为后续所有关于：

- 服务注册
- 服务发现
- 多实例
- 容器重建

讨论的前提，都是你先能把“类”和“服务”分开理解。

### 10.3 Docker IP 漂移是这套基础设施设计的现实背景

Day16 我们已经见过：

- `nginx` 握着旧 backend IP 导致 `502`

Day17 则把这个经验进一步收束成一个更本质的工程认识：

- 容器环境下，服务寻址不能继续靠手记 IP
- 注册发现体系的价值，恰恰来自地址会漂移

### 10.4 为什么现在不急着上 Elasticsearch / Kafka / K8s

Day17 的设计边界也很适合面试讲：

- `Elasticsearch` 更偏全文检索与日志检索
- `Kafka` 更偏海量事件流
- `K8s` 更偏集群级容器编排

而 Day17 当前真正要解决的是：

- 服务注册发现
- 统一路由入口
- 最小微服务运行底座

所以当前不上这些组件，不是因为不会，而是因为：

- 还没到当前阶段的主矛盾

---

## 11. Day17 当前阶段结论

到 Day17 当前这一步，项目第一次真正具备了下面这层能力：

- 服务不是只能靠固定地址直连
- 外部流量不是只能直接打 backend
- Docker 编排不再只是把服务拉起来，而是开始考虑：
  - 依赖顺序
  - 健康检查
  - 注册发现

所以 Day17 不是一个“看起来没做业务”的空转日。

它真正完成的是：

- 把项目从“单体直连运行”
- 推进到了“最小微服务基础设施可运行”的阶段

这一步一旦站住，Day18 的服务拆分、Day19 的 traceId、Day20 的最终收口，才有了稳定的基础。
