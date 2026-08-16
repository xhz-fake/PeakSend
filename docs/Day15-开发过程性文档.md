# Day15-开发过程性文档

## 1. 今天这一阶段到底在做什么

Day15 不是继续给 Day14 叠缓存注解。

这一天要解决的是另一类更像真实后端高并发场景的问题：

- 库存是限量的
- 请求会并发打进来
- 同一用户不能重复抢
- 不能只靠数据库在热点竞争时硬扛

所以 Day15 的核心目标是：

- 在 `PeakSend` 里落一个真实可运行的限量套餐活动
- 用 Redis + Lua 脚本把热点竞争校验前移
- 把“一人一单”“库存扣减”“活动状态/时间校验”收成一条完整链路
- 最后必须通过管理端页面、小程序页面和正式 Docker 后端一起真实联调

一句话概括：

- Day14 解决“高频读怎么优化”
- Day15 解决“热点抢购怎么控制”

---

## 2. 今天最终做成了什么

### 2.1 后端能力落地

已完成：

- 新增限量套餐活动表：
  - `flash_sale_setmeal_activity`
- 新增限量套餐抢购记录表：
  - `flash_sale_setmeal_order`
- 管理端接口：
  - 创建活动
  - 查询活动列表
  - 启用/停用活动
  - 删除活动
- 用户端接口：
  - 查询当前可抢活动
  - 抢购活动
  - 查询我的抢购记录
- Redis Lua 原子脚本：
  - 校验活动是否存在
  - 校验活动时间
  - 校验活动状态
  - 校验库存是否充足
  - 校验同一用户是否已抢购
  - 成功后原子扣库存并写入用户集合
- MySQL 持久化：
  - 抢购成功后落活动库存变更
  - 抢购成功后落抢购记录

### 2.2 前端入口补齐

已完成：

- 管理端新增：
  - `flash-sale-admin.html`
- 管理端首页新增限量套餐活动入口
- 小程序首页新增限量套餐活动入口
- 小程序“我的”页新增限量套餐活动入口
- 小程序新增：
  - `pages/flashSale/index.vue`
- 小程序补齐活动列表、抢购、我的抢购记录 API

### 2.3 工程化收口

已完成：

- Day15 后端代码正式打进 Docker backend
- Docker nginx 去掉临时 `host.docker.internal:8080` 过渡转发
- `8081 -> /api/` 恢复统一转发 Docker backend
- `docker-compose.yml` 补齐 Day15 SQL 初始化挂载
- Docker MySQL 当前实例已补 Day15 表结构迁移
- 小程序 `mp-weixin` 编译产物已重新生成并包含 `flashSale` 页面

---

## 3. 今天最关键的工程决策

### 3.1 不接受“只靠命令行能跑”的过渡态

今天最关键的一次方向修正，不在代码细节，而在交付标准。

一开始 Day15 后端主链路虽然已经能通过命令行验证：

- 创建活动
- 查询活动
- 抢购成功
- 重复抢购拦截
- Redis/数据库证据都能看到

但这还不够。

因为如果：

- 管理端没有入口
- 小程序没有入口
- 页面不能真实点通

那这个功能就仍然停留在“后端自证可用”，不是“真实系统可用”。

所以 Day15 的交付标准被重新钉死为：

- 必须有管理端页面入口
- 必须有小程序入口
- 必须用真实页面联调，而不是只靠 curl

这次方向纠偏本身，就是今天最重要的高价值问题点。

### 3.2 不接受宿主机 8080 过渡接线

另一个关键问题是：

- 管理页虽然一度能访问到 Day15 接口
- 但那时依赖的是 docker nginx 单独把 Day15 接口转发到宿主机 `8080`

这意味着：

- 功能能跑
- 但架构不干净
- 仍然依赖手工启动本机 jar

最终正式版收口策略改为：

- Day15 新代码正式进入 Docker backend
- nginx 恢复统一走 Docker backend
- 不再依赖宿主机手工 jar

这一步做完后，Day15 才算真正脱离过渡方案。

---

## 4. 今天遇到的关键问题与处理

### 4.1 Docker Java 17 基础镜像拉不下来

问题现象：

- 项目编译产物是 Java 17
- 直接切到 `eclipse-temurin:17-jre` / `maven:...17` 构建时失败
- 原因是当前机器 Docker 拉不到远程 17 基础镜像

