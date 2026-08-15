# Day14-开发过程性文档

> 用途：记录 Day14 Redis 高并发读场景优化专题的真实推进过程、验证记录、量化数据与中间决策。
>
> 今天的主目标不是“再把 Redis 接一遍”，而是把 `PeakSend` 当前已有的缓存雏形升级成一个更像工程化优化专题的版本：能讲命中链路、能讲失效策略、能讲高并发常见问题、也能拿到验证证据。

---

## 1. 今日任务概览

### 1.1 今日目标

- 启动 Day14：Redis 缓存工程化专题
- 先补齐用户端分类缓存链路
- 再补 Redis Cache 的 TTL、序列化、异常兜底与一致性设计
- 为后续缓存穿透、报表热点缓存、量化验证留出落点

### 1.2 本轮范围

- 本次要改的模块：
  - 用户端分类查询接口
  - 管理端分类变更接口
  - Spring Cache / Redis 缓存配置
  - Day14 文档与规划同步
- 本次先不处理的内容：
  - RocketMQ
  - 微服务拆分
  - TraceId / 链路追踪
- 本次重点验证的链路：
  - 用户端分类列表：首次查库，后续命中缓存
  - 管理端分类变更：用户端分类缓存失效
  - Redis 中缓存 key / TTL / 过期策略是否符合设计
- 当前重排后的 Day14 收口版明确包含：
  - 分类缓存命中与失效闭环
  - TTL / 序列化 / Redis 异常兜底
  - 空值缓存防穿透第一版
  - 报表类热点查询结果缓存
  - 一轮能复用到简历和面试中的量化验证

### 1.3 预期产出

- 代码改动：
  - 分类缓存接入
  - 分类缓存失效接入
  - Redis Cache 统一配置增强
- 验证结果：
  - 分类缓存命中链跑通
  - 管理端变更后用户端重新回库
  - Docker 环境下 Redis 缓存可观察
- 指标数据：
  - 分类接口缓存前后响应时间
  - Redis key 命中情况
  - MySQL Questions 增量对比
- 待沉淀到主文档的高价值点：
  - Day14 已从“会用 Redis”切换到“能讲高频读场景优化”

---

## 2. 开发前现状

### 2.1 当前已有实现

- [用户端营业状态](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-server/src/main/java/com/sky/controller/user/ShopController.java) 已直接读 Redis
- [用户端菜品列表](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-server/src/main/java/com/sky/controller/user/DishController.java) 已有 `@Cacheable`
- [用户端套餐列表](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-server/src/main/java/com/sky/controller/user/SetmealController.java) 已有 `@Cacheable`
- [管理端菜品接口](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-server/src/main/java/com/sky/controller/admin/DishController.java) 与 [管理端套餐接口](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-server/src/main/java/com/sky/controller/admin/SetmealController.java) 已做缓存失效

### 2.2 当前问题/痛点

- 用户端分类列表还没有缓存
- 管理端分类新增/修改/启停/删除还没有失效分类缓存
- 当前缓存体系还缺少更明确的：
  - TTL 策略
  - 过期随机扰动
  - 空值缓存 / 穿透防护落点
  - Redis 异常时的兜底思路
- 如果现在就停下，Day14 只能讲“项目里有 Redis”，还不能很好体现高并发工程认知

### 2.3 为什么今天要先做这个

- Day13 已经把 Docker 运行底座搭好，Day14 最适合顺势把 Redis 做成第一批可量化优化成果
- 分类缓存比菜品/套餐缓存更简单，适合教学式推进
- 它刚好能帮我们先吃透两个关键概念：
  - 缓存命中
  - 写后删缓存的一致性设计

---

## 3. 实际推进过程记录

> 这一节按时间顺序滚动追加。

### 节点1：梳理 Day14 的真实起点

