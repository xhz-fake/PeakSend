# Day13-变更细节及详细逻辑链路说明

## 1. Day13 这一阶段真正完成了什么

如果只按标题看，Day13 很容易被理解成：

- 写一个 `docker-compose.yml`
- 给后端补一个 `Dockerfile`
- 把服务放进容器里跑起来

但结合这次项目的真实推进过程，更准确的说法应该是：

- 把 `PeakSend` 从“本机手工拼环境”的开发态，推进成“具备统一启动骨架”的容器化版本
- 不是只让容器能启动，而是把管理端和小程序的真实业务链重新跑通
- 在跑通过程中，把容器化场景里最容易踩的几类环境问题全部真正修掉，包括：
  - YAML 密码隐式类型转换
  - Nginx 反向代理 `Host` 头问题
  - 初始化 SQL 密码种子不匹配
  - 初始化导库编码问题
  - HTTP 响应中文编码问题
  - Docker 数据库与本机旧业务库不同步
  - Docker 环境微信配置不正确

一句话总结：

- Day13 真正交付的，不是一堆 Docker 文件，而是一套已经验证过、能跑管理端和小程序主链的容器化工程底座。

## 2. Day13 的边界到底在哪里

这次必须先把边界钉死，不然后面最容易把 Day13 和 Day14 之后的升级版继续混在一起。

本次 Day13 已完成、并且应该算进 Day13 的部分是：

- `docker-compose.yml` 统一编排 MySQL、Redis、Backend、Nginx
- `peaksend-backend/Dockerfile` 后端镜像构建
- `application-docker.yml` 容器专用运行配置
- 容器版 Nginx 反向代理配置
- `Day13-Docker化启动说明.md`
- 容器环境下管理端登录链路跑通
- 容器环境下用户端营业状态链路跑通
- Docker 环境与本机旧业务数据同步
- 小程序登录与下单主链恢复正常
- 中文乱码、图片缺失、套餐缺失、右上角显示异常等问题定位并闭环

本次 Day13 不应该混进来的部分是：

- Redis 深度缓存设计
- RocketMQ 异步化
- 延迟关单
- 微服务拆分
- 链路追踪与监控平台

这不是说这些东西不重要，而是：

- 它们已经属于 Day14 之后的升级动作
- Day13 当前的主验收目标，是“让项目具备统一环境启动和真实链路可验证能力”

## 3. 为什么 Day13 先做 Docker，而不是直接继续上新技术

这个问题其实非常关键，因为它决定了 Day13 的工程价值。

在 Day13 之前，这个项目要正常测试，至少要手工准备：

- 本机 MySQL
- 虚拟机或本机 Redis
- IDEA 启动后端
- 本机 Nginx
- 微信开发者工具

这套方式当然能开发，但它的问题也非常明显：

- 环境步骤分散，换机器成本高
- 配置依赖大量“脑内记忆”
- 一旦切到 Docker、Redis、MQ、微服务这些升级阶段，环境切换成本会越来越高
- 出问题时，很难判断是代码问题，还是“我这台机器环境不一样”

所以 Day13 先做 Docker，不是为了“会写 Dockerfile”，而是为了解决后面所有工程升级的共同前提：

- 先把基础环境统一起来
- 再在这个统一环境上继续长功能和架构

## 4. Day13 的完整主链应该怎么理解

如果把这次 Day13 所有文件改动、联调动作和排障过程串成一条线，可以压缩成下面这 4 段。

### 4.1 启动骨架层：用 `docker-compose` 把 4 类核心服务统一拉起

入口文件是：