处理方式：

- 继续使用本机已有的 8 系列基础镜像
- 在镜像构建阶段 `apt-get install openjdk-17-jdk`
- 在运行阶段 `apt-get install openjdk-17-jre-headless`
- 显式设置 `JAVA_HOME` 指向 17

结果：

- Docker backend 成功重新构建
- Day15 新代码正式进入容器

### 4.2 正式版管理端第一次打开活动列表 500

问题现象：

- 管理端登录成功
- 套餐加载成功
- 但活动列表接口 500

定位结果：

- Docker backend 日志明确报：
  - `flash_sale_setmeal_activity` 表不存在

根因：

- 正式版切回 Docker MySQL 后，Day15 两张表并没有在容器数据库里初始化

处理方式：

- `docker-compose.yml` 补挂载 `day15_flash_sale.sql`
- 给当前运行中的 Docker MySQL 手工执行一次 Day15 SQL

结果：

- 活动列表恢复正常
- 后续重新初始化 Docker MySQL 时也具备 Day15 表结构

### 4.3 小程序源码改了，但真实包里还没有新页面

问题现象：

- 小程序源码里已经补了 `pages/flashSale/index.vue`
- 但旧的 `unpackage/dist/dev/mp-weixin` 产物里没有这个页面

处理方式：

- 使用 HBuilderX 内置 uni-app CLI 重新编译 `mp-weixin`

结果：

- `pages/flashSale/index` 已真实进入编译产物
- 微信开发者工具打开后可直接看到页面入口

---

## 5. 今天完成的真实联调闭环

这部分是 Day15 最硬的证据，不是“我觉得应该可以”，而是已经真实点通过。

### 5.1 管理端联调

已验证：

- 进入 `http://localhost:8081/flash-sale-admin.html`
- 管理员登录成功
- 套餐列表正常加载
- 活动列表正常加载
- 通过页面真实创建活动：
  - `Day15前端联调活动`
- 活动创建成功后，列表可见：
  - 活动名
  - 套餐
  - 库存
  - 状态
  - 时间窗口

### 5.2 小程序联调

已验证：

- 微信开发者工具成功打开编译后的小程序项目
- 在首页/我的页可以看到“限量套餐活动”入口
- 进入活动页后可看到当前活动数据：
  - `Day15前端联调活动`
  - 库存 `10`
  - 价格 `46.00`

### 5.3 用户抢购主链

已验证：

- 第一次点击“立即抢购”：
  - 页面提示抢购成功
  - 页面库存 `10 -> 9`
  - “我的抢购记录”出现订单号和时间
- 第二次点击“立即抢购”：
  - 页面提示：
    - `同一用户不可重复抢购`

### 5.4 管理端与用户端状态联动

已验证：

- 管理端刷新后，库存显示 `9`
- 管理端点击“停用”后：
  - 小程序再次点击抢购，提示：
    - `限量套餐活动未启用`
- 管理端尝试删除已有抢购记录的活动：
  - 页面提示：
    - `该活动已有抢购记录，不允许删除`
- 管理端重新“启用”后：
  - 小程序仍然会因为已抢过一次而被拦截
  - 提示仍为：
    - `同一用户不可重复抢购`
- 管理端最终刷新确认：
  - 活动状态 `启用`
  - 剩余库存 `9`

---

## 6. 今天拿到的最关键证据

### 6.1 Redis 侧证据

已验证：

- `flashsale:activity:{id}`
  - 存活动状态、时间戳、库存等热点字段
- `flashsale:activity:{id}:users`
  - 存已抢购用户集合

其中已经看到的直接证据包括：

- Hash 中库存从 `2 -> 1` 的变化
- Set 中存在当前用户 `4`

### 6.2 数据库侧证据

已验证：

- `flash_sale_setmeal_activity`
  - 库存同步变化
  - `update_user` 被更新
- `flash_sale_setmeal_order`
  - 抢购记录成功落库
  - 订单号可查

### 6.3 页面侧证据

已验证：

- 管理端页面真实看到活动
- 小程序页面真实看到活动
- 小程序页面真实看到“我的抢购记录”
- 管理端页面真实看到库存回写
- 管理端页面真实看到启停与删除限制

---

## 7. 今天最值得沉淀的高价值点

### 7.1 Redis 在项目里不只是缓存

Day14 更偏“高频读缓存优化”。