- 时间：开工阶段
- 动作：盘点当前项目里已有 Redis 用法与缓存注解分布
- 涉及文件：
  - [user/ShopController.java](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-server/src/main/java/com/sky/controller/user/ShopController.java)
  - [user/CategoryController.java](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-server/src/main/java/com/sky/controller/user/CategoryController.java)
  - [user/DishController.java](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-server/src/main/java/com/sky/controller/user/DishController.java)
  - [user/SetmealController.java](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-server/src/main/java/com/sky/controller/user/SetmealController.java)
  - [admin/CategoryController.java](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-server/src/main/java/com/sky/controller/admin/CategoryController.java)
- 原因：先判断项目是“没缓存”还是“缓存做了一半”，避免方案设计脱离真实代码
- 结果：
  - 已确认用户端菜品/套餐已有 `@Cacheable`
  - 已确认管理端菜品/套餐已有 `@CacheEvict`
  - 已确认用户端分类和管理端分类失效链路存在明显缺口
- 备注：Day14 第一刀确定为“分类缓存 + 分类缓存失效”

### 节点2：补齐用户端分类缓存链路

- 时间：实现阶段
- 动作：在用户端分类查询接口上接入 `@Cacheable`
- 涉及文件：
  - [user/CategoryController.java](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-server/src/main/java/com/sky/controller/user/CategoryController.java)
- 原因：
  - 分类列表本身是高频读接口
  - 它按 `type` 查询，缓存 key 设计简单，非常适合做 Day14 教学第一刀
- 结果：
  - 已新增 `@Cacheable(cacheNames = "categoryCache", key = "#type == null ? 'all' : #type")`
- 备注：
  - 当前先沿用项目已有风格，在 Controller 层声明缓存
  - 未传 `type` 时，用 `all` 做兜底 key，避免空 key

### 节点3：补齐管理端分类缓存失效链路

- 时间：实现阶段
- 动作：给管理端分类新增、修改、删除、启停接口补 `@CacheEvict`
- 涉及文件：
  - [admin/CategoryController.java](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-server/src/main/java/com/sky/controller/admin/CategoryController.java)
- 原因：
  - 如果只做读缓存，不做写后失效，用户端会读到脏数据
  - 分类会影响用户端分类页、菜品页、套餐页的入口展示
- 结果：
  - 已在新增/修改/删除/启停 4 个接口上接入 `@CacheEvict(cacheNames = "categoryCache", allEntries = true)`
- 备注：
  - 这里先选 `allEntries = true`，优先保证一致性，再考虑更细粒度优化

### 节点4：完成第一轮编译校验

- 时间：实现后验证
- 动作：执行 Maven 编译
- 涉及命令：
  - `mvn -DskipTests compile`
- 原因：先确认第一刀改动没有破坏项目构建
- 结果：
  - `BUILD SUCCESS`
- 备注：说明当前 Day14 第一刀已具备继续扩展的稳定基础

### 节点5：接管 Redis 序列化与 TTL 配置

- 时间：实现阶段
- 动作：
  - 新增 Redis 配置类
  - 新增缓存 TTL 配置类
  - 在 `application-dev.yml` 与 `application-docker.yml` 中加入缓存 TTL 参数
- 涉及文件：
  - [RedisConfiguration.java](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-server/src/main/java/com/sky/config/RedisConfiguration.java)
  - [CacheTtlProperties.java](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-server/src/main/java/com/sky/properties/CacheTtlProperties.java)
  - [application-dev.yml](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-server/src/main/resources/application-dev.yml)
  - [application-docker.yml](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-server/src/main/resources/application-docker.yml)
- 原因：
  - 项目原本只有 `@EnableCaching`，但没有接管 `RedisCacheManager`
  - TTL、序列化策略、默认缓存行为都还不在我们自己手里
- 结果：
  - 已统一配置 RedisTemplate 与 Spring Cache 的序列化
  - 已为不同缓存设置分档 TTL：
    - `categoryCache = 20m`
    - `dishCache = 30m`
    - `setmealCache = 35m`
- 备注：
  - 当前先用“不同缓存不同 TTL”的方式做第一版错峰过期
  - 这是比“一股脑统一 30 分钟过期”更稳的工程起点

### 节点6：修复 Redis 缓存中的 LocalDateTime 序列化异常

