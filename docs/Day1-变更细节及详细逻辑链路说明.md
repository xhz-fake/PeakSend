# Day1-变更细节及详细逻辑链路说明

## 1. Day1 的目标

Day1 的核心不是做很多业务功能，而是把整个项目的开发链路跑通，为后续 11 天打基础。

Day1 我们已经完成的事情可以概括成四条主线：

- Git 和 GitHub 仓库初始化与结构理顺
- 本地开发环境跑通
- 后端登录与新增员工功能打通
- APIFox / Swagger 调试链路建立

## 2. Day1 我们实际做了什么

### 2.1 Git 仓库结构理顺

我们解决了根目录仓库与 `peaksend-backend` 子目录重复初始化 Git 的问题，最终目标是让整个 `PeakSend` 根目录只有一个 Git 仓库。

这一部分的关键理解点：

- 根目录只能有一个 `.git`
- 子目录如果也有 `.git`，根仓库会把它视作另一个独立仓库
- 这会导致子目录代码无法被根仓库正常追踪

### 2.2 Maven 工程名与模块名修正

后端原来的 Maven 父工程名带有旧课程项目名称，后续被统一修正为 `peaksend-backend`，避免 IDEA 和 Maven 面板一直显示旧名字。

这一部分的关键理解点：

- IDEA 左侧模块显示和 Maven 面板显示，本质上都受 `pom.xml` 的工程信息影响
- Maven 工程信息不改，IDEA 里就会一直残留旧名字

### 2.3 JDK / Lombok / 编译环境问题排查

我们解决了 `JDK 23` 与旧版 Lombok 不兼容的问题，并将 Lombok 升级为更适合 `JDK 17` 的版本。

关键文件：

