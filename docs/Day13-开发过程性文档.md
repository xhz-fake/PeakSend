# Day13-开发过程性文档

> 用途：记录 Day13 容器化与一键启动阶段的真实推进过程、验证记录、量化数据与中间决策。
>
> 今天的主目标不是把所有工程化都做完，而是先把 `PeakSend` 从“本机手工拼环境”推进到“具备统一启动骨架和部署留痕”的第一步。

---

## 1. 今日任务概览

### 1.1 今日目标

- 启动 `Day13`：完成 `PeakSend` 的 Docker 化基础环境与一键启动骨架
- 明确当前项目运行依赖，并整理容器化落点
- 补齐 Day13 所需的过程性留痕文档，后续边做边更新

### 1.2 本轮范围

- 本次要改的模块：
  - 项目根目录部署文件
  - 容器化相关配置
  - 启动说明文档
- 本次先不处理的内容：
  - MQ、微服务、链路追踪
  - AI / Agent 升级
  - 大范围业务代码重构
- 本次重点验证的链路：
  - MySQL、Redis、Nginx、后端服务的统一启动可行性

### 1.3 预期产出

- 代码改动：
  - `docker-compose.yml`
  - 后端服务 `Dockerfile`
  - 必要的 Nginx / 启动说明文件
- 验证结果：
  - 容器环境可正常拉起
  - 后端能连上 MySQL / Redis
  - Nginx 能提供静态资源或转发能力
- 指标数据：
  - 部署步骤压缩前后对比
  - 启动耗时
  - 配置迁移复杂度
- 待沉淀到主文档的高价值点：
  - 从本机开发态迈向可交付形态

---

## 2. 开发前现状

### 2.1 当前已有实现

