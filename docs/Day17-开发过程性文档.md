# Day17-开发过程性文档

## 1. 今天这一阶段到底在做什么

Day17 的问题，已经不是继续强化 Day16 的交易链。

Day16 解决的是：

- 抢购异步落库
- 延迟关单
- MQ 幂等与最终一致性

但如果项目继续停在 Day16，系统形态仍然还是：

- `nginx -> backend`

这意味着：

- 外部入口和内部业务服务还没有分层
- 服务地址仍然容易被理解成“写死 backend 就行”
- 后续一旦继续做微服务拆分，入口、寻址和治理能力都要重新补

所以 Day17 真正要解决的是：

- 先不急着拆业务
- 先把最小微服务基础设施骨架搭起来
- 让项目第一次具备：
  - 服务注册发现
  - 网关统一入口
  - Docker 多服务稳定编排

一句话概括：

- Day16 解决“单体交易链怎么更像真实系统”
- Day17 解决“系统运行形态怎么从单体直连升级成最小微服务骨架”

---

## 2. 今天最终做成了什么

### 2.1 后端工程正式接入 Spring Cloud Alibaba 最小骨架

已完成：

- 父工程补齐 `Spring Cloud` / `Spring Cloud Alibaba` 依赖管理
- 新增独立网关模块：
  - [sky-gateway](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-gateway)
- `sky-server` 启动类开启服务发现：
  - [SkyApplication.java](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-server/src/main/java/com/sky/SkyApplication.java)
- `sky-gateway` 补齐最小路由配置：
  - `/api/**`
  - `/user/**`
  - `/ws/**`

### 2.2 Docker 运行链路从“nginx 直连 backend”升级为“nginx -> gateway -> backend”

已完成：

- `docker-compose.yml` 新增 `nacos` 服务
- `docker-compose.yml` 新增 `gateway` 服务
- `nginx` 上游目标从 `backend` 改成 `gateway`
- `backend` 与 `gateway` 都通过环境变量接入 `Nacos`
- `backend` 继续通过服务名连接：
  - `mysql`
  - `redis`
  - `rocketmq`
  - `peaksend-nacos`

### 2.3 Day17 当前真实跑通的闭环

已完成：

- `mvn -pl sky-server,sky-gateway -am package -DskipTests` 编译通过
- `Nacos` 服务列表里真实出现：
  - `sky-server`
  - `sky-gateway`
- 外部访问：
  - `http://localhost:8081/api/flashSaleSetmealActivity/list`
- 返回结果不是 `502` 或 `404`
- 而是正确进入后端鉴权层，返回 `401`

这一步的意义是：

- `nginx -> gateway -> backend` 这条链已经真的贯通
- Day17 当前阶段的“最小微服务骨架”不是只停在配置文件层面

---

## 3. 今天最关键的工程决策

### 3.1 不急着拆业务，先搭微服务底座

Day17 最容易犯的错误，是一上来就把：

- 订单
- 商品
- 用户

拆成多个服务。

但如果注册发现、网关入口、容器编排这些基础设施还没站住，后面会出现两个问题：

- 请求怎么进来不清楚
- 服务之间怎么找彼此不清楚

所以 Day17 明确控制范围：

- 不追求“今天就拆几个服务”
- 只追求先把微服务运行底座搭好

### 3.2 Nginx 和 Gateway 明确分层

Day17 之后：

- `Nginx` 继续做系统最外层入口
- `Gateway` 负责 Java 微服务体系内部统一入口

这样分层后的价值是：

- `Nginx` 继续负责静态资源和最外层代理
- `Gateway` 负责路径治理、服务名路由和后续横切能力沉淀

### 3.3 容器内部一律按服务名通信，不再按固定 IP 思考

Day17 一个很关键的认知转折是：

- 容器里服务地址不应该默认理解为固定 IP

因为容器重建后：

- IP 可能会变

所以 Day17 全部按下面这套规则走：

- Compose 里用服务名组织依赖
- Spring 配置里用环境变量 + 服务名寻址
- Gateway 用 `lb://sky-server` 按服务名路由

### 3.4 当前只落“注册发现 + 网关”，不把配置中心提前写成已完成

今天文档复查时发现一个很重要的同步问题：

- 有些全局文档把 Day17 提前写成了“注册中心 + 配置中心 + Gateway”

但当前真实代码状态是：

- `Nacos` 注册发现已落地
- `Gateway` 已落地
- **配置中心还没有真正接进来**

所以 Day17 当前正确口径必须是：

- 最小微服务骨架
- Nacos 注册发现
- Gateway 统一入口

而不是把后续阶段的配置中心能力提前写成已完成。

---

## 4. 今天遇到的关键问题与处理

### 4.1 Maven 本地仓库路径不稳定，先把构建环境固化到项目内

问题现象：

- 本机默认 Maven 仓库路径存在权限/路径问题
- 直接编译时依赖下载不稳定

处理方式：

- 新增项目级：
  - [peaksend-backend/.mvn/settings.xml](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/.mvn/settings.xml)
  - [peaksend-backend/.mvn/maven.config](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/.mvn/maven.config)

结果：

- Day17 的 Maven 构建不再依赖本机全局 Maven 仓库配置
- 编译链路可以在项目内稳定复现

### 4.2 Nacos 不能只看“容器启动了”，还得看“服务真的可用了”

问题现象：

- `nacos` 容器虽然被拉起
- 但 `backend` / `gateway` 太早去连时，仍可能撞上连接未就绪

处理方式：

