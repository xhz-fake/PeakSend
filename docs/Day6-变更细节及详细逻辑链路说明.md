# Day6-变更细节及详细逻辑链路说明

## 1. Day6 这一阶段到底做了什么

Day6 的正式主题是：

- 用户登录与用户端商品浏览

但这一天真正做下来的内容，不只是“把几个接口补出来”。

这一天第一次把项目主线从：

- 管理端

切到了：

- 用户端

也就是说，我们开始真正处理一条更贴近真实外卖场景的 C 端业务链：

- 微信小程序登录
- 用户身份识别
- 首页分类和菜品浏览
- 规格菜展示
- 基础购物车交互

最终这次真正打通并验证通过的主链是：

- 小程序获取 `code`
- 后端换取 `openid`
- 本地用户查找/自动注册
- 用户端 JWT 签发
- 用户端拦截器识别当前用户
- 首页分类和菜品查询
- 购物车加购、减购、清空

## 2. 为什么 Day6 很重要

Day3、Day4 更多是在练：

- 标准后台 CRUD
- 多表联动
- 事务
- 动态 SQL

Day5 开始练：

- Redis
- 全局共享状态

而 Day6 的重点第一次变成了：

- 第三方登录思路
- 用户态 Token
- 用户端请求鉴权
- 从“微信身份”转换到“本地系统身份”

这意味着我们第一次不再只关注：

- 数据库里怎么增删改查

而是开始真正理解：

- 小程序和后端是怎么建立登录关系的
- 后端后续是怎么识别“当前用户是谁”的
- 为什么后续业务不能相信前端自己传来的 `userId`

## 3. Day6 的范围到底到哪里

这次对范围必须重新说清楚，因为开发过程中一度把订单链也推进了，后来又确认那其实已经越界了。

按照总地图，Day6 的标准目标是：

- 用户登录
- 用户端商品浏览

而：

- 购物车

严格来说更偏 Day7。

但是本次联调过程中，购物车基础能力也被一起补通并验证通过了，所以实际成果可以表述为：

- Day6 正式目标全部完成
- 并提前触达了 Day7 的基础购物车链

明确不属于本次 Day6 验收标准的内容有：

- 地址簿
- 提交订单
- 支付
- 历史订单

这些属于更后续的订单主线，不能再拿它们来判断 Day6 是否完成。

## 4. Day6 的核心代码结构是怎么搭起来的

这次用户端主链，核心新增/修改的代码主要集中在以下几类文件：

### 登录与鉴权

- `sky-server/src/main/java/com/sky/controller/user/UserController.java`
- `sky-server/src/main/java/com/sky/service/UserService.java`
- `sky-server/src/main/java/com/sky/service/impl/UserServiceImpl.java`
- `sky-server/src/main/java/com/sky/interceptor/JwtTokenUserInterceptor.java`
- `sky-server/src/main/java/com/sky/config/WebMvcConfiguration.java`
- `sky-server/src/main/resources/application.yml`
- `sky-server/src/main/resources/application-dev.yml`

### 用户端浏览

- `sky-server/src/main/java/com/sky/controller/user/CategoryController.java`
- `sky-server/src/main/java/com/sky/controller/user/DishController.java`
- `sky-server/src/main/java/com/sky/controller/user/SetmealController.java`

### 购物车

- `sky-server/src/main/java/com/sky/controller/user/ShoppingCartController.java`
- `sky-server/src/main/java/com/sky/service/ShoppingCartService.java`
- `sky-server/src/main/java/com/sky/service/impl/ShoppingCartServiceImpl.java`
- `sky-server/src/main/java/com/sky/mapper/ShoppingCartMapper.java`
- `sky-server/src/main/resources/mapper/ShoppingCartMapper.xml`

### 小程序联调侧

- `miniProgram/project-rjwm-weixin-uniapp-develop-wsy/utils/env.js`
- `miniProgram/project-rjwm-weixin-uniapp-develop-wsy/utils/request.js`
- `miniProgram/project-rjwm-weixin-uniapp-develop-wsy/pages/api/api.js`
- `miniProgram/project-rjwm-weixin-uniapp-develop-wsy/pages/index/index.js`

## 5. 微信登录这条链到底是怎么走的

这一段是 Day6 最值得真正讲清楚的部分。

整体流程可以压缩成：

