# Day18 服务拆分与 OpenFeign 量化留档

## 1. 这组数字对应什么能力

- 最小核心服务拆分
- `OpenFeign` 跨服务调用治理
- Day18 第一条真实跨服务业务链

## 2. 版本边界

- 当前稳定版本：`Day18` 第一阶段
- 对应代码范围：
  - `product-service`
  - `sky-server` 的 `OpenFeign` 接入
  - `ShoppingCartServiceImpl`
  - `docker-compose.yml`
  - `Dockerfile.product`
- 当前阶段定位：
  - 代码闭环与构建闭环已完成
  - 运行态接口验证已补齐并完成闭环

## 3. 测量方法

- 先新增独立 `product-service` 模块
- 再在 `sky-server` 引入 `OpenFeign`
- 把购物车加购链路中“按 id 查询商品信息”的动作改成跨服务调用
- 最后执行：
  - `mvn -pl sky-server,product-service -am clean package -DskipTests`
  - `mvn clean package -DskipTests`
- 记录：
  - 新增独立服务数
  - Feign 查询接口数
  - 已形成的跨服务主链数
  - 全模块构建成功数

## 4. 原始数据

当前代表性结果：

- 新增独立服务数：`1`
  - `product-service`
- 新增 Feign 客户端数：`1`
  - `ProductClient`
- 当前已落下的 Feign 查询接口数：`2`
  - `getDishById`
  - `getSetmealById`
- 当前已形成的真实跨服务业务主链数：`1`
  - `购物车加购 -> Feign -> product-service`
- 当前 Maven 全量构建成功模块数：`6`
  - `peaksend-backend`
  - `sky-common`
  - `sky-pojo`
  - `sky-server`
  - `sky-gateway`
  - `product-service`
- 当前 Docker 编排新增服务数：`1`
  - `product-service`
- 当前运行态接口验证状态：
  - 已完成核心主链验证
- 当前已验证成功的运行态主链：
  - `gateway(8082) -> sky-server -> Feign -> product-service`
- 当前已补完的外层入口主链：
  - `nginx(8081) -> gateway -> sky-server -> Feign -> product-service`
- 当前运行态验证使用的真实商品 id：
  - `dishId = 71`
- 当前购物车写库验证结果：
  - `user_id = 4`
  - `dish_id = 71`
  - `amount = 26.00`
- 当前注册中心服务数：
  - `3`
  - `sky-server`
  - `sky-gateway`
  - `product-service`
- 当前附加待排查点：
  - 已定位并修复
  - 原因：`nginx` 持有旧的 `gateway` 容器 IP
  - 处理：改为 Docker DNS 动态解析并执行 `nginx -s reload`

## 5. 最终结论

- 当前 Day18 第一阶段已经把项目从“只有微服务底座”推进到了“出现真实跨服务业务调用”的状态
- 这次以 `product-service` 作为第一刀，先新增 `1` 个独立商品服务、落下 `1` 个 Feign 客户端和 `2` 个跨服务查询接口，并把购物车加购链路升级成 `sky-server -> Feign -> product-service`
- 当前代码、构建、编排与核心运行态验证都已经成立，Day18 第一条主链的正式量化闭环已经完成

## 6. 可写进简历/话术库的句子

- 在 `Nacos + Gateway` 微服务底座上继续推进最小服务拆分，独立落地 `product-service`，并通过 `OpenFeign` 将购物车加购链路改造成真实跨服务调用；当前已形成 `1` 条商品查询跨服务主链，后端 `6` 个模块全量构建通过。
