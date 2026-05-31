# Day7-变更细节及详细逻辑链路说明

## 1. Day7 这一阶段到底做了什么

Day7 当前真正完成的主题是：

- 商品缓存

如果只看课程标题，Day7 常常会被表述成：

- 缓存
- 购物车

但结合当前仓库的真实进度，应该更准确地说：

- 购物车基础链在 Day6 联调阶段已经提前打通
- Day7 真正新增且最有工程含金量的部分，是用户端商品缓存

本次真实完成并验证通过的链路包括：

- Spring Cache 接入
- Redis 作为商品缓存介质接入用户端查询链
- 用户端菜品按分类缓存
- 用户端套餐按分类缓存
- 管理端菜品变更后清理菜品缓存
- 管理端套餐变更后清理套餐缓存
- Redis 鉴权、配置、联调问题排查
- APIFox 手工联调验证缓存命中与缓存失效

## 2. 为什么 Day7 的核心不只是“加个 Redis”

Day5 虽然已经把 Redis 接进项目，但那次 Redis 承担的是：

- 店铺营业状态这种全局轻量状态

到了 Day7，Redis 的定位发生了明显变化：

- 不只是存一个全局状态
- 而是开始缓存高频读业务的查询结果

这意味着 Day7 的重点已经从：

- 读写一个简单 key

转向：

- 哪些接口适合缓存
- 缓存 key 按什么维度设计
- 命中缓存时为什么不再进方法体
- 管理端修改数据后怎么保证缓存一致性

所以 Day7 最重要的能力，不是“会用注解”，而是：

- 理解高频读接口为什么适合缓存
- 理解缓存命中与数据库查询的关系
- 理解缓存失效与业务变更的一致性设计

## 3. 当前 Day7 的范围到底到哪里

这次必须先把边界说清楚，不然又容易把后续订单主线混进来。

本次 Day7 已完成的内容是：

- 菜品缓存
- 套餐缓存
- 缓存失效
- 缓存命中联调验证

这次没有新增推进的内容包括：

- 地址簿
- 提交订单
- 支付
- 历史订单

这些都不属于本次 Day7 收尾范围。

## 4. Day7 的核心代码结构是怎么搭起来的

本次和缓存直接相关的代码主要集中在以下文件：

### 缓存能力开启

- `peaksend-backend/sky-server/src/main/java/com/sky/SkyApplication.java`

### 用户端缓存入口

- `peaksend-backend/sky-server/src/main/java/com/sky/controller/user/DishController.java`
- `peaksend-backend/sky-server/src/main/java/com/sky/controller/user/SetmealController.java`

### 管理端缓存失效入口

- `peaksend-backend/sky-server/src/main/java/com/sky/controller/admin/DishController.java`
- `peaksend-backend/sky-server/src/main/java/com/sky/controller/admin/SetmealController.java`

### Redis 配置与环境

- `peaksend-backend/sky-server/src/main/resources/application.yml`
- `peaksend-backend/sky-server/src/main/resources/application-dev.yml`

### 相关理解链

- `peaksend-backend/sky-server/src/main/java/com/sky/interceptor/JwtTokenUserInterceptor.java`
- `peaksend-backend/sky-server/src/main/java/com/sky/config/WebMvcConfiguration.java`
- `peaksend-backend/sky-common/src/main/java/com/sky/context/BaseContext.java`
- `peaksend-backend/sky-common/src/main/java/com/sky/properties/JwtProperties.java`

## 5. 菜品缓存这条链到底是怎么走的

菜品缓存用户端入口在：

- `GET /user/dish/list?categoryId=16`

整体链路可以压缩成：

1. 前端点击左侧分类，比如“蜀味烤鱼”
2. 前端发起 `/user/dish/list?categoryId=16`
3. Spring MVC 先让请求通过用户端 JWT 拦截器
4. 拦截器从 `authentication` 请求头里取 token
5. 后端解析 token，得到本地 `userId`
6. Controller 上的 `@Cacheable` 先按 `categoryId` 查 Redis
7. 如果命中 `dishCache::16`，直接返回缓存结果
8. 如果没命中，才执行方法体
9. 方法体按 `categoryId + status=ENABLE` 查询菜品
10. Service 层继续查询每个菜品的口味数据
11. 返回结果后，Spring Cache 自动把结果写入 Redis

这里一定要分清 3 个概念：

- `categoryId`：前端当前点击的分类 id
- `dishCache::16`：后端根据 `categoryId=16` 推导出的缓存 key
- `Result<List<DishVO>>`：最终被缓存起来的整份分类菜品列表结果

## 6. 为什么菜品缓存按 categoryId 设计 key

这次最容易抽象化，但也最值得记住的点就是：

- 缓存 key 的设计要跟接口查询维度保持一致

用户端菜品接口问的问题不是：

- “给我 dishId=67 这一道菜”

而是：

- “给我 categoryId=16 这个分类下所有起售菜品”

所以缓存自然不该按 `dishId` 设计，而应该按：

- `categoryId`

也就是说，这次缓存的不是单个菜品，而是：

- 某个分类下的一整份菜品列表结果

## 7. 为什么缓存里天然只有起售中的菜品和套餐

这次很多理解卡点都集中在这里。

真正的机制不是：

- 先查全部
- 再把停售数据从缓存里剔掉

而是：

- 查询数据库时就带上 `status = ENABLE`
- 返回结果天然只有起售中的数据
- Spring Cache 缓存的就是这个查询结果

所以：