- 在 `docker-compose.yml` 中为 `nacos` 增加 `healthcheck`
- `backend` 和 `gateway` 对 `nacos` 的依赖改成：
  - `condition: service_healthy`

结果：

- Day17 运行链不再只靠“启动顺序”碰运气
- 形成了“容器启动完成”和“服务真正可用”两层区分

### 4.3 第一次看到 `401`，不能误判成链路失败

问题现象：

- 外部访问 `http://localhost:8081/api/flashSaleSetmealActivity/list`
- 返回的是 `401`

容易产生的误判是：

- 以为 Gateway 没转发成功

但这次真正的工程结论是：

- 如果链路没通，更常见的是：
  - `502`
  - `404`
  - 连接失败
- 这次返回 `401`，反而说明请求已经进入后端鉴权层

结果：

- `nginx -> gateway -> backend` 真实贯通

### 4.4 Day17 再次证明：服务不是代码文件，服务是跑起来的进程

今天复盘里最值得单独记住的一个理解点是：

- `SkyApplication.java` 不是服务本身
- `sky-server` 跑起来之后，监听端口、对外提供能力的那个运行实体，才是服务

这个认知之所以重要，是因为 Day17 开始之后很多讨论都不再是：

- “某个类在不在”

而是：

- “某个服务有没有启动”
- “它注册到了哪里”
- “别的服务怎么找到它”

---

## 5. Day17 当前阶段的代表性证据与轻量量化

Day17 按路线规划，本来就不做最终业务压测，而是拿轻量工程指标。

当前已拿到的代表性证据：

- Nacos 成功注册服务数：`2`
  - `sky-server`
  - `sky-gateway`
- Gateway 当前统一承接的路径类别：`3`
  - `/api/**`
  - `/user/**`
  - `/ws/**`
- 外部链路贯通验证：
  - 访问 `/api/flashSaleSetmealActivity/list`
  - 当前代表性结果：`401`
- Gateway 路由目标硬编码 IP：`0` 处
  - 当前全部通过服务名寻址

这组数字的意义不是“性能压测”，而是：

- 证明 Day17 的微服务基础设施不是空壳
- 证明入口、路由、注册发现已经有了可复述证据

---

## 6. 今天最值得沉淀的高价值点

### 6.1 Nacos 不是“上阿里云”，而是微服务注册发现基础设施

今天最容易混淆的点就是：

- `Spring Cloud Alibaba`

并不等于：

- 先去阿里云官网开一个云服务

Day17 当前做的，其实是：

- 把 Nacos 这个本地可运行的注册发现能力接进来

### 6.2 为什么先上 Nacos + Gateway，而不是直接拆 order/product

这个点很适合面试讲：

- 微服务不是把包拆散就完了
- 真正的第一步是先把服务注册发现和统一入口搭起来

否则后续拆出来的服务：

- 没有稳定入口
- 没有统一路由
- 没有服务寻址基础

### 6.3 Docker 里的 IP 漂移，是 Nacos 这一步真正有现实意义的原因之一

Day16 我们已经见过：

- nginx 握着旧 backend IP，导致 `502`

Day17 再往前走一步后，这个经验就更值钱了：

- 容器网络里的地址不是你手工永久维护的
- 服务之间不能继续靠写死 IP 思考
- 服务名发现这件事不是“高级感”，而是现实需要

### 6.4 为什么现在不急着加 Elasticsearch / Kafka / K8s

Day17 这一天也顺带明确了一个非常重要的选型原则：

- 项目不是技术展览馆
- 技术必须围绕当前问题来加

当前最需要解决的是：

- 注册发现
- 网关统一入口
- 微服务基础设施起步

而不是：

- 全文检索
- 海量事件流平台
- 集群级容器编排

所以 Day17 的技术选型仍然是问题驱动，而不是名词堆叠。

---

## 7. 今天涉及到的核心文件

| 类型 | 文件 | 作用 |
|---|---|---|
| 启动编排 | [docker-compose.yml](file:///D:/ProgramFiles/CodeProjects/PeakSend/docker-compose.yml) | 统一编排 `mysql / redis / rocketmq / nacos / backend / gateway / nginx` |
| 外层入口 | [docker/nginx/nginx.conf](file:///D:/ProgramFiles/CodeProjects/PeakSend/docker/nginx/nginx.conf) | `Nginx` 外层入口，统一把动态请求转给 `gateway` |
| 网关路由 | [sky-gateway/application.yml](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-gateway/src/main/resources/application.yml) | Gateway 路由规则、Nacos 注册发现配置 |
| 后端运行态配置 | [sky-server/application-docker.yml](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-server/src/main/resources/application-docker.yml) | Docker 环境下的 MySQL / Redis / RocketMQ / Nacos 寻址 |
| 后端启动入口 | [SkyApplication.java](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-server/src/main/java/com/sky/SkyApplication.java) | 开启 `@EnableDiscoveryClient` |
| 构建环境 | [peaksend-backend/.mvn/settings.xml](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/.mvn/settings.xml) | 固化 Day17 Maven 构建环境 |

---

## 8. Day17 当前阶段结论

Day17 当前已经完成的，不是“业务拆分”，而是：

- 把项目从 `nginx -> backend` 的单体直连形态
- 升级成了 `nginx -> gateway -> backend` 的最小微服务基础设施形态

并且当前已有真实证据证明：

- `Nacos` 注册发现已生效
- `Gateway` 路由已生效
- Docker 多服务编排已进入稳定可复现状态

当前这一天最正确的定位不是：

- “微服务全部做完了”

而是：

- “微服务基础设施正式进场，并且最小骨架已经可跑、可讲、可验证”