- 后端主工程位于 [peaksend-backend](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend)
- 前端运行环境位于 [FrontRunEnv](file:///D:/ProgramFiles/CodeProjects/PeakSend/FrontRunEnv)
- 初始化 SQL 位于 [数据库/sky.sql](file:///D:/ProgramFiles/CodeProjects/PeakSend/%E6%95%B0%E6%8D%AE%E5%BA%93/sky.sql)
- 当前开发配置位于 [application-dev.yml](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-server/src/main/resources/application-dev.yml)

### 2.2 当前问题/痛点

- 项目当前仍依赖本机手工安装 MySQL / Redis / Nginx / JDK / Maven
- 新机器启动成本高，迁移步骤分散，容易遗漏
- 当前仓库尚未具备标准的容器化交付骨架
- 后续 Day14~Day20 若继续推进，没有统一环境底座会增加切换成本

### 2.3 为什么今天要先做这个

- Day13 是后续所有工程化升级的底座
- 先把环境拉齐，后面的 Redis、MQ、微服务、日志链路才不会一直卡在“本机环境差异”
- 这一阶段也最容易形成有工程优化价值、能写进简历的成果

---

## 3. 实际推进过程记录

> 这一节按时间顺序滚动追加。

### 节点1：确认 Day13 起点与已有资源

- 时间：开工阶段
- 动作：盘点仓库中与部署相关的现有资源
- 涉及文件：
  - [README.md](file:///D:/ProgramFiles/CodeProjects/PeakSend/README.md)
  - [application-dev.yml](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-server/src/main/resources/application-dev.yml)
  - [sky.sql](file:///D:/ProgramFiles/CodeProjects/PeakSend/%E6%95%B0%E6%8D%AE%E5%BA%93/sky.sql)
  - [FrontRunEnv](file:///D:/ProgramFiles/CodeProjects/PeakSend/FrontRunEnv)
- 原因：先判断当前容器化能复用哪些资源，避免拍脑袋开工
- 结果：
  - 已确认仓库内当前没有 `Dockerfile` / `docker-compose.yml`
  - 已确认当前存在本地 Nginx 运行环境和初始化 SQL
  - 已确认后端依赖 MySQL、Redis、本地上传目录
- 备注：Day13 可以直接从“补部署骨架”切入

### 节点2：补齐 Day13 第一版容器化骨架

- 时间：推进阶段
- 动作：新增容器化相关核心文件，建立最小可运行启动方案
- 涉及文件：
  - [docker-compose.yml](file:///D:/ProgramFiles/CodeProjects/PeakSend/docker-compose.yml)
  - [peaksend-backend/Dockerfile](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/Dockerfile)
  - [application-docker.yml](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-server/src/main/resources/application-docker.yml)
  - [docker/nginx/nginx.conf](file:///D:/ProgramFiles/CodeProjects/PeakSend/docker/nginx/nginx.conf)
  - [Day13-Docker化启动说明.md](file:///D:/ProgramFiles/CodeProjects/PeakSend/docs/Day13-Docker%E5%8C%96%E5%90%AF%E5%8A%A8%E8%AF%B4%E6%98%8E.md)
- 原因：
  - 当前仓库尚无任何容器化交付文件
  - 为了尽快形成 Day13 的最小闭环，需要先把“编排 + 构建 + 容器专用配置 + 启动说明”一起补齐
- 结果：
  - 已新增 `docker-compose.yml`，统一编排 MySQL、Redis、Backend、Nginx 四类核心服务
  - 已新增后端 `Dockerfile`，采用 Maven 多阶段构建生成可运行镜像
  - 已新增 `application-docker.yml`，避免污染本地 `dev` 配置
  - 已新增容器专用 Nginx 配置，使反向代理目标从 `localhost` 切换为容器服务名
  - 已新增 Day13 启动说明文档，便于后续验证与复盘
- 备注：下一步需要真实执行 `docker compose up -d --build` 验证容器拉起情况，并补全量化数据

### 节点3：完成 Day13 第一轮真实启动验证

- 时间：验证阶段
- 动作：执行容器编排启动，并验证 4 类核心服务与页面/接口可访问性
- 涉及命令：
  - `docker compose up -d --build`
  - `docker compose ps`
  - `docker compose logs backend`
  - `docker compose logs nginx`
  - `docker compose logs mysql`
- 原因：
  - Day13 的骨架文件只有真正跑通，才算从“设计方案”进入“工程化落地”
- 结果：
  - `backend` 镜像成功构建
  - `mysql / redis / backend / nginx` 四个容器全部启动成功
  - 管理端页面 `http://localhost:8081` 可访问
  - 后端接口文档 `http://localhost:8080/doc.html` 可访问
  - `backend` 日志显示激活了 `docker` profile，Tomcat 正常监听 `8080`
  - `mysql` 日志显示数据库 `sky_take_out` 已创建，初始化脚本已执行，服务 ready for connections
- 备注：这意味着 Day13 的最小闭环已经成立，后续可进入“量化记录 + 主文档收口 + 下一步优化”阶段

---

## 4. 关键实现与中间决策

### 4.1 当前已明确的 Day13 实现方向

- 容器化优先覆盖：
  - MySQL
  - Redis
  - Nginx
  - 后端服务
- 前端源码不完整的部分，不在 Day13 解决，优先保证现有运行环境能被纳入统一启动方案
- 本地开发配置与容器配置分离：
  - 本地继续走 [application-dev.yml](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-server/src/main/resources/application-dev.yml)
  - 容器环境新增 [application-docker.yml](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-server/src/main/resources/application-docker.yml)

### 4.2 为什么采用“新增 docker profile”，而不是直接改 dev 配置

- 直接改 `dev` 配置会破坏你当前本机开发环境
- Day13 的目标是新增工程化能力，不是替换原有开发方式
- 分 profile 后，后面可以同时保留：
  - 本机联调态
  - 容器化运行态
  两条路线

### 4.3 当前实现边界

- 今天的重点不是做“完美生产部署”
- 而是先形成：
  - 可运行
  - 可验证
  - 可继续扩展
  - 可留证据
  的最小闭环

---

## 5. 验证记录

### 5.1 当前已确认的基础依赖

- MySQL：需要，且有初始化 SQL
- Redis：需要，当前开发配置显式依赖
- Nginx：需要，当前已有本地运行环境
- 后端服务：需要容器化运行骨架

### 5.2 当前仓库部署缺口确认

- `docker-compose.yml`：缺失
- 后端 `Dockerfile`：缺失
- 容器化启动说明：缺失
- 容器化环境变量说明：缺失

### 5.3 当前已补齐的部署骨架

- `docker-compose.yml`：已新增
- 后端 `Dockerfile`：已新增
- `application-docker.yml`：已新增
- 容器版 Nginx 配置：已新增
- Day13 启动说明：已新增

### 5.4 第一轮真实启动验证结果

- `docker compose ps` 结果：
  - `peaksend-backend`：Up
  - `peaksend-mysql`：Up
  - `peaksend-redis`：Up
  - `peaksend-nginx`：Up
- 后端日志验证：
  - `docker` profile 已生效
  - Tomcat 正常监听 `8080`
  - Spring Boot 启动完成，无致命异常
- MySQL 日志验证：
  - 数据库初始化完成
  - [sky.sql](file:///D:/ProgramFiles/CodeProjects/PeakSend/%E6%95%B0%E6%8D%AE%E5%BA%93/sky.sql) 已执行
  - 服务 ready for connections
- 页面与接口验证：
  - `http://localhost:8081` 可打开
  - `http://localhost:8080/doc.html` 可打开

---

## 6. 量化指标记录

### 6.1 本轮预期指标

| 指标项 | 优化前 | 优化后 | 测量方法 | 结论 |
|---|---:|---:|---|---|
| 从零部署步骤数 | 待统计 | 待统计 | 按旧流程与新流程列清单对比 | 待后续补齐 |
| 启动耗时 | 待统计 | 后端容器内 Spring Boot 启动约 7.39s | 读取 `docker compose logs backend` | 已拿到第一条真实数据 |
| 环境配置踩坑点数量 | 待统计 | 待统计 | 按旧流程与容器化流程对比 | 待补 |

### 6.2 原始数据来源

- 旧流程步骤统计：待补
- 新流程启动记录：`docker compose up -d --build`
- 容器日志与健康检查结果：`docker compose ps` 与各服务日志

### 6.3 当前可直接复用的工程量化点

- 当前已经从“零容器文件”推进到“4类核心服务统一编排”的第一版骨架
- 当前新增部署相关核心文件共 5 份，可直接作为 Day13 的工程化产出基础
- 当前已验证 4 个核心容器全部成功启动
- 当前已拿到后端启动耗时日志：Spring Boot 启动约 `7.39s`

---

## 7. 问题与排查过程

### 7.1 当前已知风险

- 本地开发配置包含硬编码环境信息，后续需要改成容器环境变量注入
- Windows 环境下容器挂载路径和换行符可能带来兼容问题
- Nginx 当前为本地运行环境，后续需要判断是直接复用目录还是单独补容器配置
- Maven 多阶段构建首次拉镜像和打包耗时可能较长
- MySQL 初始化脚本在首次启动后只会执行一次，重复验证时要注意数据卷状态

### 7.2 当前已遇到的问题

- 问题现象：
  - `docker compose up -d --build` 过程中，`backend` 镜像已成功构建，但 `mysql` 容器启动失败
  - 报错：宿主机 `3306` 端口已被占用
- 初步定位：
  - 本机已有 `mysqld.exe` 正在监听 `3306`
- 当前处理：
  - 将 [docker-compose.yml](file:///D:/ProgramFiles/CodeProjects/PeakSend/docker-compose.yml) 中 MySQL 端口映射由 `3306:3306` 调整为 `3307:3306`
- 这样处理的原因：
  - 不影响容器内部服务间通信
  - 不需要停掉你本机已有 MySQL
  - 可以最快继续推进 Day13

### 7.3 最小业务链联调时发现 `/user/shop/status` 返回 500

- 问题现象：
  - 访问 `http://localhost:8080/user/shop/status` 返回 500
- 复现方式：
  - `curl.exe http://localhost:8080/user/shop/status`
- 关键证据：
  - 后端日志出现 `RedisCommandExecutionException: WRONGPASS invalid username-password pair or user is disabled`
  - `backend` 容器环境变量中实际为 `REDIS_PASSWORD=25043`
  - `redis` 容器实际 `requirepass` 为 `060723`
- 根因定位：
  - [docker-compose.yml](file:///D:/ProgramFiles/CodeProjects/PeakSend/docker-compose.yml) 中 `backend.environment.REDIS_PASSWORD` 写成了未加引号的 `060723`
  - YAML 会把这种数字按八进制解析，最终注入容器后变成十进制字符串 `25043`
  - Redis 容器命令行参数 `--requirepass 060723` 保留了原字符串，因此后端认证失败
- 修复动作：
  - 将 `backend.environment.REDIS_PASSWORD` 改为 `"060723"`
  - 重新执行 `docker compose up -d --force-recreate backend`
- 修复后验证：
  - `curl.exe http://localhost:8080/user/shop/status`
  - 返回：`{"code":1,"msg":null,"data":0}`
  - 后端日志显示：`用户端获取店铺营业状态：打烊中`
- 经验沉淀：
  - `docker-compose.yml` 里所有看起来像数字的密码、密钥、验证码类值，后续都必须显式加双引号，避免 YAML 隐式类型转换

### 7.4 管理员登录联调时暴露的两类环境问题

- 第一类问题现象：
  - 访问 `http://localhost:8081/api/employee/login` 返回 `400 Bad Request`
- 根因定位：
  - 容器版 [docker/nginx/nginx.conf](file:///D:/ProgramFiles/CodeProjects/PeakSend/docker/nginx/nginx.conf) 中使用了上游名 `backend_server`
  - Nginx 默认会把该上游名作为 `Host` 头转发给后端
  - Tomcat 认为 `backend_server` 中的下划线 `_` 不是合法域名字符，因此在请求进入业务层前直接返回 `400`
- 修复动作：
  - 在 `/api/`、`/user/`、`/ws/` 三个代理 location 中补充
    - `proxy_set_header Host $host;`
    - `proxy_set_header X-Real-IP $remote_addr;`
    - `proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;`
    - `proxy_set_header X-Forwarded-Proto $scheme;`
  - 重新 `reload` nginx 容器配置

- 第二类问题现象：
  - `400` 修复后，管理员登录请求已进入后端与 MySQL，但接口返回“密码错误”
- 根因定位：
  - [数据库/sky.sql](file:///D:/ProgramFiles/CodeProjects/PeakSend/%E6%95%B0%E6%8D%AE%E5%BA%93/sky.sql) 中初始化的 `admin` 密码是明文 `123456`
  - [EmployeeServiceImpl.java](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-server/src/main/java/com/sky/service/impl/EmployeeServiceImpl.java) 登录逻辑会先对前端输入密码做 MD5，再与数据库字段比对
  - 因此明文种子数据与后端校验规则不一致
- 修复动作：
  - 将初始化 SQL 中 `admin` 密码改为 MD5 值 `e10adc3949ba59abbe56e057f20f883e`
  - 同步把当前容器内 MySQL 的 `admin` 数据更新为相同哈希

- 补充收口：
  - 同步将 `docker-compose.yml` 中的 `MYSQL_ROOT_PASSWORD`、`DB_PASSWORD` 也补上双引号，避免再次触发 YAML 数值解析问题
  - 在重建 `backend` 后，对 `nginx` 再执行一次 `reload`，消除旧 upstream IP 缓存导致的临时 `502`

- 最终验证结果：
  - `GET http://localhost:8080/user/shop/status` 返回成功，`data=0`
  - `POST http://localhost:8081/api/employee/login` 返回成功，拿到管理员 `token`

---

## 8. 今日结论（简版）

- Day13 已正式启动
- 当前已完成 Day13 开工前的部署资源盘点
- 已确认目前容器化骨架原本为空，并已补齐第一版最小骨架
- 当前已经完成 Day13 第一轮真实启动验证，最小启动链路已跑通
- 当前已确认：
  - 4 个核心容器可以统一拉起
  - 页面与接口可访问
  - 数据库初始化成功
- 后续主线会收口到：
  - 补 Day13 第一批量化数据
  - 选择一个最小业务链做容器态联调验证
  - 开始为主文档提炼高价值表达

---

## 9. 待同步到主文档的素材

### 9.1 可写进主文档的核心改动

- 从本机开发态转向可容器化部署的工程底座建设
- 新增 `docker-compose + Dockerfile + docker profile + 容器版 Nginx` 的统一部署骨架

### 9.2 可写进主文档的高价值指标

- 部署步骤压缩
- 启动耗时对比
- 环境迁移复杂度对比
- 后端容器化启动耗时（当前已拿到一条：Spring Boot 约 7.39s）
- 四类核心服务统一拉起成功

### 9.3 可写进主文档的句子雏形

- 将项目从依赖本机手工安装环境的开发态，推进为具备统一启动骨架的容器化版本，为后续缓存、MQ、微服务与链路追踪升级打下工程基础。
- 通过独立的 `docker` 运行 profile 保留本机开发态与容器化运行态两条路径，降低工程升级对现有联调流程的破坏。
- 完成 MySQL、Redis、Backend、Nginx 四类核心服务的统一编排与首次验证，容器态下管理端页面与接口文档均可正常访问，说明项目已从“本机可跑”迈向“具备统一启动能力”的工程化形态。

---

## 10. 证据清单

| 类型 | 文件名/位置 | 对应内容 |
|---|---|---|
| 文档 | [Day13-开发过程性文档.md](file:///D:/ProgramFiles/CodeProjects/PeakSend/docs/Day13-%E5%BC%80%E5%8F%91%E8%BF%87%E7%A8%8B%E6%80%A7%E6%96%87%E6%A1%A3.md) | Day13 过程留痕主文件 |
| 文档 | [Day13-Docker化启动说明.md](file:///D:/ProgramFiles/CodeProjects/PeakSend/docs/Day13-Docker%E5%8C%96%E5%90%AF%E5%8A%A8%E8%AF%B4%E6%98%8E.md) | Day13 启动与验证说明 |
| 配置 | [application-dev.yml](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-server/src/main/resources/application-dev.yml) | 当前开发环境依赖 |
| 配置 | [application-docker.yml](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-server/src/main/resources/application-docker.yml) | 容器运行环境配置 |
| 配置 | [docker-compose.yml](file:///D:/ProgramFiles/CodeProjects/PeakSend/docker-compose.yml) | Day13 统一编排文件 |
| 配置 | [peaksend-backend/Dockerfile](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/Dockerfile) | 后端服务镜像构建文件 |
| 配置 | [docker/nginx/nginx.conf](file:///D:/ProgramFiles/CodeProjects/PeakSend/docker/nginx/nginx.conf) | 容器版 Nginx 反向代理配置 |
| SQL | [sky.sql](file:///D:/ProgramFiles/CodeProjects/PeakSend/%E6%95%B0%E6%8D%AE%E5%BA%93/sky.sql) | 初始化数据库脚本 |
| 环境 | [FrontRunEnv](file:///D:/ProgramFiles/CodeProjects/PeakSend/FrontRunEnv) | 当前 Nginx / 前端运行环境 |
