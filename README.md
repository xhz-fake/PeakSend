# PeakSend

PeakSend（峰送商配平台）是一个围绕本地生活配送场景构建的全栈商配平台项目。项目覆盖管理端与用户端两条业务主线，既包含商品、购物车、下单、订单流转、工作台、统计报表等核心业务，也在此基础上继续补强了容器化交付、Redis 缓存工程化、RocketMQ 异步链路、最小微服务拆分与 `traceId` 全链路追踪等工程能力。

## 项目概览

这个项目的定位不是只把课程业务跑通，而是把一个业务项目逐步推进成更适合后端岗位展示的工程化项目。

当前已经形成的核心能力包括：

- 管理端与用户端核心业务闭环
- Docker Compose 一键拉起本地运行环境
- Redis 缓存工程化与热点查询优化
- RocketMQ 异步落库与延迟关单
- `Nacos + Gateway + OpenFeign` 最小微服务主链
- `traceId` 全链路透传与多服务日志串联

## 技术栈

### 后端

- Java 8
- Spring Boot 2.7
- Spring MVC
- MyBatis
- MySQL 8
- Redis 6
- RocketMQ 5
- Spring Cloud Gateway
- Spring Cloud Alibaba Nacos
- OpenFeign

### 工程与运行环境

- Maven 多模块工程
- Docker / Docker Compose
- Nginx

### 前端与用户入口

- 管理端静态资源页面
- uni-app 小程序前端（本地开发统一走 `http://localhost:8081` 网关入口）

## 项目亮点

1. **缓存优化**  
   基于 `Redis + Spring Cache` 对分类与报表查询做缓存工程化改造，营业额统计查询耗时由约 `76.5ms` 优化至约 `15.2ms`。

2. **异步削峰与时效控制**  
   基于 `RocketMQ` 将限量套餐链路改造为“Redis 抢资格 + MQ 异步落库”，异步落库时延约 `20ms`；同时通过延迟消息实现超时订单自动关闭，关单触发稳定在约 `10s`。

3. **最小微服务拆分**  
   基于 `Nacos + Spring Cloud Gateway + OpenFeign` 拆分独立 `product-service`，打通 `nginx -> gateway -> sky-server -> product-service` 跨服务主链。

4. **链路追踪与可观测性**  
   基于 `Gateway Filter + MDC + Feign RequestInterceptor` 实现 `traceId` 全链路透传，补测 `30` 次请求中主链调用成功率与日志串联率均为 `100%`。

## 仓库结构

```text
PeakSend
├─ peaksend-backend/                 # 后端 Maven 多模块工程
│  ├─ sky-common/                    # 常量、工具类、公共组件
│  ├─ sky-pojo/                      # DTO / Entity / VO
│  ├─ sky-server/                    # 主业务服务
│  ├─ sky-gateway/                   # 网关服务
│  └─ product-service/               # 商品查询服务
├─ miniProgram/                      # uni-app 小程序前端
├─ FrontRunEnv/                      # 管理端静态资源与运行环境
├─ docker/                           # Nginx / MySQL / Redis / RocketMQ 相关配置
├─ docs/                             # 过程文档、详细逻辑文档、规划与话术沉淀
├─ 数据库/                            # 初始化 SQL 与阶段增量 SQL
├─ 产品原型/                          # 原型与页面流转资料
├─ 项目接口/                          # 静态接口资料
└─ docker-compose.yml                # 本地统一编排入口
```

## 快速开始

如果你想在自己的机器上复现这个项目，优先阅读：

- [部署文档](./docs/部署文档.md)

它会告诉你：

- 本地需要准备什么
- 哪些文件负责配置
- 如何启动容器与服务
- 如何验证管理端、后端、网关、小程序入口链路
- 遇到常见问题先查哪里

## 文档索引

### 总览与规划

- [20天陪跑总地图](./docs/20天陪跑总地图.md)
- [升级版项目行动规划（Day13 起）](./docs/PeakSend升级版项目行动规划-Day13起.md)

### 启动与部署

- [部署文档](./docs/部署文档.md)
- [Day13-Docker化启动说明](./docs/Day13-Docker化启动说明.md)

### 关键阶段文档

- [Day18-变更细节及详细逻辑链路说明](./docs/Day18-变更细节及详细逻辑链路说明.md)
- [Day19-变更细节及详细逻辑链路说明](./docs/Day19-变更细节及详细逻辑链路说明.md)
- [Day20-升级版项目总收口清单](./docs/Day20-升级版项目总收口清单.md)

### 量化留档

- [upgrade-metrics/README](./docs/upgrade-metrics/README.md)
- [trace_observability](./docs/upgrade-metrics/trace_observability.md)
- [service_split_feign](./docs/upgrade-metrics/service_split_feign.md)
- [flash_sale_async](./docs/upgrade-metrics/flash_sale_async.md)
- [order_delay_close](./docs/upgrade-metrics/order_delay_close.md)

## 当前阶段说明

当前仓库的重点已经不再是单纯补课程功能，而是围绕以下主线做工程化升级：

- Day13：Docker 化环境统一拉起
- Day14：Redis 缓存工程化
- Day15：Redis 高并发资源竞争
- Day16：RocketMQ 异步链与延迟关单
- Day17：Nacos 注册发现与 Gateway 统一入口
- Day18：最小微服务拆分
- Day19：`traceId` 全链路透传与可观测性
- Day20：量化、部署、文档与最终表达收口

## 说明

- 这份 README 只聚焦项目本身、项目价值与复现路径
- 更细的变更细节、源码逻辑、验证过程和阶段结论统一维护在 `docs/`
