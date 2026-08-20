# Day18-开发过程性文档

## 1. 今天这一阶段到底在做什么

Day18 不是要把整个单体一口气拆成很多服务。

这一天真正要解决的是：

- 在 Day17 已经搭好的微服务底座上
- 迈出第一步真正的服务拆分
- 并且让项目第一次出现真实的跨服务调用

如果 Day17 停在：

- `nginx -> gateway -> backend`

那它解决的主要还是：

- 服务注册发现
- 网关统一入口
- Docker 下按服务名通信

但 Day18 要再往前推进一层，解决的是：

- 业务边界不能只停留在包结构
- 某些能力要开始真正独立成服务
- 调用方不能再默认“就在本地直接查 Mapper”

一句话概括：

- Day17 解决“微服务底座先搭起来”
- Day18 解决“在这个底座上，真正迈出第一刀服务拆分”

---

## 2. 什么叫“最小可跑”

这里的“最小可跑”，不是“少写代码”。

它真正的意思是：

- 改动尽量小
- 风险尽量小
- 但结果必须是完整闭环

也就是最少的一组改动，已经能形成：

- 有新服务
- 有注册发现
- 有服务间调用
- 能构建
- 后续能验证
- 还能讲清楚为什么这样拆

所以 Day18 这一版追求的不是：

- 一天拆完 `order-service`、`product-service`、`notify-service`

而是：

- 先拿到第一条真实跨服务链
- 先把最难讲的“为什么这么拆”讲顺

---

## 3. 为什么 Day18 第一刀先拆 `product-service`

路线里给了几个候选方向：

- `gateway-service`
- `order-service`
- `product-service`

但真正落代码时，不能只看名字，还要看耦合度。

当前代码里：

- `OrderServiceImpl` 同时牵涉订单主表、订单明细、购物车、地址簿、用户态、消息队列、状态流转
- 如果第一刀直接硬拆订单域，改动面会很大，主链也最容易被拉爆

相比之下，商品域里“按 id 查菜品 / 套餐详情”这一类能力边界更清晰：

- 输入就是商品 id
- 输出就是商品名称、图片、价格等展示数据
- 非常适合作为第一条 Feign 调用的目标

所以 Day18 这一步的实际落法是：

- 先新增 `product-service`
- 先把“商品查询能力”独立出去
- 再让购物车加购时通过 `OpenFeign` 跨服务查商品信息

这不是偏离路线，而是按路线里的主矛盾做更稳的第一刀。

---

## 4. 今天最终做成了什么

### 4.1 后端新增独立 `product-service`

已完成：

- 父工程新增模块：
  - [peaksend-backend/pom.xml](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/pom.xml)
- 新增独立服务目录：
  - [product-service](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/product-service)
- 新增启动类：
  - [ProductApplication.java](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/product-service/src/main/java/com/sky/product/ProductApplication.java)
- 新增最小查询接口：
  - [ProductQueryController.java](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/product-service/src/main/java/com/sky/product/controller/ProductQueryController.java)

这一步的本质是：

- 商品域第一次不再只是 `sky-server` 里的一个包
- 而是第一次拥有了独立服务形态

### 4.2 `sky-server` 正式接入 `OpenFeign`

已完成：

- `sky-server/pom.xml` 新增：
  - `spring-cloud-starter-openfeign`
- [SkyApplication.java](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-server/src/main/java/com/sky/SkyApplication.java) 开启 `@EnableFeignClients`
- 新增 Feign 客户端：
  - [ProductClient.java](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-server/src/main/java/com/sky/client/ProductClient.java)

这一步的本质是：

- `sky-server` 不再默认“商品信息就在我本地直接查”
- 而是开始具备跨服务调用意识

### 4.3 购物车加购链路从“本地查 Mapper”改成“跨服务查商品”

已完成：

- [ShoppingCartServiceImpl.java](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-server/src/main/java/com/sky/service/impl/ShoppingCartServiceImpl.java) 去掉对：
  - `DishMapper`
  - `SetmealMapper`
  的直接依赖