- 时间：运行态验证阶段
- 动作：重建后端后，使用用户端 JWT 测试分类接口缓存链
- 现象：
  - 接口首次访问时返回 `500`
  - 后端日志报：
    - `SerializationException`
    - `Could not write JSON`
    - `Category["createTime"]`
- 根因：
  - HTTP 层使用的是项目自定义的 `JacksonObjectMapper`
  - Redis 层最初使用默认 `GenericJackson2JsonRedisSerializer`
  - 这两层的时间类型序列化规则不一致，导致 `LocalDateTime` 无法写入缓存
- 修复：
  - 在 Redis 配置中改为使用项目已有的 `JacksonObjectMapper`
  - 同时启用默认类型信息，保证缓存对象可反序列化
- 结果：
  - 分类接口恢复正常
  - Redis 能正确写入 `categoryCache::1`
- 备注：
  - 这是 Day14 当前非常有价值的一个工程问题样本：HTTP 返回能过，不代表缓存层一定能过

### 节点7：完成第一轮真实缓存验证

- 时间：运行态验证阶段
- 动作：
  - 重建 Docker `backend`
  - 使用测试用户 JWT 连续调用两次 `/user/category/list?type=1`
  - 观察 Redis key、TTL 和后端日志
- 验证结果：
  - 第一次请求：命中数据库查询
  - 第二次请求：未再出现分类查询日志，说明命中缓存
  - Redis 中存在：
    - `categoryCache::1`
  - 实测 TTL：
    - `1192` 秒（约 20 分钟，符合配置）
- 备注：
  - 这说明 Day14 第一刀已经不只是“代码写了注解”，而是缓存行为已经在运行态真实出现

### 节点8：完成分类缓存失效验证并恢复测试数据

- 时间：运行态验证阶段
- 动作：
  - 管理端登录获取管理员 token
  - 调用：
    - `POST /admin/category/status/0?id=24`
  - 观察 Redis 中 `categoryCache::1` 是否被删除
  - 再调用：
    - `POST /admin/category/status/1?id=24`
  - 恢复测试分类状态并重建缓存
- 结果：
  - 改分类状态后，`categoryCache::1` 立刻不存在
  - 恢复状态后，重新访问分类接口，缓存再次建立
- 备注：
  - 这一步证明管理端写操作已经真正打通“写后删缓存”的一致性链路

### 节点9：补上缓存异常兜底并完成一次短时故障验证

- 时间：运行态验证阶段
- 动作：
  - 在 Redis 配置中新增 `CacheErrorHandler`
  - 显式挂接到 Spring Cache 基础设施
  - 短暂停止 Redis 容器，验证分类接口是否还能继续返回业务结果
- 涉及文件：
  - [RedisConfiguration.java](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-server/src/main/java/com/sky/config/RedisConfiguration.java)
- 结果：
  - 分类接口在 Redis 暂停期间仍成功返回业务数据
  - 日志中记录：
    - `Redis 写入缓存失败，忽略本次缓存写入`
  - Redis 恢复后，容器已重新拉起
- 备注：
  - 当前验证说明：
    - 至少“缓存写入失败不拖垮主链路”已经成立
  - 这为后续继续验证“缓存读取失败回库”的行为打下基础

### 节点10：补上空值缓存，完成缓存穿透第一版验证

- 时间：运行态验证阶段
- 动作：
  - 选取用户端地址簿按 id 查询作为穿透防护第一版落点
  - 在 [AddressBookController.java](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-server/src/main/java/com/sky/controller/user/AddressBookController.java) 中给 `GET /user/addressBook/{id}` 增加 `@Cacheable`
  - 缓存 key 设计为：
    - `当前用户id:地址id`
  - 为 `addressBookByIdCache` 单独配置短 TTL：
    - `2m`
  - 给地址新增、设默认、修改、删除补 `@CacheEvict(allEntries = true)`
  - 重建 Docker `backend` 后，使用真实用户 JWT 连续请求不存在的地址：
    - `GET /user/addressBook/999999`