- [docker-compose.yml](file:///D:/ProgramFiles/CodeProjects/PeakSend/docker-compose.yml)

这里真正完成的事不是“把 4 个容器列出来”，而是把项目过去分散在本机上的核心依赖统一到了一个编排文件里：

- `mysql`
- `redis`
- `backend`
- `nginx`

这里最重要的变化有两个：

- 容器内服务之间不再用 `localhost` 通信，而是用 Compose 服务名：
  - `mysql`
  - `redis`
  - `backend`
- 宿主机和容器的职责被明确分开：
  - 宿主机负责执行 `docker compose up`
  - 容器内部负责运行具体服务

这一步真正带来的工程价值是：

- 项目第一次具备了“按固定步骤复现环境”的能力

### 4.2 构建与配置层：后端不再默认依赖本机环境

对应文件是：

- [peaksend-backend/Dockerfile](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/Dockerfile)
- [application-docker.yml](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-server/src/main/resources/application-docker.yml)

这里最应该记住的是：

- Docker 化不是把原有 `dev` 配置强行改掉
- 而是新增一条容器专用运行路径

也就是说，现在项目同时保留了两种运行方式：

- 本机联调态：`application-dev.yml`
- 容器运行态：`application-docker.yml`

这样做的意义非常大：

- 不破坏原来的本机联调环境
- 后续排查 Docker 问题时，能明确知道自己现在跑的是哪一套配置

这一层里真正有价值的不是“多了一个 yml”，而是：

- 项目第一次形成了“本机开发态”和“交付运行态”两条清晰路径

### 4.3 代理接入层：管理端页面与后端接口重新在容器里连通

对应文件是：

- [docker/nginx/nginx.conf](file:///D:/ProgramFiles/CodeProjects/PeakSend/docker/nginx/nginx.conf)

这一层的核心目标是：

- 保持你原来管理端访问方式不变，仍然走 `http://localhost:8081`
- 但把 `/api/`、`/user/`、`/ws/` 三类请求全部转发到后端容器

也就是说，Day13 并没有重写前端，而是：

- 复用现有静态资源目录
- 通过容器版 Nginx 重建前后端联通路径

这一步的实际价值在于：

- 前端页面访问方式基本不变
- 但后端依赖已经切换成容器环境

### 4.4 真实链路验证层：不是“容器启动成功”，而是“业务链真的恢复可用”

这次 Day13 最重要的一点，是我们没有在“4 个容器 Up 了”就宣布结束。

真正完成的验证，至少包括：

- 管理端登录成功，拿到管理员 `token`
- `/user/shop/status` 在容器态返回正确数据
- 管理端页面可访问
- 小程序链路恢复，完成：
  - 点餐
  - 下单
  - 催单
  - 接单
  - 派送
  - 完成订单

所以 Day13 真正的验收标准不是：

- `docker compose ps` 全绿

而是：

- 用户和管理端关键业务链在 Docker 环境下重新恢复可用

## 5. Day13 里最关键的几次真实排障

这部分才是 Day13 最有工程价值的地方。

因为容器化最怕的不是写文件，而是：

- 以为只是“环境切换”
- 结果真实链路一跑，全是边界问题

这次我们真正处理掉的核心问题如下。

### 5.1 Redis 密码错误，不是 Redis 配错，而是 YAML 把密码当八进制解析了

问题现象：

- `/user/shop/status` 返回 `500`
- 后端日志出现：
  - `WRONGPASS invalid username-password pair or user is disabled`

根因不是 Redis 本身密码写错，而是：

- `docker-compose.yml` 里的 `060723` 没加引号
- YAML 把它按八进制数解析
- 最终注入到 `backend` 容器里变成了别的字符串

修复动作：

- 把 `REDIS_PASSWORD` 改成 `"060723"`
- 同时把 `MYSQL_ROOT_PASSWORD`、`DB_PASSWORD` 也统一加双引号

这一点非常值得记住，因为它属于典型的：

- 代码没问题
- 业务没问题
- 但环境配置语法细节直接把链路打断

### 5.2 管理端登录返回 400，不是业务错，而是 Nginx 转发的 `Host` 头不合法

问题现象：

- `POST /api/employee/login` 返回 `400 Bad Request`

真正根因在于：

- Nginx 上游名使用了 `backend_server`
- Tomcat 认为带下划线 `_` 的 `Host` 头不是合法域名
- 请求还没到业务层就被拦了

修复动作：

- 在 `/api/`、`/user/`、`/ws/` 三段代理里补齐：
  - `proxy_set_header Host $host;`
  - `proxy_set_header X-Real-IP $remote_addr;`
  - `proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;`
  - `proxy_set_header X-Forwarded-Proto $scheme;`

这个问题的工程价值在于：

- 它说明容器化里“网络能通”和“代理语义正确”不是一回事

### 5.3 管理端登录“密码错误”，不是前端输错，而是初始化 SQL 和后端认证规则不一致

问题现象：

- `400` 修完后，管理员登录仍然失败
- 提示密码错误

根因是：

- 初始化 SQL 里 `admin` 密码是明文 `123456`
- 但后端登录逻辑会先对输入密码做 MD5
- 两者天然对不上

修复动作：

- 把 [数据库/sky.sql](file:///D:/ProgramFiles/CodeProjects/PeakSend/%E6%95%B0%E6%8D%AE%E5%BA%93/sky.sql) 中管理员密码改成 MD5：
  - `e10adc3949ba59abbe56e057f20f883e`

这一点说明：

- 容器化不是只改部署文件
- 数据种子和业务校验规则只要有一个地方不一致，链路一样会挂

### 5.4 数据不一致、套餐缺失、图片缺失，不是“Docker 没同步”，而是 Docker 默认用了新的容器数据库

问题现象：

- 套餐管理为空
- 测试菜图片缺失
- 工作台数据和以前本机环境不同

真正根因不是 Docker “没自动同步”，而是：

- 容器里的 MySQL 是一套新的运行实例
- 它只会在首次初始化时执行 `sky.sql`
- 不会自动知道你本机旧 MySQL 里那套历史开发数据

所以我们必须把这个认知钉死：

- Docker 提供的是数据持久化能力
- 不是自动数据迁移能力

修复动作：

- 把本机旧 MySQL 业务库实际同步进当前容器库

这一步之后，容器环境才真正恢复了：

- 套餐
- 测试菜
- 图片 URL
- 历史分类与员工数据

### 5.5 中文乱码不是一个点的问题，而是“导库编码 + 响应编码 + 浏览器缓存”三个层次叠在一起

这次乱码问题很典型，因为它不是单一根因。

#### 第一层：初始化导库阶段可能把中文写坏

修复动作：

- 在 [数据库/sky.sql](file:///D:/ProgramFiles/CodeProjects/PeakSend/%E6%95%B0%E6%8D%AE%E5%BA%93/sky.sql) 顶部增加：
  - `SET NAMES utf8mb4;`

目的很明确：

- 防止新初始化容器时，中文在导入阶段就被错误编码写进库里

#### 第二层：HTTP 响应头没显式声明 UTF-8

修复动作：

- 在 [application-docker.yml](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-server/src/main/resources/application-docker.yml) 中增加：
  - `server.servlet.encoding.charset=UTF-8`
  - `enabled=true`
  - `force=true`

这样返回给前端的 JSON 才会明确带上：

- `application/json;charset=UTF-8`

#### 第三层：浏览器仍然可能缓存旧登录态里的乱码用户信息

这也是最后那个“右上角管理员名字仍然异常”的根因。

最终定位结果是：

- 后端返回的 `name` 已经正常
- 新浏览器会话重新登录后，右上角显示也正常
- 剩余异常来自旧浏览器缓存里残留的 `user_info`

这一步的经验非常重要：

- 当前接口修好了，不代表浏览器里历史脏数据会自动消失

### 5.6 小程序 `timeout` 和 `401`，根因并不在同一层

问题现象：

- 微信开发者工具中先出现 `timeout`
- 后续又出现 `/user/order/historyOrders 401`

真正链路拆开后，根因有两层：

- `401` 来自用户端 token 没有建立成功
- token 建不起来，是因为前面的微信登录配置不正确

我们最后定位出：

- Docker 环境最开始用的是占位的 `demo-appid/demo-secret`
- 所以微信登录直接返回 `invalid appid`

修复动作：

- 把 Docker 环境微信配置对齐到本机开发配置

修复后结果变成：

- 错误从 `invalid appid` 变成 `invalid code`

这一步说明：

- 当前 Docker 环境已经在使用正确的小程序配置
- 后续只要是真实微信登录流拿到的 `code`，整条用户端登录链就能重新成立

最终用户也验证通过了：

- 小程序下单主链完整跑通

## 6. Day13 为什么比“会 Docker”更有面试价值

如果只说“我做了 Docker 化”，其实信息量很低。

真正有面试价值的是下面这几件事：

### 6.1 你不是简单把服务装进容器，而是把旧项目迁移成可统一启动的工程版本

这意味着你做的不只是“打镜像”，而是：

- 编排依赖
- 隔离配置
- 处理前后端接入
- 重建验证路径

### 6.2 你处理的是“环境迁移后真实业务链失效”的问题

这比单纯写 Dockerfile 更像真实公司场景。

因为企业里最常见的问题不是：

- 容器起不来

而是：

- 容器起了，但登录不对、数据不对、编码不对、依赖不对、消息不对

Day13 这次你真正拿到的，是这种排障能力。

### 6.3 你已经能解释 Docker 到底改变了什么

最准确的表达不是：

- “Docker 让项目更高级了”

而是：

- Docker 没有改变业务功能
- 它改变的是环境的组装方式、复现方式和交付方式

这句话在面试里非常重要，因为它说明你理解的是：

- 工程化价值

而不是：

- 工具名词

## 7. Day13 当前最终落地文件

这次真正沉淀下来的核心文件如下：

- [docker-compose.yml](file:///D:/ProgramFiles/CodeProjects/PeakSend/docker-compose.yml)
- [peaksend-backend/Dockerfile](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/Dockerfile)
- [application-docker.yml](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-server/src/main/resources/application-docker.yml)
- [docker/nginx/nginx.conf](file:///D:/ProgramFiles/CodeProjects/PeakSend/docker/nginx/nginx.conf)
- [数据库/sky.sql](file:///D:/ProgramFiles/CodeProjects/PeakSend/%E6%95%B0%E6%8D%AE%E5%BA%93/sky.sql)
- [Day13-Docker化启动说明.md](file:///D:/ProgramFiles/CodeProjects/PeakSend/docs/Day13-Docker%E5%8C%96%E5%90%AF%E5%8A%A8%E8%AF%B4%E6%98%8E.md)
- [Day13-开发过程性文档.md](file:///D:/ProgramFiles/CodeProjects/PeakSend/docs/Day13-%E5%BC%80%E5%8F%91%E8%BF%87%E7%A8%8B%E6%80%A7%E6%96%87%E6%A1%A3.md)

## 8. Day13 的最终验证结果应该怎么表述

到目前为止，Day13 最准确的完成表述应该是：

- 4 个核心容器已经可以统一拉起
- 管理端页面与核心接口恢复可用
- 管理员登录链路跑通
- `/user/shop/status` 业务链路跑通
- Docker 环境已经同步到本机旧业务数据
- 中文乱码、图片缺失、套餐缺失、小程序登录异常等问题已定位并闭环
- 小程序完成从登录到下单、催单、接单、派送、完成订单的完整链路验证
- 剩余右上角名称异常已确认是浏览器旧缓存，不是后端新问题

如果压缩成一句话：

- Day13 我真正完成的，是把项目从“本机手工可跑”推进成“容器环境下关键业务链真实可用、可验证、可排障”的工程化版本。

## 9. Day13 从简历和面试视角应该怎么讲

如果从“我们现在的一切动作都要服务后续求职表达”这个顶层原则来看，Day13 最值得保留的表达，不是：

- “我学会了 Docker”

而是：

- “我把一个原本依赖本机手工环境的单体项目，改造成了可通过 Docker Compose 统一编排 MySQL、Redis、Nginx 和后端服务的容器化版本；在迁移过程中，系统性解决了环境变量解析、Nginx 代理、初始化数据、中文编码、历史数据迁移和微信配置等多类容器化落地问题，并完成了管理端与小程序核心业务链的真实联调验证。”

这句话背后真正能展开讲的点很多：

- 为什么要分 `dev` 和 `docker` profile
- 为什么容器内不能再写 `localhost`
- 为什么 Compose 服务名能替代固定 IP
- 为什么 Docker 只提供持久化，不自动同步旧数据库
- 为什么乱码问题不能只盯前端
- 为什么最后还要验证到小程序下单闭环

## 10. Day13 当前收尾结论

如果只用一句话总结 Day13：

- Day13 不是“把 Docker 文件补齐了”，而是“把项目从开发态推进成了具备统一启动骨架、真实链路可验证、关键环境问题已闭环的工程化版本”，这才是它真正的价值。