- [pom.xml](file:///d:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/pom.xml)

这一部分的关键理解点：

- 项目“能不能编译”不只取决于代码本身
- 还取决于 JDK、Lombok、IDEA 项目 SDK、Maven Runner JRE 是否一致
- 构建失败时要先区分：是代码逻辑问题，还是环境兼容问题

### 2.4 数据库连接问题排查

我们排查并修复了 MySQL 连接问题，包括：

- 用户名密码问题
- YAML 密码值解析问题
- 数据库服务实例识别

这一部分的关键理解点：

- 后端连不上数据库时，要区分是“认证失败”还是“端口不可达”
- 同一台电脑可能同时装多个 MySQL 实例，配置和实际连接的实例可能不是同一个

### 2.5 前端与后端端口关系理清

我们明确了：

- 后端 Spring Boot 在 `8080`
- Nginx 前端入口在 `8081`

这一部分的关键理解点：

- `localhost:8080` 是后端接口服务
- `localhost:8081` 是前端访问入口
- Nginx 通过反向代理把前端请求转发给后端

### 2.6 APIFox 与 Swagger 调试链路建立

我们完成了以下调试链路：

- 导入课程提供的接口文档到 APIFox
- 确认 `http://localhost:8080/v2/api-docs` 可访问
- 认识到静态 YApi JSON 与代码注解生成的 Swagger 文档不是同一套数据源
- 后续采用 Swagger URL 导入方式，让 APIFox 能看到代码注解变化

关键理解点：

- APIFox 可以做接口文档、接口调试、环境管理
- Swagger / Knife4j 负责根据代码注解生成接口文档
- 代码里新增 `@Api(tags = "...")` 后，只有重新导入 Swagger 文档，APIFox 才能同步看到

### 2.7 员工登录功能跑通

关键文件：

- [EmployeeController](file:///d:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-server/src/main/java/com/sky/controller/admin/EmployeeController.java)
- [EmployeeServiceImpl](file:///d:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-server/src/main/java/com/sky/service/impl/EmployeeServiceImpl.java)

我们已经验证登录接口可以在 APIFox 中成功返回 token。

这一部分的逻辑链路如下：

1. 前端或 APIFox 向 `/admin/employee/login` 发送用户名密码
2. Controller 接收 `EmployeeLoginDTO`
3. Service 根据 `username` 查询员工信息
4. 对前端明文密码做 MD5
5. 与数据库中的加密密码比对
6. 校验账号状态是否正常
7. 登录成功后生成 JWT
8. 封装 `EmployeeLoginVO`
9. 返回统一响应 `Result.success(...)`

这一条链路非常重要，因为它贯穿了后面几乎所有管理端接口的鉴权思路。

### 2.8 新增员工功能跑通

关键文件：

- [EmployeeController](file:///d:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-server/src/main/java/com/sky/controller/admin/EmployeeController.java)
- [EmployeeServiceImpl](file:///d:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-server/src/main/java/com/sky/service/impl/EmployeeServiceImpl.java)
- [GlobalExceptionHandler](file:///d:/ProgramFiles/CodeProjects/PeakSend/peaksend-backend/sky-server/src/main/java/com/sky/handler/GlobalExceptionHandler.java)

最关键的修复点是：

- Controller 的新增员工接口补上了 `@RequestBody`

如果不加这个注解，APIFox 发送 JSON 请求体时，后端接收到的 `EmployeeDTO` 会全部是 `null`。

新增员工的逻辑链路如下：

1. APIFox 发送 `POST /admin/employee`
2. Header 携带登录 token
3. Controller 通过 `@RequestBody` 接收 `EmployeeDTO`
4. Service 先做重复用户名检查
5. 把 DTO 拷贝到 Employee 实体
6. 设置默认状态、默认密码、创建时间、修改时间、创建人、修改人
7. 调用 Mapper 插入数据库
8. 返回统一响应 `Result.success()`

### 2.9 防止重复新增员工

我们已经为新增员工增加了两层保护：

- 业务层主动按 `username` 查重
- 全局异常处理兜底数据库唯一约束异常

关键理解点：

- “查重”可以做在业务层，也可以依赖数据库唯一索引
- 更稳的做法是两层都做
- 业务层负责给出更清晰的业务提示
- 数据库唯一索引负责兜底，防止并发或漏判

## 3. Day1 重点代码逻辑串

### 3.1 登录链路

`APIFox -> EmployeeController.login() -> EmployeeServiceImpl.login() -> EmployeeMapper.getByUsername() -> 密码校验 -> JWT 生成 -> Result.success(EmployeeLoginVO)`

### 3.2 新增员工链路

`APIFox -> JwtTokenAdminInterceptor -> EmployeeController.save() -> EmployeeServiceImpl.save() -> EmployeeMapper.insert() -> Result.success()`

### 3.3 重复校验链路

`APIFox -> save() -> getByUsername() 查重 -> 已存在则抛 BaseException -> GlobalExceptionHandler 返回统一错误结果`

## 4. Day1 最重要的理解成果

- 我们不只是“把项目跑起来”，而是已经打通了从接口文档到后端实现、从 APIFox 到数据库、从登录到鉴权的完整开发链路
- 我们已经建立了后面每天都要反复使用的基本开发套路
- 后面每做一个功能，都可以用 Day1 建立起来的这套套路继续复制

## 5. Day1 还存在的已知问题

- `createUser` 与 `updateUser` 目前仍是写死值，后续需要改为从当前登录用户上下文中获取
- 管理端很多前端页面会请求尚未实现的接口，因此登录后仍会看到一些 404 警告
- 项目目前只完成了员工模块的起步功能，后面还有大量业务模块待开发

## 6. Day1 复盘结论

Day1 最重要的意义不是“实现了几个接口”，而是搭起了后面所有开发工作的脚手架：

- 工程能跑
- 文档能看
- 接口能调
- 登录能通
- 鉴权能用
- 数据能落库
- 异常能返回统一结果

这意味着我们已经正式进入“可以持续开发”的状态。

## 7. Day1 沉淀出的高价值经验

- 不要把“项目能跑”误认为“自己已经会了”，真正的掌握标准是能把一条业务链路从请求讲到数据库
- 初学阶段最容易忽略的不是代码语法，而是环境、端口、JDK、依赖版本、数据库实例这些“非业务错误”
- 接口调试一定要学会读日志，尤其要能看懂 400、404、500、参数为 null、SQL 执行参数这些信号
- APIFox、Swagger、后端日志、数据库查询三者必须联动使用，不能只盯一个地方
- 后端开发中“请求到了没、参数对不对、SQL 执行了没、数据库变了没”是最基础也是最重要的四连问

## 8. Day1 常见问题与正确理解

### 8.1 为什么 APIFox 里有接口，但调用是 404

因为接口文档列的是“目标能力”，不代表当前代码已经全部实现。  
文档中存在但代码里还没写的接口，请求时自然返回 404。

### 8.2 为什么新增员工时前端明明传了值，后端却全是 null

因为 Controller 参数缺少 `@RequestBody`，导致 JSON 请求体没有正确绑定到 DTO。

### 8.3 为什么我改了数据库后，登录结果还是不符合预期

因为要先区分你改的是：

- `username`：登录账号
- `name`：显示姓名

登录使用的是 `username`，不是 `name`。

### 8.4 为什么浏览器访问的是 8081，而后端配置写的是 8080

因为：

- `8080` 是 Spring Boot 后端服务
- `8081` 是 Nginx 前端入口

这两个端口分别服务于不同层次，不冲突也不矛盾。

### 8.5 为什么改了代码里的 Swagger 注解，APIFox 没有自动变化

因为 APIFox 里原先导入的是静态 JSON 文档，不会自动跟随后端代码变化。  
只有通过 Swagger URL 重新导入，APIFox 才能同步最新注解信息。
