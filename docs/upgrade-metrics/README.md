# upgrade-metrics 留档规则

这个目录只存一类东西：

- **最终会写进简历或面试表达的量化证据**

不存的东西：

- 纯过程性的临时测试
- 已经被后续版本推翻的过渡数据
- 没有办法回溯测量方法的“随手记一个数字”

## 1. 留档原则

不是按“Day 几”存，而是按“最终能力线”存。

例如：

- `docker_delivery.md`
- `redis_cache.md`
- `flash_sale_async.md`
- `order_delay_close.md`
- `microservice_mainline.md`
- `trace_observability.md`

如果同一条能力线后面又升级了：

- 不要继续沿用旧版本的整链数字
- 以最新稳定版本的数字为准
- 旧数据只在“做前后对比确实有意义”时保留

## 2. 每份量化记录至少要有的 5 块内容

1. **能力名称**
   - 这条数字对应哪条简历能力线
2. **版本边界**
   - 这是在哪个阶段、哪套最终代码上测的
3. **测量方法**
   - 用了什么工具
   - 请求样本是多少
   - 基线和优化后分别怎么取
4. **原始证据**
   - 命令输出、截图、日志、Dashboard 数据、SQL 对比
5. **最终可复述结论**
   - 最后能写进简历的那一句话

## 3. 建议模板

每个能力线文件都按下面结构写：

```md
# 能力线名称

## 1. 这组数字对应什么能力

## 2. 版本边界

## 3. 测量方法

## 4. 原始数据

## 5. 最终结论

## 6. 可写进简历/话术库的句子
```

## 4. 当前阶段的文件规划与现状

按当前路线，优先保留下面几条：

- `docker_delivery.md`
- `redis_cache.md`
- `flash_sale_async.md`
- `order_delay_close.md`
- `microservice_mainline.md`
- `trace_observability.md`

当前已落档：

- `flash_sale_async.md`
- `order_delay_close.md`
- `microservice_mainline.md`

当前待补：

- `docker_delivery.md`
- `redis_cache.md`
- `trace_observability.md`

## 5. 当前执行规则

- Day16、Day18、Day19：必须补正式量化留档
- Day17：只补轻量工程指标，不急着做最终业务压测
- Day20：负责汇总、补漏、筛掉过渡数据，不发明新数字

补充同步原则：

- 一旦某条能力线已经在过程文档中拿到了可复述的正式数字，就要尽快同步到这个目录
- 不能出现“全局规则要求留档，但目录里没有对应能力文件”的状态

一句话记住：

- **量化数据按最终能力线收，不按开发日期堆。**
