# Day13-Docker化启动说明

## 1. 目标

把 `PeakSend` 从“本机手工安装依赖后启动”的方式，升级为“可通过 `docker-compose` 统一拉起 MySQL、Redis、后端服务、Nginx”的最小可运行版本。

## 2. 当前容器化范围

本轮覆盖以下 4 类核心服务：

- MySQL
- Redis
- PeakSend Backend
- Nginx

## 3. 关键文件

- [docker-compose.yml](file:///D:/ProgramFiles/CodeProjects/PeakSend/docker-compose.yml)
- [peaksend-backend/Dockerfile](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/Dockerfile)
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
- Backend：`8080`
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

### 7.3 查看 Nginx 日志

```powershell
docker compose logs nginx
```

### 7.4 验证数据库初始化

确认 [sky.sql](file:///D:/ProgramFiles/CodeProjects/PeakSend/%E6%95%B0%E6%8D%AE%E5%BA%93/sky.sql) 已被自动挂载到 MySQL 初始化目录。

### 7.5 验证页面和接口

- 打开管理端：`http://localhost:8081`
- 验证后端接口：`http://localhost:8080/doc.html`

## 8. 当前边界

- 当前仍使用 `mock` 支付模式
- 当前容器化重点是“统一拉起与工程底座”，不是生产环境级别部署
- 当前前端仍基于已有静态资源目录，不涉及前端工程源码重构

## 9. 后续可继续补强的方向

- MySQL / Redis 健康检查
- `.env` 环境变量文件
- 上传目录持久化规范
- 网关与微服务接入后的 compose 扩展
