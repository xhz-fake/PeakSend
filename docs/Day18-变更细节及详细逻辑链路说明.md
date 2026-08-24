# Day18-变更细节及详细逻辑链路说明

## 1. Day18 这一阶段真正要解决的是什么

Day18 的目标不是继续停留在：

- `gateway` 能转发
- `Nacos` 能注册

而是要把 Day17 的微服务底座继续往前推一层：

- 让单体内部某部分能力真正独立成服务
- 让另一个服务开始通过 `OpenFeign` 调它

所以 Day18 真正的主问题是：

- **怎么迈出第一刀服务拆分，而且这第一刀既不能太轻，也不能把主链拆炸。**

---

## 2. 为什么第一刀没有先拆 `order-service`

这个问题必须先讲清楚，因为它决定了 Day18 到底是在“按路线推进”，还是“看起来很热闹，实际上把主链改崩”。

当前的订单域实现里，`OrderServiceImpl` 同时牵涉：

- 购物车
- 地址簿
- 订单主表
- 订单明细
- 用户上下文
- 延迟关单消息
- 状态流转

它的问题不是“不能拆”，而是：

- 第一刀拆它，改动面太大
- 一次会牵到太多业务表和太多运行链
- 很容易出现“服务是拆了，但今天根本跑不起来”

所以 Day18 的更稳做法不是：

- 先拆最核心、最重的服务

而是：

- 先拆边界更清晰、调用关系更容易改造的能力

这就是为什么当前第一刀选择的是：

- `product-service`

---

## 3. 为什么商品域更适合做第一刀

商品域里，当前最适合先独立的不是整个管理后台，而是：

- 通过商品 id 查询菜品信息
- 通过套餐 id 查询套餐信息

因为这一类能力天然具备下面几个特点：

### 3.1 输入输出简单

- 输入：
  - 一个 `dishId` 或 `setmealId`
- 输出：
  - 名称、图片、价格、分类信息等商品展示数据

### 3.2 调用方明确

当前最明确依赖这块信息的就是：

- 购物车加购链路

也就是说它本来就在干一件事：

- 根据前端传来的商品 id
- 去商品域把名称、图片、金额补齐

这正好是最适合改成跨服务调用的位置。

### 3.3 对主链冲击小

即便这一步做调整，影响也主要集中在：

- 商品查询
- 购物车加购

不会像订单服务拆分那样，立刻波及整个订单状态机和消息链。

---

## 4. 这次代码层到底改了哪几类东西

这次 Day18 第一阶段，主要改了四类代码。

### 4.1 父工程模块层

在 [peaksend-backend/pom.xml](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/pom.xml) 中新增：

- `product-service`

它解决的是：

- Maven 聚合工程第一次正式接纳独立商品服务模块

### 4.2 `sky-server` 的 Feign 接入层

在 [sky-server/pom.xml](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-server/pom.xml) 中新增：

- `spring-cloud-starter-openfeign`

在 [SkyApplication.java](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-server/src/main/java/com/sky/SkyApplication.java) 中新增：

- `@EnableFeignClients`

在 [ProductClient.java](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-server/src/main/java/com/sky/client/ProductClient.java) 中定义：

- `getDishById`
- `getSetmealById`

这几步共同解决的是：

- `sky-server` 已经具备通过服务名去调用 `product-service` 的能力

### 4.3 `product-service` 的最小服务骨架

新增：

- [ProductApplication.java](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/product-service/src/main/java/com/sky/product/ProductApplication.java)
- [ProductQueryController.java](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/product-service/src/main/java/com/sky/product/controller/ProductQueryController.java)
- [ProductQueryService.java](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/product-service/src/main/java/com/sky/product/service/ProductQueryService.java)
- [ProductQueryServiceImpl.java](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/product-service/src/main/java/com/sky/product/service/impl/ProductQueryServiceImpl.java)
- `DishMapper / SetmealMapper`
- 对应 MyBatis XML

这里要特别注意：

- 这一版不是把整个商品域后台全量迁走
- 而是先建立“最小商品查询服务”

