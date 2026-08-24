# TraceId 链路追踪与可观测性量化留档

## 1. 这组数字对应什么能力

- `traceId` 全链路透传
- 多服务日志串联与问题定位
- `gateway -> sky-server -> product-service` 主链可观测性补强

## 2. 版本边界

- 当前稳定版本：`Day19`
- 对应代码范围：
  - `sky-gateway` 的 `TraceIdGlobalFilter`
  - `sky-server` 的 `TraceIdFilter`
  - `product-service` 的 `TraceIdFilter`
  - `sky-server` 的 `FeignTraceConfiguration`
  - 三个服务的日志格式补齐
- 当前阶段定位：
  - HTTP 主链的 `traceId` 透传与日志串联已完成
  - MQ 侧代码已补齐，但当前运行态未启用对应消费链，不纳入本次正式量化

## 3. 测量方法

- 验证对象：
  - `nginx(8081) -> gateway(8082) -> sky-server(8080) -> Feign -> product-service(8083)`
- 测量方式：
  1. 使用真实用户 JWT，先执行 `DELETE /user/shoppingCart/clean`
  2. 再执行 `POST /user/shoppingCart/add`，固定传入不同的 `X-Trace-Id`
  3. 依次检查：
     - 业务接口是否返回成功
     - `gateway` 日志中是否存在同一 `traceId`
     - `sky-server` 日志中是否存在同一 `traceId`
     - `product-service` 日志中是否存在同一 `traceId`
- 样本批次：
  - 第 1 轮：`10` 次
  - 第 2 轮：`20` 次
  - 合计：`30` 次

## 4. 原始数据

### 4.1 业务主链成功率

- 第 1 轮：
  - `business_success=10/10`
- 第 2 轮：
  - `business_success_batch2=20/20`
- 合计：
  - `30/30`

### 4.2 TraceId 串联完整率

- 第 1 轮：
  - `trace_full_chain=10/10`
- 第 2 轮：
  - `trace_full_chain_batch2=20/20`
- 合计：
  - `30/30`

### 4.3 代表性日志证据

- `gateway`
  - `gateway request start: traceId=day19-verify-001`
  - `gateway request end: traceId=day19-verify-001`
- `sky-server`
  - `traceId=day19-verify-001 request start`
  - `traceId=day19-verify-001 用户端获取店铺营业状态：营业中`
- `product-service`
  - `traceId=day19-verify-003 request start: method=GET, uri=/rpc/products/dishes/71`
  - `traceId=day19-verify-003 Parameters: 71(Long)`
  - `traceId=day19-verify-003 request end: method=GET, uri=/rpc/products/dishes/71, status=200`

## 5. 最终结论

- Day19 已经把项目从“多服务日志分散、只能靠人工逐个翻查”推进到了“可以按同一个 `traceId` 串联整条 HTTP 主链日志”的状态
- 当前基于购物车跨服务链路补测 `30` 次，`gateway -> sky-server -> product-service` 主链调用成功率为 `30/30`
- 同批次下，`traceId` 在三段日志中的串联完整率为 `30/30`
- 其中跨服务传播的关键不是 `MDC` 自己同步，而是：
  - 上游服务先把 `traceId` 放入本地 `MDC`
  - `Feign RequestInterceptor` 再主动把它写入下游请求头
  - 下游服务收到请求后再放入自己的 `MDC`
- 因此，Day19 的正式量化闭环已经成立，后续可以稳定支撑简历与面试中的“链路追踪与问题定位效率提升”表达

## 6. 可写进简历/话术库的句子

- 基于 `Gateway Filter + MDC + Feign RequestInterceptor` 实现 `traceId` 全链路透传，在购物车跨服务链路补测 `30` 次请求中，主链调用成功率与三段日志串联率均为 `100%`，显著提升多服务环境下的问题定位效率。
