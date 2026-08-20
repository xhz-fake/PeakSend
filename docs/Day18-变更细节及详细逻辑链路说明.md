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

## 8. Day18 第一阶段最适合怎么讲

最稳的一句话是：

- Day18 我没有急着把订单主链整条拆出去，而是先独立出商品查询服务 `product-service`，再把购物车加购时对商品信息的获取改成 `OpenFeign` 跨服务调用，先拿到第一条真实、低风险、可验证的跨服务主链。

如果再展开一点，可以讲成：

- Day17 我先把 `Nacos + Gateway` 的微服务底座搭起来；到了 Day18，我开始真正做服务拆分，但没有一上来硬拆高耦合的订单域，而是先把商品查询能力抽成 `product-service`，再让 `sky-server` 通过 `Feign` 去调它。这样做的好处是，能够用最小改动先拿到一条真实跨服务调用链，同时不把当前订单主链一次性改炸。
