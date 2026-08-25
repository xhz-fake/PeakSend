# Day13-Docker化启动说明

## 1. 目标

把 `PeakSend` 从“本机手工安装依赖后启动”的方式，升级为“可通过 `docker compose` 统一拉起 MySQL、Redis、Nacos、RocketMQ、后端服务、网关、Nginx”的最小可运行版本。

## 2. 当前容器化范围

当前覆盖以下 3 层服务：

### 2.1 基础数据层

- MySQL
- Redis

### 2.2 基础设施层

- Nacos
- RocketMQ NameServer
- RocketMQ Broker

### 2.3 应用接入层

- `sky-server`（backend）
- `product-service`
- `sky-gateway`
- Nginx

## 3. 关键文件

- [docker-compose.yml](file:///D:/ProgramFiles/CodeProjects/PeakSend/docker-compose.yml)
- [peaksend-backend/Dockerfile](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/Dockerfile)
- [peaksend-backend/Dockerfile.product](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/Dockerfile.product)
- [peaksend-backend/Dockerfile.gateway](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/Dockerfile.gateway)
- [application-docker.yml](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-server/src/main/resources/application-docker.yml)
- [docker/nginx/nginx.conf](file:///D:/ProgramFiles/CodeProjects/PeakSend/docker/nginx/nginx.conf)

## 4. 启动前准备

确保本机已经安装：

- Docker Desktop（或其他可用 Docker 环境）
- Docker Compose

## 5. 启动命令

在项目根目录执行：

```powershell
docker compose up -d --build
```

## 6. 预期端口

- MySQL：宿主机 `3307` -> 容器 `3306`
- Redis：`6379`
- Nacos：`8848`
- RocketMQ NameServer：`9876`
- RocketMQ Broker：`10911`、`10909`
- `sky-server`：`8080`
- `sky-gateway`：`8082`
- `product-service`：`8083`
- Nginx：`8081`

## 7. 基础验证项

### 7.1 查看容器状态

```powershell
docker compose ps
```

### 7.2 查看后端日志

```powershell
docker compose logs backend
```

### 7.3 查看 gateway 日志

```powershell
docker compose logs gateway
```

### 7.4 查看 Nginx 日志

```powershell
docker compose logs nginx
```

### 7.5 查看 product-service 日志

```powershell
docker compose logs product-service
```

### 7.6 验证数据库初始化

确认 [sky.sql](file:///D:/ProgramFiles/CodeProjects/PeakSend/%E6%95%B0%E6%8D%AE%E5%BA%93/sky.sql) 已被自动挂载到 MySQL 初始化目录。

### 7.7 验证页面和接口

- 打开管理端：`http://localhost:8081`
- 验证 Nacos：`http://localhost:8848/nacos`
- 验证 backend 接口文档：`http://localhost:8080/doc.html`
- 验证 product-service 接口文档：`http://localhost:8083/doc.html`

### 7.8 验证完整入口链路

当前真实入口建议统一走：

- 管理端 / 用户端入口：`http://localhost:8081`
- 小程序本地入口：`miniProgram/utils/env.js` 中的 `http://localhost:8081`

这样可以保证请求经过：

- `nginx -> gateway -> backend/product-service`

而不会绕过 gateway。

## 8. 当前边界

- 当前仍使用 `mock` 支付模式
- 当前重点是“统一拉起与工程底座”，不是生产环境级别部署
- 当前前端仍基于已有静态资源目录，不涉及前端工程源码重构
- 当前 compose 已覆盖微服务阶段核心链路，但仍主要服务于本地开发、联调、验证与求职展示

## 9. 后续可继续补强的方向

- MySQL / Redis 健康检查
- `.env` 环境变量文件
- 上传目录持久化规范
- 统一架构图、服务清单、启动清单和常见排障手册