Day15 则把 Redis 的角色推进到：

- 热点竞争前置校验
- 高并发原子控制
- 一人一单资格控制

这两天合起来，才真正形成：

- Redis 既能做缓存，又能做高并发资源竞争控制

### 7.2 Lua 脚本的价值不是“会写脚本”，而是把竞争校验收成一次原子执行

如果不用 Lua，而是在 Java 里分多次调 Redis：

- 先查库存
- 再查用户是否抢过
- 再扣库存
- 再写用户集合

那并发下就会有窗口期问题。

Lua 的核心价值是：

- 让这些关键校验和扣减在 Redis 内一次性完成
- 避免 Java 层自己拼多段 Redis 操作造成竞态

### 7.3 “真实可用”本身就是交付标准的一部分

今天一个非常重要的认识是：

- 后端接口能调通，不等于功能已经完整交付

只有当下面三件事一起成立时，这个功能才真的站住：

- 页面有入口
- 真实用户能点击
- 页面状态和后端状态一致

Day15 最后之所以比一开始更扎实，关键就在这里。

---

## 8. 今日高价值问题 / 踩坑点

- 不能把“命令行验证通过”误判成“真实功能已经交付”
- 过渡接线虽然能临时跑通，但会把架构状态搞脏，必须尽快回收到正式版 Docker backend
- 容器数据库初始化链路如果没同步更新，新业务表非常容易在正式环境第一次联调时直接 500
- 小程序源码改了不代表用户真的能看到，必须看编译产物里页面有没有真正进去
- Redis 高并发控制方案如果没有页面联调，很容易在最终复盘时显得像“后端自娱自乐”

---

## 9. 今天最终完成度判断

Day15 到现在的状态，不再是“半成品”。

它已经完成了：

- 后端业务开发
- Docker 正式版收口
- 管理端页面入口
- 小程序页面入口
- 真实前端联调
- Redis / MySQL / 页面三侧证据闭环

所以 Day15 现在的正确状态是：

- **功能与工程主链已完成**
- **可以正式进入源码级复盘、文档沉淀和 Git 收口阶段**

---

## 10. 证据清单

| 类型 | 文件名/位置 | 对应内容 |
|---|---|---|
| 文档 | [Day15-开发过程性文档.md](file:///D:/ProgramFiles/CodeProjects/PeakSend/docs/Day15-%E5%BC%80%E5%8F%91%E8%BF%87%E7%A8%8B%E6%80%A7%E6%96%87%E6%A1%A3.md) | Day15 过程留痕主文件 |
| 代码 | [FlashSaleSetmealActivityServiceImpl.java](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-server/src/main/java/com/sky/service/impl/FlashSaleSetmealActivityServiceImpl.java) | 限量套餐活动核心业务与 Redis/Lua 主链 |
| 代码 | [flash_sale_setmeal_seize.lua](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-server/src/main/resources/lua/flash_sale_setmeal_seize.lua) | Redis Lua 原子校验脚本 |
| 代码 | [FlashSaleSetmealActivityAdminController.java](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-server/src/main/java/com/sky/controller/admin/FlashSaleSetmealActivityAdminController.java) | 管理端活动接口 |
| 代码 | [FlashSaleSetmealActivityController.java](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-server/src/main/java/com/sky/controller/user/FlashSaleSetmealActivityController.java) | 用户端活动接口 |
| 前端 | [flash-sale-admin.html](file:///D:/ProgramFiles/CodeProjects/PeakSend/FrontRunEnv/nginx-1.20.2/html/sky/flash-sale-admin.html) | 管理端限量套餐活动页面 |
| 前端 | [pages/flashSale/index.vue](file:///D:/ProgramFiles/CodeProjects/PeakSend/miniProgram/project-rjwm-weixin-uniapp-develop-wsy/pages/flashSale/index.vue) | 小程序限量套餐活动页面 |
| SQL | [day15_flash_sale.sql](file:///D:/ProgramFiles/CodeProjects/PeakSend/%E6%95%B0%E6%8D%AE%E5%BA%93/day15_flash_sale.sql) | Day15 表结构 |
| 配置 | [docker-compose.yml](file:///D:/ProgramFiles/CodeProjects/PeakSend/docker-compose.yml) | Day15 SQL 初始化挂载 |
| 配置 | [Dockerfile](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/Dockerfile) | Java 17 容器构建兼容方案 |