- 结果：
  - 第一次请求返回：
    - `{"code":1,"msg":null,"data":null}`
  - Redis 中出现：
    - `addressBookByIdCache::4:999999`
  - 第二次请求如果已经晚于 2 分钟 TTL，则会重新查库并重建空值缓存
  - 在 TTL 未过期前再次请求时：
    - 只剩 JWT 日志
    - `AddressBookController` 日志消失
    - `AddressBookMapper.getById` SQL 消失
  - 说明“查不到”的结果也已经命中缓存
- 备注：
  - 这一步证明 Day14 已经开始承载“缓存穿透第一版防护”这个高频面试点
  - 这里最重要的证据不是单看耗时，而是：
    - 方法体是否执行
    - SQL 是否继续出现

### 节点11：补上报表结果缓存，完成报表热点查询量化验证

- 时间：运行态验证阶段
- 动作：
  - 选取管理端营业额统计作为报表结果缓存落点
  - 在 [ReportController.java](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-server/src/main/java/com/sky/controller/admin/ReportController.java) 中给 `GET /admin/report/turnoverStatistics` 增加 `@Cacheable`
  - 缓存 key 设计为：
    - `begin:end`
  - 为 `turnoverReportCache` 单独配置 TTL
  - 重建 Docker `backend` 后，使用真实管理员 JWT 连续请求同一时间区间：
    - `GET /admin/report/turnoverStatistics?begin=2026-08-01&end=2026-08-14`
    - `GET /admin/report/turnoverStatistics?begin=2026-08-02&end=2026-08-13`
  - 结合 Redis key、TTL、响应时间与 MySQL `Questions` 做量化对比
- 结果：
  - 第一次请求老区间：
    - `0.707692s`
  - 第二次请求同老区间：
    - `0.030966s`
  - 第一次请求新区间：
    - `0.076508s`
  - 第二次请求同新区间：
    - `0.015217s`
  - Redis 中出现：
    - `turnoverReportCache::2026-08-01:2026-08-14`
    - `turnoverReportCache::2026-08-02:2026-08-13`
  - 实测 TTL：
    - 老区间缓存剩余 `44` 秒时再次命中成功
  - MySQL `Questions`：
    - 基线：`22`
    - 第一次新区间请求后：`43`
    - 第二次同新区间请求后：`46`
- 备注：
  - 这一步说明报表缓存不是缓存原始表数据，而是缓存“某个时间区间最终算出来的统计结果”
  - `Questions` 是全局累计值，更适合看增量趋势，而不是机械抠某一次必须精确等于多少条 SQL
  - 这一步把 Day14 从“列表缓存”推进到了“结果级缓存优化”

---

## 4. 关键实现与中间决策

### 4.1 为什么 Day14 不是“从 0 到 1 接 Redis”

- 当前项目里 Redis 已经真实存在：
  - 店铺营业状态直接读 Redis
  - 菜品和套餐已有缓存注解
- Day14 的价值不在于“重新接一遍中间件”
- 而在于把已有缓存能力升级成：
  - 有更清晰的场景边界
  - 有更完整的一致性设计
  - 有更具体的高并发问题应对
  - 有可量化验证结果

### 4.2 为什么先从分类缓存开始

- 分类查询链更简单，适合教学式推进
- key 只和 `type` 有关，理解成本低
- 它能最快帮我们吃透：
  - 为什么按查询维度设计 key
  - 为什么管理端变更后要删缓存
  - 为什么先删整组缓存而不是做精准更新

### 4.3 当前实现的边界

- 当前已完成的是 Day14 的收口版主体，不是整个升级版 Redis 路线的终点
- 但 Day14 本身的收口边界已经重新钉死，不再继续向 Day15 侵占：
  - Day14 收口版负责“缓存工程化”
  - Day15 才继续下探到“Redis 高并发资源竞争”
- 因此 Day14 当前已经收住的内容包括：
  - 分类缓存命中与写后删缓存闭环
  - TTL / 序列化 / Redis 异常兜底
  - 地址簿空值缓存第一版
  - 报表类热点查询结果缓存
  - 一轮可复用到简历和面试里的量化验证