也就是说这一步强调的是：

- **先形成独立服务 + 可调用接口**

而不是：

- **先把所有商品管理功能都搬家**

### 4.4 购物车主链的调用关系改造

在 [ShoppingCartServiceImpl.java](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-server/src/main/java/com/sky/service/impl/ShoppingCartServiceImpl.java) 中：

- 去掉了对本地 `DishMapper`
- 去掉了对本地 `SetmealMapper`
- 改成注入 `ProductClient`

原来更像：

- `购物车服务 -> 本地 Mapper -> 数据库`

现在变成：

- `购物车服务 -> ProductClient -> product-service -> 数据库`

这就是 Day18 这一版最重要的代码事实。

---

## 5. 为什么这一步已经算“真实服务拆分”

因为判断微服务拆分是不是真的成立，不是看目录名，而是看调用关系有没有变。

如果只是：

- 新建了个模块
- 复制了几份代码
- 但原服务还是继续直连本地 Mapper

那它本质上还只是“把代码放在另一个地方”，不是服务拆分。

而现在这一步已经发生了两个关键变化：

### 5.1 服务边界变了

商品查询能力不再只存在于 `sky-server` 内部。

### 5.2 调用方式变了

购物车加购不再默认“本地拿商品数据”，而是：

- 通过服务名找 `product-service`
- 再由独立服务返回商品信息

所以这一步虽然只是一刀，但它已经不是假拆分。

---

## 6. Docker 与部署边界这次做了什么准备

这次还新增了：