- 改为通过 `ProductClient` 获取：
  - 菜品信息
  - 套餐信息
- 当目标商品不存在时，显式抛出业务异常

这一步最关键，因为它标志着：

- Day18 第一条真实跨服务业务链已经出现

---

## 5. 今天最关键的链路变化是什么

改造前，购物车加购在后端内部更像：

- `ShoppingCartServiceImpl -> DishMapper/SetmealMapper -> MySQL`

改造后，链路升级成：

- `ShoppingCartServiceImpl -> ProductClient -> product-service -> MySQL`

它的意义不是“多绕了一圈”，而是：

- 服务边界第一次真的被拉开了
- `sky-server` 开始把商品能力视为外部服务能力
- Day18 的“服务拆分 + Feign”第一次从概念变成代码事实

---

## 6. 当前验证已经做到哪一步

### 6.1 已完成的静态验证

已完成：

- `mvn -pl sky-server,product-service -am clean package -DskipTests`
- `mvn clean package -DskipTests`

两个构建都通过，说明：

- 父工程模块关系正确
- `product-service` 能被 Maven 正常聚合
- `sky-server` 的 Feign 接入没有破坏现有编译链
- `sky-gateway` 也没有被这次改动带崩

### 6.2 已完成的运行编排准备

已完成：

- 新增 [Dockerfile.product](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/Dockerfile.product)
- 更新 [docker-compose.yml](file:///D:/ProgramFiles/CodeProjects/PeakSend/docker-compose.yml)
- 为 `product-service` 暴露：
  - `8083`

### 6.3 已补完的运行态验证

当前已完成的运行态验证包括：

- `Nacos` 服务列表从 `2` 个增加到 `3` 个：
  - `sky-server`
  - `sky-gateway`
  - `product-service`
- `product-service` 独立启动成功，并完成注册
- 通过 `8082` 网关直连打通：
  - `gateway -> backend -> Feign -> product-service`
- 购物车加购成功写入数据库：
  - `user_id = 4`
  - `dish_id = 71`
  - 金额 `26.00`

更关键的是，这次日志已经证明：

- `sky-server` 不再走本地商品查询逻辑
- `product-service` 实际收到了 `dishId = 71` 的查询请求
- `shopping_cart` 表最终插入了对应商品记录

因此当前更准确的状态已经变成：

- 代码完成
- 构建完成
- 编排完成
- `gateway -> backend -> Feign -> product-service` 运行态主链已验证

### 6.4 这次额外排掉的运行态问题

在补运行态验证时，确实遇到过：

- 外层 `nginx:8081` 访问 `/user/**` 路径时出现间歇性 `502`

但这个问题已经在本轮内完成定位和修复：

- 根因：
  - `nginx` 持有旧的 `gateway` 容器 IP
- 处理：
  - 在 `nginx.conf` 中改为 Docker DNS 动态解析
  - 执行 `nginx -s reload`

修复后再次验证：

- `http://localhost:8081/user/shop/status` 返回 `200`
- 购物车加购主链已可通过 `8081` 外层入口稳定打通

所以 Day18 当前已经不仅是核心主链成立，而是：

- `nginx -> gateway -> backend -> Feign -> product-service`

这条完整外层链路也已经成立。

---

## 7. 今天这一阶段的高价值点

### 7.1 高价值工程点

- 微服务拆分不是“目录搬家”，而是让调用关系真正跨服务化
- Day18 第一刀选择比“拆多少”更重要
- 面对高耦合订单主链时，先拆商品查询服务，是一种典型的工程降风险策略

### 7.2 面试高频点

- 什么叫“最小可跑”
- 为什么 Day18 不先拆 `order-service`
- 为什么先把购物车里的商品查询改成 `Feign`
- 当前这一步怎么证明不是“假拆分”

### 7.3 当前最适合的一句话总结

- Day18 我没有急着把订单主链全部拆散，而是先独立出 `product-service`，再把购物车加购时的商品信息查询改成 `OpenFeign` 跨服务调用，先拿到第一条真实、可验证、可讲清楚的服务拆分闭环。