- Day14 暂不继续内卷的内容包括：
  - 更细粒度的 per-key 随机扰动
  - 更复杂的热点 key 击穿保护
  - 更重型的分布式锁 / Lua 资源竞争场景
- 不放在 Day14 内硬追的内容：
  - 布隆过滤器
  - Redis 高并发资源竞争专题
  - MQ 削峰与异步落库

### 4.4 Day14 何时做完整链路验证与复盘

- 不是现在立刻停下来做
- 正确时机是：
  1. 分类缓存与失效链已补齐
  2. TTL / 序列化 / 异常兜底配置已落地
  3. 至少一个“穿透 / 空值缓存 / 异常兜底”点位明确
  4. 至少一个报表类热点查询缓存具备量化对比证据
- 到这些条件满足后，再做 Day14 全链路验证，验证内容包括：
  - API 首次请求查库
  - 二次请求命中 Redis
  - 管理端写操作后缓存失效
  - Redis 中 key 与 TTL 观察
  - MySQL Questions 前后对比
- 然后再写 Day14 主复盘文档

---

## 5. 验证记录

### 5.1 编译验证

- 动作：`mvn -DskipTests compile`
- 预期结果：后端成功编译
- 实际结果：`BUILD SUCCESS`
- 是否通过：通过
- 证据位置：命令行编译输出

### 5.2 API / Redis / 数据库联动验证

- 当前状态：已完成 Day14 收口版验证
- 已完成验证点：
  - 分类接口首次请求查库
  - 分类接口二次请求命中缓存
  - 管理端修改分类状态后缓存失效
  - Redis 中可观察到 `categoryCache::1`
  - Redis 中 TTL 与配置一致
  - 地址簿不存在 id 的空值缓存写入 Redis
  - 地址簿不存在 id 在 TTL 有效期内重复请求时不再打 SQL
  - 报表接口首次请求生成结果缓存
  - 报表接口同区间二次请求命中结果缓存
  - MySQL `Questions` 在首次报表请求后明显上升，在同区间二次请求后仅小幅增长
- 关键证据：
  - Redis key：`categoryCache::1`
  - 实测 TTL：`1192` 秒
  - 后端日志中第二次请求未再出现分类查询日志
  - Redis key：`addressBookByIdCache::4:999999`
  - 首次地址查询返回：`{"code":1,"msg":null,"data":null}`
  - TTL 有效期内重复请求仅保留 JWT 日志，未再出现地址查询日志和 SQL
  - Redis key：`turnoverReportCache::2026-08-01:2026-08-14`
  - Redis key：`turnoverReportCache::2026-08-02:2026-08-13`
  - 老区间响应时间：`0.707692s -> 0.030966s`
  - 新区间响应时间：`0.076508s -> 0.015217s`
  - MySQL `Questions`：`22 -> 43 -> 46`
- 待补验证点：
  - Redis 读取异常的单独证据
  - 是否还需要补一轮更大样本的压测数据

---

## 6. 量化指标记录

### 6.1 本轮指标清单

| 指标项 | 优化前 | 优化后 | 测量方法 | 结论 |
|---|---:|---:|---|---|
| 分类接口单次响应时间 | 首次 `0.037384s` | 二次 `0.019104s` | `curl` + 真实 JWT | 已完成第一轮证明 |
| Redis key 命中情况 | 首次无 key | 已出现 `categoryCache::1` | `redis-cli KEYS` | 已完成第一轮验证 |
| 分类缓存 TTL | 未配置 | `1192` 秒（实测） | `redis-cli TTL categoryCache::1` | 与 20m 配置基本一致 |
| 地址空值缓存写入 | 首次无 key | 已出现 `addressBookByIdCache::4:999999` | `redis-cli KEYS` | 已完成 |
| 地址空值缓存命中 | 反复请求会回库 | TTL 内重复请求不再出现地址 SQL | 后端日志对比 | 已完成第一轮证明 |
| 报表老区间响应时间 | 首次 `0.707692s` | 二次 `0.030966s` | `curl` + 真实管理员 JWT | 已完成 |
| 报表新区间响应时间 | 首次 `0.076508s` | 二次 `0.015217s` | `curl` + 真实管理员 JWT | 已完成 |
| 报表结果缓存写入 | 首次无 key | 已出现 `turnoverReportCache::2026-08-01:2026-08-14` | `redis-cli KEYS` | 已完成 |
| MySQL Questions 增量 | 基线 `22` | 首次后 `43`，二次后 `46` | `SHOW GLOBAL STATUS LIKE 'Questions';` | 趋势已完成 |