- [Dockerfile.product](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/Dockerfile.product)
- [docker-compose.yml](file:///D:/ProgramFiles/CodeProjects/PeakSend/docker-compose.yml) 中的 `product-service`

这说明 Day18 当前不是只在本地 IDE 里“编译通过”。

它还进一步准备了：

- 独立镜像构建入口
- 独立端口：
  - `8083`
- 独立服务注册

因此这一步已经具备了：

- 独立打包
- 独立部署
- 独立注册到 `Nacos`

的运行形态基础。

---

## 6.1 这次为什么会出现 `nginx:8081` 间歇性 `502`

这个问题不是商品服务本身挂了，也不是网关路由规则写错了。

真正原因是：

- `docker/nginx/nginx.conf` 里最开始使用的是固定 `upstream gateway_server { server gateway:8082; }`
- `nginx` 在启动时会先把 `gateway` 解析成当时的容器 IP
- 但 Day18 验证过程中，`gateway` 容器被重建过，IP 从旧地址变成了新地址
- `nginx` 还在拿旧 IP 转发，所以访问 `8081/user/**` 时就会报 `502 Bad Gateway`

这类问题在 Docker 里很典型，本质上是：

- **容器服务名没变，但背后的容器 IP 变了；而 `nginx` 仍然持有旧解析结果。**

---

## 6.2 这次是怎么修的

这次没有靠“手动重启一次 nginx 就算完”，而是把配置本身改成了更稳的方式。

具体改动在：

- [docker/nginx/nginx.conf](file:///D:/ProgramFiles/CodeProjects/PeakSend/docker/nginx/nginx.conf)

核心改法是：

- 增加：
  - `resolver 127.0.0.11 valid=30s ipv6=off;`
- 把原来固定 `upstream` 的写法，改成：
  - `set $gateway_upstream http://gateway:8082;`
  - `proxy_pass $gateway_upstream;`

这样做的意义是：

- 不再让 `nginx` 只在启动时解析一次 `gateway`
- 而是让它通过 Docker 内置 DNS 在运行期动态解析服务名

修完后再执行：

- `docker exec peaksend-nginx nginx -s reload`

即可让新配置生效。

---

## 6.3 这次怎么证明 `nginx` 这一层也收口了

修复后补了两层验证：

### 第一层：外层健康接口恢复

通过：

- `http://localhost:8081/user/shop/status`

已经返回：

- `200 OK`

这说明：

- `nginx -> gateway -> backend`

这一层已经重新打通。

### 第二层：购物车全链路通过外层入口验证成功

再次通过：

- `DELETE /user/shoppingCart/clean`
- `POST /user/shoppingCart/add`
- `GET /user/shoppingCart/list`

且入口使用的是：

- `http://localhost:8081`

最终结果是：

- 购物车列表成功返回
- `product-service` 日志出现了 `dishId = 71` 的查询 SQL
- `shopping_cart` 表中出现新的购物车记录

这说明 Day18 当前已经不只是：

- `gateway -> backend -> Feign -> product-service`

打通了，

而是：

- `nginx -> gateway -> backend -> Feign -> product-service`

这条完整主链也已经打通。

---

## 7. 当前验证结果应该怎么准确表述

### 7.1 已经完成的

已完成：

- `product-service` 模块代码落地
- `sky-server` 的 `OpenFeign` 接入
- 购物车加购链路改成跨服务取商品信息
- Docker 编排补齐 `product-service`
- 后端全模块 `mvn clean package -DskipTests` 构建通过

### 7.2 已补完的运行态验证

当前已经补完的运行态验证包括：

- `product-service` 已真正拉起并完成注册
- 购物车这条 Feign 主链已通过接口验证打通
- 已通过 `8082` 验证：
  - `gateway -> backend -> Feign -> product-service`
- 已通过 `8081` 验证：
  - `nginx -> gateway -> backend -> Feign -> product-service`
- 购物车加购成功写入数据库：
  - `user_id = 4`
  - `dish_id = 71`
  - `amount = 26.00`
- `product-service` 日志已出现：
  - `DishMapper.getById`
  - `Parameters: 71(Long)`

所以 Day18 当前最准确的对外说法是：

- **第一阶段代码、构建、编排与运行态主链验证都已经完成，项目已经从“只有微服务底座”推进到了“出现真实跨服务业务调用”的状态。**

---

## 8. Feign、Spring MVC、MyBatis 在这条链里分别干什么

这一段是 Day18 最容易反复混淆、但又最值得讲透的部分。

因为购物车加购改造成跨服务调用之后，链路里同时出现了：

- `OpenFeign`
- `Spring MVC`
- `MyBatis`
- `DishVO`
- `JSON`

如果不把这几个角色拆开，很容易误以为：

- `Feign` 是一个夹在两个服务中间的独立节点
- 或者“所有 JSON 和对象之间的转换都是 Spring MVC 在干”

这些理解都不够准确。

### 8.1 Feign 不是独立服务，而是调用方内部的远程调用组件

在当前项目里，真正主动发起跨服务请求的是：

- `sky-server`

而 `Feign` 的作用是：

- 让 `sky-server` 里调用 `ProductClient` 接口方法时
- 底层自动构造成一个 HTTP 请求
- 再发送给 `product-service`

所以更准确的理解不是：

- `sky-server -> Feign -> product-service`（把 `Feign` 当成独立节点）

而是：

- `sky-server` **借助 `Feign`** 去调用 `product-service`

这也是为什么：

- `Feign` 属于调用方 `sky-server`
- 而不是网络中单独存在的一个“中间服务”

### 8.2 为什么 `sky-server -> product-service` 要 Feign，而返回时不需要

这个问题非常关键。

原因是：

- `Feign` 只负责“主动发请求”的一侧
- 不负责“被动返回响应”的一侧

也就是说：

- 当 `sky-server` 需要商品信息时，它要主动去找 `product-service`
- 这时候需要有人帮它构造请求、带上参数、发送 HTTP、解析响应
- 所以这里需要 `Feign`

但 `product-service` 在收到请求之后，只是在做：

- 接住请求
- 查数据
- 沿着**同一条 HTTP 连接**把响应写回去

这不是重新发起了一次新请求，所以它不需要 `Feign`。

一句话记忆：

- **Feign 只出现在请求发起方；响应返回方只是复用原连接回包。**

### 8.3 这条商品查询跨服务链路到底怎么走

完整顺序如下：

1. `sky-server` 的 [ShoppingCartServiceImpl.java](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-server/src/main/java/com/sky/service/impl/ShoppingCartServiceImpl.java) 里调用：
   - `productClient.getDishById(dishId)`
2. `sky-server` 内部的 `Feign` 根据 [ProductClient.java](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-server/src/main/java/com/sky/client/ProductClient.java) 的接口声明：
   - 把方法调用构造成 HTTP 请求
   - 发给 `product-service`
3. `product-service` 的 [ProductQueryController.java](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/product-service/src/main/java/com/sky/product/controller/ProductQueryController.java) 接住请求
4. Controller 调 Service，Service 再调 Mapper
5. `MyBatis` 根据 Mapper + XML 执行 SQL
6. 数据库结果被映射成 `DishVO`
7. `Spring MVC / Jackson` 再把这个 `DishVO` 序列化成 JSON 响应
8. 响应通过原 HTTP 连接回到 `sky-server`
9. `sky-server` 侧的 `Feign` 再把 JSON 解码成 `DishVO`
10. 业务代码继续使用这个 `DishVO` 补齐购物车数据

### 8.4 为什么这里不是“Spring MVC 把请求体 JSON 转成 DTO”

这也是一个高频误区。

当前商品查询接口是：

- `GET /rpc/products/dishes/{id}`

所以这里并没有：

- `@RequestBody`
- JSON 请求体
- DTO 绑定

在这个场景下，`Spring MVC` 做的是：

- 把路径中的 `{id}` 绑定为方法参数 `Long id`

而不是：

- 把 JSON 请求体解析成 DTO

这和用户端新增购物车这类接口不同。

后者更像：

- 前端发 JSON
- `Spring MVC` 把 JSON 解析成 `ShoppingCartDTO`

而商品查询这一条链不是这种模式。

### 8.5 MyBatis 和 Spring MVC 的边界在哪里

在 `product-service` 内部，这两者是连续配合、但职责不同的两步：

#### 第一步：MyBatis

- 根据 Mapper 方法找到对应 SQL
- 绑定参数
- 执行 SQL
- 把数据库结果集映射成 `DishVO`

也就是：

- **数据库行 -> `DishVO`**

#### 第二步：Spring MVC / Jackson

- Controller 返回 `DishVO`
- 把这个 `DishVO` 序列化成 JSON 响应体

也就是：

- **`DishVO` -> JSON**

所以最准确的说法是：

- `MyBatis` 负责服务内部的数据访问与对象映射
- `Spring MVC` 负责服务端 HTTP 收发时的参数绑定与响应序列化

### 8.6 为什么 `sky-server` 侧不是 Spring MVC 在把 JSON 转回对象

因为这一步发生在：

- `sky-server` 作为**HTTP 客户端**
- 收到别的服务响应之后

它不是 Controller 在接浏览器请求，所以这里不是 Spring MVC 的典型服务端场景。

更准确地说：

- `product-service` 服务端由 `Spring MVC` 把 `DishVO` 写成 JSON
- `sky-server` 调用侧由 `Feign` 客户端根据接口返回类型把 JSON 解码成 `DishVO`

所以一句话总结这三个组件的分工：

- `Feign`：解决跨服务通信
- `Spring MVC`：解决服务端 HTTP 收发
- `MyBatis`：解决服务内部数据库访问

## 9. Day18 第一阶段最适合怎么讲

最稳的一句话是：

- Day18 我没有急着把订单主链整条拆出去，而是先独立出商品查询服务 `product-service`，再把购物车加购时对商品信息的获取改成 `OpenFeign` 跨服务调用，先拿到第一条真实、低风险、可验证的跨服务主链。

如果再展开一点，可以讲成：

- Day17 我先把 `Nacos + Gateway` 的微服务底座搭起来；到了 Day18，我开始真正做服务拆分，但没有一上来硬拆高耦合的订单域，而是先把商品查询能力抽成 `product-service`，再让 `sky-server` 通过 `Feign` 去调它。这样做的好处是，能够用最小改动先拿到一条真实跨服务调用链，同时不把当前订单主链一次性改炸。