- 用户端只展示起售中的菜品和套餐
- 缓存里也天然只会出现起售中的菜品和套餐

## 8. 为什么第二次请求只剩 JWT 日志

这次联调里拿到的日志证据非常关键。

第一次请求同一分类时，可以看到：

- 用户端 JWT 校验日志
- Controller 里的分类查询日志
- `DishMapper.list`
- `DishFlavorMapper.getByDishId`

第二次请求时，只剩下：

- 用户端 JWT 校验日志

但 Controller 日志和 SQL 都消失了。

这说明：

- JWT 拦截器仍然会先执行，因为它发生在 Controller 之前
- 但缓存已经命中，所以 Controller 方法体根本没有继续执行

也就是说，Spring Cache 是在：

- 请求真正进入方法体之前

就已经把缓存命中这件事处理掉了。

## 9. 管理端改一个菜品，为什么用户端下一次又要回库

这次管理端菜品起售/停售接口上用了：

- `@CacheEvict(cacheNames = "dishCache", allEntries = true)`

它的意思不是“更新数据库”，而是：

- 这个方法成功执行后，把 `dishCache` 整组清掉

所以你在管理端调：

- `POST /admin/dish/status/1?id=67`

真实发生的是两件事：

1. 更新数据库里的菜品状态
2. 清空 `dishCache`

因此用户端下一次再查：

- `/user/dish/list?categoryId=16`

就无法再命中 Redis，只能重新回库查询并重建缓存。

## 10. 套餐缓存和菜品缓存的关系是什么

这次一个非常有价值的认识是：

- 套餐缓存和菜品缓存不是两套完全不同的设计
- 它们本质上是同一个“按分类缓存列表”的模板

共性包括：

- 都是用户端分类列表查询
- 都按 `categoryId` 做缓存 key
- 都只查起售中的数据
- 都是第一次查库、第二次命中缓存
- 管理端变更后都通过 `@CacheEvict` 清理对应缓存

两者最大的区别主要在：

- 菜品缓存首次查询更重，因为还要把口味数据带出来
- 套餐缓存列表查询相对更轻，主要是套餐主表列表

此外，套餐这次联调里还暴露出一个真实业务点：

- 用户端查不到套餐，不一定是缓存问题
- 也可能只是该套餐当前 `status = 0`

## 11. JWT 拦截和缓存在请求链里分别负责什么

这次 Day7 虽然主题是缓存，但真正理解这条链时，不可避免要把 JWT 拦截一起补平。

要分清：

### JWT 拦截器负责什么

- 判断当前请求是不是合法已登录用户发来的
- 从请求头中取 token
- 用配置好的密钥解析 token
- 取出 `userId`
- 放到 `BaseContext`

### 缓存负责什么

- 决定本次请求的数据是从 Redis 返回，还是从 MySQL 查询

也就是说：

- JWT 拦截器解决的是“你是谁”
- 缓存解决的是“数据从哪来”

## 12. 这次 Redis 环境问题到底踩了哪些坑

本次 Day7 联调里，Redis 不只是“接上就完了”，而是经历了一轮非常真实的环境排查。

踩到的关键问题包括：

- Redis 在 Ubuntu 虚拟机里运行，Windows 侧后端通过端口转发访问
- `protected-mode` 一开始只是临时关闭，不是永久配置
- 系统服务版 Redis 启动失败，不是端口问题，而是旧 `dump.rdb` 和当前 Redis 版本不兼容
- Redis 开启了 `requirepass`，但 Spring Boot 一开始没有配 Redis 密码

最终这次真正搞清楚了两个工程结论：

- “Redis 自己能 `PONG`” 不等于“项目已经能连上 Redis”
- 项目是否真正接通 Redis，最终要看用户端或管理端接口能否正常调用依赖 Redis 的链路

## 13. APIFox 联调中真正学到的是什么

这次联调不只是点接口成功，而是顺手补齐了几个非常实战的工程认知：

- 用户端和管理端必须分开维护 token
- 管理端请求头名是：
  - `token`
- 用户端请求头名是：
  - `authentication`
- APIFox 模块变量要放在对应模块作用域里，否则变量会发红、不生效
- 变量名和真实请求头名不是一回事

这次最终整理出的最稳做法是：

- 管理端模块保存 `adminToken`
- 用户端模块保存 `userToken`
- 调接口时分别用：
  - `token: {{adminToken}}`
  - `authentication: {{userToken}}`

## 14. Day7 最值得带走的工程理解

如果要压缩成最值钱的能力点，Day7 练到的是：

- 不是所有高频读接口都应该每次直查数据库
- 缓存 key 设计必须跟查询维度一致
- 缓存命中不等于跳过鉴权
- 缓存真正难点不在“加缓存”，而在“数据变更后如何保证一致性”

而这次项目里选择的策略是：

- 用户端查询按分类做缓存
- 管理端变更时清整组缓存
- 先保证正确和一致性，再考虑更细粒度优化

## 15. Day7 当前收尾结论

到目前为止，Day7 已经真实完成并联调通过的内容可以表述为：

- 菜品缓存链打通
- 套餐缓存链打通
- Redis 环境和密码配置打通
- 管理端缓存失效链打通
- APIFox 用户端/管理端双 token 联调模型打通

如果要一句话总结 Day7，可以这样说：

- Day7 我不只是把 Redis 接进了项目，而是真正把用户端高频商品查询升级成了“JWT 鉴权 + 分类维度缓存 + 管理端变更后自动失效”的完整工程链路。