### 6.2 原始数据来源

- 压测/验证工具：`curl`、`redis-cli`、`mysql status`
- 测试轮次：
  - 分类缓存链：真实用户 JWT 连续请求
  - 地址簿空值缓存链：真实用户 JWT 连续请求不存在地址
  - 报表结果缓存链：真实管理员 JWT 连续请求同一时间区间
- 测试环境：Docker 运行态
- 数据位置：
  - Redis key / TTL：运行态命令行记录
  - 响应时间：`curl -w` 输出
  - MySQL 压力：`SHOW GLOBAL STATUS LIKE 'Questions';`

### 6.3 当前可直接复用的数字

- 分类接口：`0.037384s -> 0.019104s`
- 报表老区间：`0.707692s -> 0.030966s`
- 报表新区间：`0.076508s -> 0.015217s`
- MySQL `Questions`：`22 -> 43 -> 46`
- Redis key：
  - `categoryCache::1`
  - `addressBookByIdCache::4:999999`
  - `turnoverReportCache::2026-08-01:2026-08-14`
  - `turnoverReportCache::2026-08-02:2026-08-13`

---

## 7. 问题与排查过程

### 7.1 当前已识别但未正式闭环的问题

- 当前还没做 Redis 读取失败的单独证据化验证
- 当前 Day14 重排后，不再把更细粒度的 per-key 随机扰动列为本日必须收口项

### 7.2 本轮已解决的问题

- 解决了分类接口无缓存的问题
- 解决了管理端分类变更后缓存不会失效的问题
- 解决了 Redis 缓存序列化与 `LocalDateTime` 不兼容的问题
- 解决了 TTL 与缓存行为完全由 Spring 默认接管、缺少显式配置的问题
- 解决了 Redis 写缓存失败会拖垮接口的问题
- 解决了“不存在地址被反复请求时会持续回库”的明显穿透场景
- 解决了“同一时间区间报表反复统计会重复按天查库”的热点查询问题

### 7.3 后续排查重点

1. Redis key 是否按设计写入
2. `@Cacheable` 命中后 Controller 日志是否消失
3. 管理端写操作后 Redis 对应 key 是否被清掉
4. TTL 是否按配置生效
5. MySQL Questions 是否出现明显下降
6. 是否要继续把“读取失败回库”证据补到更强
7. 重复请求的时间差是否已经超过短 TTL，避免把“缓存过期后的回库”误判成“缓存未命中”
8. 报表类结果缓存是否需要继续扩展到用户统计、订单统计等其他报表接口

---

## 8. 今日结论（简版）

- 今天最终完成了什么：
  - Day14 文档起稿
  - Day14 第一刀缓存链路落地
  - Redis TTL / 序列化 / 缓存异常兜底第一版落地
  - 地址簿按 id 查询的空值缓存第一版落地
  - 管理端营业额统计结果缓存落地并完成量化验证
- 哪条链路已经跑通：
  - 分类缓存命中链路
  - 分类缓存失效链路
  - 不存在地址 id 的空值缓存链路
  - 报表结果缓存链路
- 哪些验证已经完成：
  - Maven 编译验证
  - Redis key / TTL 验证
  - 管理端写后删缓存验证
  - Redis 短时异常下的缓存写失败兜底验证
  - 地址空值缓存写入与 TTL 内命中验证
  - 报表结果缓存写入、TTL、响应时间与 MySQL 增量趋势验证