1. 小程序调用 `uni.login()` 获取临时 `code`
2. 前端把 `code` 发给后端登录接口
3. 后端拿 `code + appid + secret` 去调微信接口
4. 微信返回这个用户在当前小程序下对应的 `openid`
5. 后端用 `openid` 查询本地 `user` 表
6. 查到则直接登录，查不到则自动注册
7. 后端把本地 `user.id` 放进 JWT
8. 前端后续带着 token 请求用户端接口
9. 拦截器从 token 中解析 `userId`
10. 业务代码通过 `BaseContext` 获取当前用户

这里一定要分清 5 个概念：

- `AppID`：当前小程序自己的身份标识
- `code`：前端临时拿到的一次性登录凭证
- `openid`：微信体系里这个用户在当前小程序下的稳定标识
- `id`：我们自己数据库 `user` 表主键
- `token`：后端签发给前端的后续访问通行证

## 6. 为什么 token 里放的是 user.id

这是 Day6 登录链最容易糊涂但最值得记住的点。

登录成功后，后端不会把：

- `code`

放进 token。

也不会直接拿前端后续再传一个：

- `userId`

来做业务。

真正的做法是：

- 后端根据 `openid` 找到本地 `user`
- 再把本地 `user.id` 放进 token

原因是：

- `code` 只是一次性临时凭证，不能作为后续稳定身份
- `openid` 是微信体系里的身份标识，但业务系统后续还是围绕本地用户主键更方便
- `user.id` 更适合和购物车、地址、订单这些业务表做关联

所以这次真正完成的是一件很关键的事：

- 把“微信身份”转换成“本地系统身份”

## 7. 用户端拦截器为什么会自动执行

这次 Day6 还第一次比较清楚地碰到了一个重要框架概念：

- 框架回调

`JwtTokenUserInterceptor` 里的 `preHandle()` 并不是业务代码手写去调用的。

真正的机制是：

1. Spring Boot 启动时，在 `WebMvcConfiguration` 中注册拦截规则
2. 运行过程中，Spring MVC 收到匹配 `/user/**` 的请求
3. 如果不在白名单里，就自动回调拦截器的 `preHandle()`
4. 返回 `true` 才放行，返回 `false` 就拦下

所以这次一定要建立一个意识：

- 不是所有执行的方法，代码里都能看到手动调用
- 很多方法是被 Spring 在生命周期里自动回调的

## 8. 为什么后端不能相信前端传 userId

这次在购物车链路里，一个非常重要的设计点是：

- 当前用户身份不能依赖前端直接传参

正确做法是：

- 前端只带 token
- 后端拦截器自己解析 token
- 从中取出可信的 `userId`
- 通过 `BaseContext.setCurrentId(userId)` 在本次请求处理过程中共享
- 后续业务代码统一用 `BaseContext.getCurrentId()`

这样做的原因是：

- 如果允许前端直接传 `userId`
- 用户就可以伪造身份
- 进而查看甚至修改别人的购物车、订单等数据

所以这次不只是写通了功能，更进一步理解了：

- 为什么“当前用户是谁”必须由后端确认，而不是由前端决定

## 9. 首页浏览和购物车链路是怎么接起来的

这次用户端页面真正联调跑通后，主线顺序大致是：

1. 先查询店铺营业状态
2. 再初始化分类列表
3. 再按分类查询菜品
4. 菜品如果有口味，则前端弹出规格选择
5. 用户点加号后，请求购物车接口
6. 后端先从 `BaseContext` 拿当前用户
7. 再按用户、菜品/套餐、口味做判重
8. 命中则数量加一，未命中则新增一条购物车记录

这意味着购物车里的“同一项”，并不是简单指：

- 同一道菜

而是更准确地指：

- 同一个用户点的同一道菜的同一种口味

## 10. 购物车判重为什么不能只看 dishId

这是 Day6 提前触达 Day7 时最有业务味的一段代码。

比如同一个用户先点：

- 鱼香肉丝 + 微辣

又点：

- 鱼香肉丝 + 不辣

如果只按 `dishId` 判重，就会错误地把这两条合并成：

- 一条记录，数量 `+1`

但这显然不对，因为：

- 口味不同，代表用户点的是两种不同配置
- 后面购物车展示会错
- 减购会错
- 下单明细也会错

所以这次真正理解到的判重维度是：

- `userId + dishId/setmealId + dishFlavor`

## 11. 购物车加购和减购的业务规则

### 加购

逻辑是：

