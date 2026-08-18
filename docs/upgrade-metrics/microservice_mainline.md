# 微服务基础设施量化留档

## 1. 这组数字对应什么能力

- `Nacos` 注册发现
- `Gateway` 统一入口
- `nginx -> gateway -> backend` 的最小微服务基础设施链路

## 2. 版本边界

- 当前稳定版本：`Day17`
- 对应链路：
  - `docker-compose.yml`
  - `docker/nginx/nginx.conf`
  - `sky-gateway/application.yml`
  - `sky-server/application-docker.yml`
  - `SkyApplication.java`
- 当前阶段定位：
  - 这是“最小微服务基础设施骨架”
  - 还不是 Day18 之后的跨服务业务主链

## 3. 测量方法

- 先完成 `sky-server` 与 `sky-gateway` 的构建和 Docker 编排
- 再打开 `Nacos` 服务列表，记录当前稳定注册的服务数与服务名
- 再核对 Gateway 当前承接的路径类别：
  - `/api/**`
  - `/user/**`
  - `/ws/**`
- 最后从浏览器侧访问：
  - `http://localhost:8081/api/flashSaleSetmealActivity/list`
- 如果返回结果不是 `502/404`，而是进入 backend 鉴权层，则说明：
  - `nginx -> gateway -> backend` 已真实贯通

## 4. 原始数据

当前代表性结果：

- Nacos 服务列表中已注册：
  - `sky-server`
  - `sky-gateway`
- 成功注册的服务数：`2`
- Gateway 当前统一承接的路径类别：`3`
  - `/api/**`
  - `/user/**`
  - `/ws/**`
- 外部链路贯通验证：
  - 访问 `http://localhost:8081/api/flashSaleSetmealActivity/list`
  - 当前代表性结果：返回 `401`
  - 说明请求已穿过：
    - `nginx`
    - `gateway`
    - 并进入 backend 鉴权层
- 路由目标硬编码 IP：
  - 当前 `0` 处
  - 全部改为通过服务名与环境变量组织

## 5. 最终结论

- 当前 Day17 稳定版本已经把项目从 `nginx -> backend` 的单体直连形态，推进到了 `nginx -> gateway -> backend` 的最小微服务基础设施形态
- 当前已有 `2` 个核心服务稳定注册到 `Nacos`
- `Gateway` 已经统一承接 `3` 类入口路径，并能把外部请求稳定转入 backend 鉴权层
- 这组轻量工程指标足够证明 Day17 的注册发现、网关入口和 Docker 编排不是空壳，后续 Day18 可在这个基础上继续补跨服务业务主链量化

## 6. 可写进简历/话术库的句子

- 基于 `Nacos + Spring Cloud Gateway` 搭建最小微服务基础设施骨架，当前已有 `sky-server` 与 `sky-gateway` 两个核心服务稳定注册到注册中心，并统一收敛管理端、用户端和 WebSocket 三类入口流量，为后续服务拆分和治理打下基础。