- 哪些指标已经拿到：
  - `categoryCache::1`
  - TTL 实测约 20 分钟
  - `addressBookByIdCache::4:999999`
  - 分类接口：`0.037384s -> 0.019104s`
  - `turnoverReportCache::2026-08-01:2026-08-14`
  - `turnoverReportCache::2026-08-02:2026-08-13`
  - 报表接口：`0.707692s -> 0.030966s`
  - 报表接口：`0.076508s -> 0.015217s`
  - MySQL `Questions`：`22 -> 43 -> 46`
- 哪些点最值得写进主文档：
  - Day14 不是重新接 Redis，而是升级缓存工程设计
  - HTTP 层和缓存层可能出现不同的序列化问题
  - 空值缓存不是玄学概念，而是“查不到也短暂缓存”的工程动作
  - 报表结果缓存不是缓存原始表数据，而是缓存“指定时间区间最终算出来的统计结果”
- 哪些内容还需要继续：
  - 更完整的 Redis 读取异常兜底证据
  - 后续更高阶的穿透/击穿/雪崩方案将转入 Day15 之后的增强项

---

## 9. 待同步到主文档的素材

### 9.1 可写进主文档的核心改动

- 用户端分类列表接入 Redis 缓存
- 管理端分类写操作统一失效分类缓存
- 用户端地址簿按 id 查询接入空值缓存第一版
- 管理端营业额统计接入按时间区间划分的结果级缓存

### 9.2 可写进主文档的高价值指标

- 分类接口缓存命中前后时间对比
- 地址簿空值缓存命中前后的日志对比
- MySQL Questions 增量变化
- 报表接口同区间查询前后时间对比

### 9.3 可写进主文档的排障案例

- 待后续验证阶段补

### 9.4 可写进简历/项目话术的句子雏形

- 将 Redis 从“项目中已有”升级成“面向高频读接口的工程化缓存能力”，补齐分类列表缓存与管理端失效链路，为后续 TTL、缓存穿透与性能量化验证打下基础。
- 在用户端地址簿按 id 查询上补了空值缓存第一版，通过短 TTL 缓存不存在地址的查询结果，降低相同无效请求短时间内反复回库的概率。
- 对管理端营业额统计接口增加按时间区间划分的结果级缓存，在 Docker 真实链路下将同区间重复查询从约 `708ms` 降到约 `31ms`，并结合 Redis key、TTL 与 MySQL `Questions` 增量验证缓存命中与数据库压力下降趋势。

---

## 10. 收口补充：高价值提炼

### 10.1 今日高价值交流点

- Day14 不是重新学一次 Redis，而是把项目里已有的缓存能力升级成一个可讲高并发读优化的工程专题
- 当前项目不是“完全没缓存”，而是“缓存做了一半”，所以 Day14 应该基于真实代码补齐缺口，而不是从 0 重新设计一套脱离项目现状的方案
- 分类缓存之所以适合作为第一刀，是因为它能最清楚地帮助我们吃透 key 设计、缓存命中和写后删缓存的一致性逻辑
- Redis 配置层也是业务能力的一部分，TTL、序列化和异常兜底都属于缓存工程能力，而不是“配一下就行”的边角料
- 空值缓存第一版的价值，不在于“把 null 也塞进 Redis”这句话本身，而在于它能真实挡住短时间内重复请求不存在数据的回库压力
- 报表缓存比普通列表缓存更像真实后端优化题，因为它优化的是“重复统计计算”而不是“重复查一份原始列表”
- 项目卖点不该只停留在“用了什么技术”，更该沉淀成“遇到了什么问题、为什么这样设计、最后指标怎么变好”

### 10.2 今日高价值问题 / 踩坑点