1. 把前端传来的菜品/套餐和口味信息拷到购物车条件对象
2. 再补上当前用户 `userId`
3. 先查库判断购物车里是否已有同一项
4. 如果有，则数量 `+1`
5. 如果没有，则查询菜品表或套餐表，补齐名称、图片、金额
6. 初始化数量为 `1`
7. 插入新购物车记录

### 减购

逻辑是：

1. 同样先按用户、菜品/套餐、口味查到目标购物车项
2. 如果查不到，直接结束
3. 如果数量大于 `1`，就数量 `-1`
4. 如果数量已经是 `1`，则直接删除这条购物车记录

这里有一个很重要的工程化意识：

- 购物车里不应该保留 `number = 0` 的脏数据

因此最后一份减掉时，应该：

- `delete`

而不是：

- `update number = 0`

## 12. Day6 遇到的真实问题和排查顺序

这次 Day6 最大的价值之一，不只是把功能写出来，而是经历了一次比较完整的联调和排错过程。

### 第一个问题：小程序还在打旧的 cpolar 地址

现象：

- 点击登录后，后端控制台毫无反应

最后定位到：

- 当前真正运行的是 `unpackage/dist/dev/mp-weixin`
- 修改源码 `env.js` 不等于当前运行产物立刻生效

最终通过修正运行产物里的真实请求地址，才让请求重新打回本地后端。

### 第二个问题：Redis 连接失败

现象：

- `/user/shop/status` 返回 500

最后从后端日志定位到：

- Redis 连不上

解决过程包括：

- 启动 Redis
- 验证 `PING -> PONG`
- 处理 `protected-mode`

这里再次验证了一个原则：

- 请求 500 不一定是 Controller 写错，很可能是中间件或环境层问题

### 第三个问题：微信登录报 invalid appsecret

现象：

- 微信接口返回 `40125`
- `invalid appsecret`

最后定位到：

- 当前 `secret` 和真实 `appid` 不匹配

更换正确的 `AppSecret` 后，登录链恢复正常。

### 第四个问题：登录成功后继续出现多个 404

现象：

- 分类接口 404
- 购物车接口 404
- 菜品接口 404

这里很容易误以为“是不是越来越坏了”。

但实际上这恰恰说明：

- 登录已经打通
- 前端开始继续往下执行首页初始化链
- 所以才会把后面缺失的用户端接口一层层暴露出来

这也是联调里一个很重要的观察点：

- 新报错不一定代表退步，也可能代表主链更往后走了

## 13. 一次越界推进带来的教训

这次开发过程中，一度错误地把地址簿、订单提交和支付页也推进了。

后来重新对照总地图，才明确：

- Day6 不是做订单链
- Day6 的标准收口是登录和浏览，最多加上基础购物车

这次带来的教训非常重要：

- 项目推进必须严格对照阶段目标
- 不能因为前端页面已经存在，就机械地往后硬补
- 否则不仅会让联调复杂度激增，还会打乱复盘节奏

## 14. Day6 到底算不算完成

按总地图标准，Day6 已经可以判定：

- 完成

原因是：

- 用户登录链打通
- 用户态 JWT 打通
- 用户端分类和菜品浏览打通
- 规格菜展示打通
- 小程序和本地后端联调打通

而且这次还额外完成了：

- 购物车查看
- 购物车加购
- 购物车减购
- 购物车清空

这部分严格来说已经是提前进入 Day7 的基础能力了。

## 15. Day6 最值得沉淀的 8 个理解点

如果现在只记最关键的 8 个点，优先记这些：

- `AppID` 是小程序自己的身份标识，不是用户身份
- `code` 是一次性临时登录凭证
- `openid` 是微信体系中用户在当前小程序下的稳定标识
- 本地业务最终围绕的是 `user.id`，不是 `code`
- token 里保存的是本地 `userId`
- 拦截器是 Spring MVC 自动回调执行的，不是手写调用
- 当前用户身份必须由后端基于 token 解析确认，不能相信前端自己传 `userId`
- 购物车判重必须至少基于 `userId + 菜品/套餐 + 口味`

## 16. Day6 可以怎么总结

可以用一段比较完整的话来概括：

- Day6 我们第一次从管理端切换到用户端，真正打通了微信小程序登录到后端身份识别的完整链路。这个阶段最关键的不是单个接口，而是理解了 `code -> openid -> user.id -> token -> 拦截器 -> BaseContext` 这条用户身份链，同时也跑通了首页商品浏览和基础购物车交互。它让我第一次比较完整地理解了小程序联调、第三方登录、用户态鉴权和用户端业务处理是怎么串起来的。