- 缓存真正难的地方不在 `@Cacheable` 注解本身，而在 key 粒度和写操作后的失效策略
- 如果只补读缓存、不补管理端写后失效，系统一定会出现用户端读脏数据的问题
- 规划文档如果不和当前真实执行路线同步，很容易造成“代码已经在做 A，文档还停留在泛泛的 B”这种后续复盘混乱
- HTTP 返回能正常序列化，不代表 Redis 缓存层也一定能正常序列化；时间类型是非常典型的分层序列化坑
- “想当然以为框架会自动接管”也很危险，像 `CacheErrorHandler` 这种能力要确认它真的挂到了缓存切面上
- 缓存验证不能只看响应时间快没快；时间受冷启动、连接状态、TTL 是否已过期等因素影响，真正硬的证据是 Controller 日志和 SQL 是否消失
- `MySQL Questions` 这类指标也不能机械套公式，它更适合看前后增量趋势，而不是直接把某一次绝对值等同于某一条业务 SQL 数量
- 如果一边推进一边说“第一版第二版”，但没有先钉死收口边界，会让人误以为今天只完成了全部任务里的很小一部分，这是路线表达上的真问题

### 10.3 今日有必要整理但暂未展开的点

- TTL、随机过期扰动和 RedisCacheManager 的统一配置需要在下一轮实现中补齐
- Day14 正式复盘时，要专门整理“当前方案如何对应缓存穿透、雪崩、异常兜底”这组高频面试问题
- 后面可以继续考虑把“读取异常回库”的证据做得更强，比如更严格地区分 get 失败与 put 失败
- 当前空值缓存还是第一版，只覆盖了“明显不存在数据的短 TTL 缓存”，还没有上更强的布隆过滤器方案；但这已经不再作为 Day14 收口阻塞项
- Day15 的 Redis 高并发资源竞争专题，需要继续坚持“能实现、能验证、能讲”的边界，不为了表面上的技术名词堆砌去硬开新业务坑

### 10.4 今日最值得反复记住的一句话

- Day14 的第一刀不是为了多加一个缓存注解，而是为了先把“高频读接口为什么适合缓存，以及写后为什么必须删缓存”这条工程主线走明白。
- 判断缓存有没有命中，优先看“方法体和 SQL 还在不在”，不要先被单次耗时数字带偏。
- 面试里更有价值的表达，不是“我用了 Redis”，而是“我面对一个重复读/重复算的问题，为什么选择用 Redis，以及它把指标变成了什么样”。 

---

## 11. 证据清单

| 类型 | 文件名/位置 | 对应内容 |
|---|---|---|
| 文档 | [Day14-开发过程性文档.md](file:///D:/ProgramFiles/CodeProjects/PeakSend/docs/Day14-%E5%BC%80%E5%8F%91%E8%BF%87%E7%A8%8B%E6%80%A7%E6%96%87%E6%A1%A3.md) | Day14 过程留痕主文件 |
| 文档 | [Day14-变更细节及详细逻辑链路说明.md](file:///D:/ProgramFiles/CodeProjects/PeakSend/docs/Day14-%E5%8F%98%E6%9B%B4%E7%BB%86%E8%8A%82%E5%8F%8A%E8%AF%A6%E7%BB%86%E9%80%BB%E8%BE%91%E9%93%BE%E8%B7%AF%E8%AF%B4%E6%98%8E.md) | Day14 主复盘文档 |
| 代码 | [user/CategoryController.java](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-server/src/main/java/com/sky/controller/user/CategoryController.java) | 用户端分类缓存入口 |
| 代码 | [admin/CategoryController.java](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-server/src/main/java/com/sky/controller/admin/CategoryController.java) | 管理端分类缓存失效入口 |
| 代码 | [RedisConfiguration.java](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-server/src/main/java/com/sky/config/RedisConfiguration.java) | Redis 序列化、TTL 与异常兜底配置 |
| 代码 | [CacheTtlProperties.java](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-server/src/main/java/com/sky/properties/CacheTtlProperties.java) | 各类缓存 TTL 配置 |
| 代码 | [AddressBookController.java](file:///D:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-server/src/main/java/com/sky/controller/user/AddressBookController.java) | 地址簿空值缓存第一版入口 |
| 日志 | Maven 编译输出 | Day14 第一刀编译通过证据 |
| 日志 | Docker backend / redis 运行日志 | 缓存命中、失效、异常兜底验证证据 |
