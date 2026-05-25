# 教学事务管理系统 — 代码快速上手指南

## 项目概览

这是一个 **B/S 架构的教学事务管理系统**（选课 + 成绩管理），技术栈为：

| 层级       | 技术                                                 |
| ---------- | ---------------------------------------------------- |
| 后端框架   | Spring Boot 3.3.5 (Java 17)                          |
| 数据库访问 | MyBatis 3.0.3（纯注解，无 XML 映射文件）             |
| 数据库     | MySQL，使用存储过程处理核心业务事务                  |
| 缓存       | Redis（Spring Data Redis）                           |
| 密码加密   | BCrypt（spring-security-crypto）                     |
| 前端       | 原生 HTML + JavaScript + CSS（单页应用 SPA，无框架） |

**总计约 59 个源文件**，结构清晰，适合学习 Spring Boot + MyBatis 的典型企业项目写法。

---

## 第一部分：项目文件总览

```
eduAffairSQL-main/
├── pom.xml                          # Maven 构建配置
├── README.md
├── quickstart.md                    # 本文件
├── database/
│   ├── schema.sql                   # 数据库 DDL（17 张表 + 触发器 + 视图 + 存储过程）
│   └── data.sql                     # 种子测试数据
├── scripts/
│   ├── backup_database.ps1          # Windows 备份脚本
│   └── backup_database.sh           # Linux/macOS 备份脚本
└── src/main/
    ├── resources/
    │   ├── application.yml          # Spring Boot 配置（数据库、Redis、定时备份）
    │   └── static/
    │       ├── index.html           # 前端 SPA 入口
    │       ├── app.js               # 前端全部逻辑（约 2800 行）
    │       └── styles.css           # 前端样式表
    └── java/com/student/management/
        ├── TeachingAffairsApplication.java   # 启动类
        ├── common/                  # 公共工具
        ├── config/                  # Web 配置
        ├── controller/              # 控制器（6 个）
        ├── dto/                     # 数据传输对象（13 个）
        ├── mapper/                  # MyBatis 映射器（6 个）
        ├── security/                # 安全/认证（5 个）
        └── service/                 # 业务服务（7 个）
```

---

## 第二部分：推荐阅读路线（由浅入深）

### 第 1 步：理解项目骨架（3 个文件）

**目标**：了解项目如何启动、有哪些依赖、配置了什么。

| 阅读顺序 | 文件                                                                                                    | 关注点                                                                                                                                                         |
| -------- | ------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1.1      | [pom.xml](pom.xml)                                                                                      | 依赖：spring-boot-starter-web、mybatis-spring-boot-starter、mysql-connector-j、spring-boot-starter-data-redis、spring-security-crypto、spring-boot-starter-aop |
| 1.2      | [application.yml](src/main/resources/application.yml)                                                   | 服务端口 8000、MySQL 连接池（Hikari）、Redis 连接、MyBatis 驼峰映射、定时备份 cron（每天 02:00）                                                               |
| 1.3      | [TeachingAffairsApplication.java](src/main/java/com/student/management/TeachingAffairsApplication.java) | `@SpringBootApplication` + `@MapperScan` + `@EnableScheduling`，标准的 Spring Boot 启动类                                                                      |

**读完你应该知道**：项目跑在 8000 端口，连接 MySQL 的 `test` 数据库，开启了定时任务和 MyBatis 注解扫描。

---

### 第 2 步：理解数据库设计（1 个文件）

**目标**：搞懂数据是怎么存的，这是理解业务逻辑的基础。

| 阅读顺序 | 文件                              | 关注点                                          |
| -------- | --------------------------------- | ----------------------------------------------- |
| 2.1      | [schema.sql](database/schema.sql) | 17 张表 + 10 个触发器 + 2 个视图 + 5 个存储过程 |

**核心表关系（按依赖顺序读）**：

```
roles ──→ users ──→ students ──→ enrollments ──→ grades
  │         │         │                │
  │         │         │                │
  │         │       majors ──→ departments
  │         │
  │         └──→ teachers ──→ course_offerings ──→ course_offering_times
  │                               │                      │
  │                               │                      └──→ classrooms
  │                               │
  │                     courses ──┘
  │
  └── departments

semesters ──→ semester_active_phases
notices
transactions ──→ transaction_log_entries
backup_records
```

**表的作用速查**：

| 表名                      | 作用                                                                              |
| ------------------------- | --------------------------------------------------------------------------------- |
| `roles`                   | 角色（admin / teacher / student）                                                 |
| `users`                   | 通用用户表（用户名、密码哈希、邮箱、状态）                                        |
| `departments`             | 院系                                                                              |
| `majors`                  | 专业（属于某个院系）                                                              |
| `students`                | 学生扩展信息（学号、专业、入学年份），关联 `users`                                |
| `teachers`                | 教师扩展信息（工号、院系、职称），关联 `users`                                    |
| `semesters`               | 学期（名称、起止日期、最大学分上限）                                              |
| `semester_active_phases`  | 当前激活的学期阶段（选课阶段 / 登分阶段），同一时间只有一个选课学期和一个登分学期 |
| `courses`                 | 课程基础信息（课程号、课程名、学分、所属院系）                                    |
| `classrooms`              | 教室                                                                              |
| `course_offerings`        | **课程班**（某学期某教师开的某课程，有容量、考试占比），是选课的核心对象          |
| `course_offering_times`   | 课程班的上课时间（星期几、第几节、哪几周、哪个教室）                              |
| `enrollments`             | **选课记录**（学生选了哪个课程班，状态是 selected 还是 dropped）                  |
| `grades`                  | 成绩（平时分、考试分，关联选课记录）                                              |
| `notices`                 | 通知公告（标题、内容、受众角色）                                                  |
| `transactions`            | 业务事务审计（谁在什么时候做了什么操作）                                          |
| `transaction_log_entries` | 事务审计明细（INSERT/UPDATE/DELETE 等操作日志）                                   |
| `backup_records`          | 数据库备份记录                                                                    |

**两个关键视图**：

- `course_offering_stats`：课程班 + 已选人数统计，几乎所有查询都用它，避免重复 JOIN
- `grade_results`：成绩 + 最终分计算（`平时分 × (1-考试占比) + 考试分 × 考试占比`）+ 绩点换算

**五个存储过程（核心业务逻辑在数据库层）**：

- `sp_select_course`：学生选课，内含完整的业务校验（重复选课、时间冲突、学分上限、容量检查、已通过课程不可重选等）
- `sp_student_drop_course`：学生退课
- `sp_admin_drop_course`：管理员为学生退课
- `sp_admin_drop_enrollment`：管理员按选课记录 ID 退课
- `sp_save_grade`：教师录入成绩

**触发器的作用**：学期日期不能重叠、选课不能超容量、课程时间不能和教师/教室/学生已有安排冲突。

---

### 第 3 步：理解 API 统一响应和异常处理（3 个文件）

**目标**：搞懂后端如何统一返回数据、如何处理错误。

| 阅读顺序 | 文件                                                                                                   | 关注点                                                                                                                                         |
| -------- | ------------------------------------------------------------------------------------------------------ | ---------------------------------------------------------------------------------------------------------------------------------------------- |
| 3.1      | [ApiResponse.java](src/main/java/com/student/management/common/ApiResponse.java)                       | `record ApiResponse<T>(boolean success, T data, String message)` — Java 17 的 record 类型，不可变数据载体。`ok()` 表示成功，`error()` 表示失败 |
| 3.2      | [ApiException.java](src/main/java/com/student/management/common/ApiException.java)                     | 自定义运行时异常，带上 HTTP 状态码。业务逻辑中遇到错误就 `throw new ApiException(400, "错误信息")`                                             |
| 3.3      | [GlobalExceptionHandler.java](src/main/java/com/student/management/common/GlobalExceptionHandler.java) | `@RestControllerAdvice`，统一拦截三种异常：ApiException（返回对应状态码）、参数校验异常（400）、数据库异常（400）、兜底异常（500）             |

**读完你应该知道**：所有 API 返回 `{"success": true/false, "data": ..., "message": ...}` 格式。业务代码只需抛 ApiException，全局处理器会自动转成 JSON 响应。

---

### 第 4 步：理解认证与权限（5 个文件）

**目标**：搞懂用户如何登录、token 如何管理、权限如何控制。

| 阅读顺序 | 文件                                                                                             | 关注点                                                                                                                          |
| -------- | ------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------- |
| 4.1      | [SessionUser.java](src/main/java/com/student/management/security/SessionUser.java)               | `record` 类型，包含用户 id、username、role、profile（学生或教师的扩展信息 Map）                                                 |
| 4.2      | [SessionRegistry.java](src/main/java/com/student/management/security/SessionRegistry.java)       | 会话管理：`create()` 生成 token（两个 UUID 拼接），`find()` 从 ConcurrentHashMap + Redis 双层查找，默认 8 小时过期              |
| 4.3      | [CurrentUserContext.java](src/main/java/com/student/management/security/CurrentUserContext.java) | ThreadLocal 存储当前请求的用户，请求结束自动清理                                                                                |
| 4.4      | [RequireRole.java](src/main/java/com/student/management/security/RequireRole.java)               | 自定义注解，`@RequireRole("admin")` 表示只有 admin 角色能访问                                                                   |
| 4.5      | [AuthInterceptor.java](src/main/java/com/student/management/security/AuthInterceptor.java)       | Spring 拦截器：从 `Authorization: Bearer <token>` 头解析 token，查 SessionRegistry 获取用户，检查 `@RequireRole` 注解的权限要求 |

**认证流程总结**：

1. 用户 POST `/api/auth/login` 发送用户名密码
2. AuthService 校验密码（BCrypt），创建 SessionUser，生成 token 返回
3. 后续请求在 Header 中携带 `Authorization: Bearer <token>`
4. AuthInterceptor 拦截 `/api/**` 路径，验证 token 和角色权限
5. 通过后，SessionUser 通过 `CurrentUserArgumentResolver` 自动注入到 Controller 方法参数

---

### 第 5 步：理解 Controller 层（6 个文件）

**目标**：搞懂有哪些 API 接口，每个接口做什么。

| Controller                                                                                           | 路由前缀                              | 所需角色  | 功能                                                                                                                 |
| ---------------------------------------------------------------------------------------------------- | ------------------------------------- | --------- | -------------------------------------------------------------------------------------------------------------------- |
| [AuthController.java](src/main/java/com/student/management/controller/AuthController.java)           | `/api/auth/**`, `/api/me`             | 无/已登录 | 登录、登出、修改密码、获取当前用户信息                                                                               |
| [CommonController.java](src/main/java/com/student/management/controller/CommonController.java)       | `/api/public/landing`, `/api/catalog` | 无/已登录 | 公共首页数据（当前学期、通知）、系统目录（学期列表、选课/登分状态）                                                  |
| [DashboardController.java](src/main/java/com/student/management/controller/DashboardController.java) | `/api/dashboard`                      | 已登录    | 按角色返回不同首页数据（管理员看系统状态，教师看授课统计，学生看已选课程）                                           |
| [AdminController.java](src/main/java/com/student/management/controller/AdminController.java)         | `/api/admin/**`                       | admin     | 用户管理、教师管理、学生管理、课程管理、课程班管理、学期管理、选课控制、通知管理、事务日志、备份管理（约 30 个接口） |
| [TeacherController.java](src/main/java/com/student/management/controller/TeacherController.java)     | `/api/teacher/**`                     | teacher   | 查看任课课程、排课安排、成绩录入                                                                                     |
| [StudentController.java](src/main/java/com/student/management/controller/StudentController.java)     | `/api/student/**`                     | student   | 浏览可选课程、选课、退课、查看课表、查看成绩                                                                         |

**关键设计模式**：

- 所有 Controller 通过构造器注入 Service（Spring 推荐的依赖注入方式）
- Controller 方法参数中直接写 `SessionUser user`，由 `CurrentUserArgumentResolver` 自动注入
- 返回类型统一包装在 `ApiResponse<T>` 中

---

### 第 6 步：理解 Service 层（7 个文件）

**目标**：搞懂业务逻辑怎么写，缓存怎么用，事务审计怎么做。

| Service                                                                                                   | 核心职责                                                                                                                                                                                                          |
| --------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| [AuthService.java](src/main/java/com/student/management/service/AuthService.java)                         | 登录验证（查用户表 → 验密码 → 查学生/教师 profile → 创建 session）；修改密码                                                                                                                                      |
| [DashboardService.java](src/main/java/com/student/management/service/DashboardService.java)               | 简单的路由分发：根据用户角色调用对应的 AdminService/TeacherService/StudentService 的 dashboard 方法                                                                                                               |
| [AdminService.java](src/main/java/com/student/management/service/AdminService.java)                       | **最大的 Service（约 910 行）**。包含：用户 CRUD、教师 CRUD、学生 CRUD、课程管理、课程班管理（含排课冲突校验）、学期管理（含选课/登分阶段控制）、通知管理、选课统计、成绩统计、系统状态监控（CPU/内存/磁盘/网络） |
| [StudentService.java](src/main/java/com/student/management/service/StudentService.java)                   | 学生选课/退课（调用存储过程）、查看可选课程、课表、成绩单                                                                                                                                                         |
| [TeacherService.java](src/main/java/com/student/management/service/TeacherService.java)                   | 教师查看课程/排课/成绩单、录入成绩（调用存储过程）                                                                                                                                                                |
| [BackupService.java](src/main/java/com/student/management/service/BackupService.java)                     | 调用外部脚本执行 mysqldump 备份，支持定时和手动触发，智能检测操作系统和编码                                                                                                                                       |
| [TransactionAuditService.java](src/main/java/com/student/management/service/TransactionAuditService.java) | 事务审计日志的写入（INSERT/UPDATE/DELETE/COMMIT/ROLLBACK），含补偿机制清理超时未完成的事务记录                                                                                                                    |

**Service 层的重要模式**：

1. **Redis 缓存**：大量使用 `cache.get(key, type, loader)` 模式——先查缓存，缓存没有就执行 loader 查询数据库并写入缓存。修改数据后调用 `clearTeachingCaches()` 清除所有相关缓存前缀。

2. **事务审计**：通过 `@BusinessTransaction` 注解 + AOP 切面（`TransactionAuditAspect`）自动记录业务操作到 `transactions` 和 `transaction_log_entries` 表。被注解的方法会在独立事务中执行，自动记录开始/成功/失败。

3. **业务锁顺序**：代码注释明确写了锁顺序 `students → semester_active_phases → course_offerings → enrollments → grades`，这是为了防止数据库死锁。

---

### 第 7 步：理解 Mapper 层（6 个文件）

**目标**：搞懂 SQL 怎么写，MyBatis 注解怎么用。

| Mapper                                                                                                 | 主要 SQL                                                                                                                                                                                                                                     |
| ------------------------------------------------------------------------------------------------------ | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| [AuthMapper.java](src/main/java/com/student/management/mapper/AuthMapper.java)                         | 按用户名查用户（JOIN roles）、查学生/教师 profile、查密码哈希、更新密码                                                                                                                                                                      |
| [CommonMapper.java](src/main/java/com/student/management/mapper/CommonMapper.java)                     | 查询通知、查询学期列表、查询当前学期、查询选课/登分学期、**批量查上课时间**（`offeringTimes`，被多处复用）                                                                                                                                   |
| [AdminMapper.java](src/main/java/com/student/management/mapper/AdminMapper.java)                       | **最大的 Mapper（约 900 行）**。包含用户/教师/学生/课程/课程班/学期/通知的全部 CRUD SQL，锁查询（SELECT ... FOR UPDATE），排课冲突检测，选课统计，成绩统计，事务日志和备份记录的分页查询。**核心业务（选课/退课/登分）通过调用存储过程实现** |
| [StudentMapper.java](src/main/java/com/student/management/mapper/StudentMapper.java)                   | 学生可选课程查询（含已选状态、是否已通过标记）、调用选课/退课存储过程、课表查询、成绩查询、成绩单查询、仪表盘统计                                                                                                                            |
| [TeacherMapper.java](src/main/java/com/student/management/mapper/TeacherMapper.java)                   | 教师课程/排课/登分课程/花名册查询、调用登分存储过程、成绩统计、仪表盘统计                                                                                                                                                                    |
| [TransactionAuditMapper.java](src/main/java/com/student/management/mapper/TransactionAuditMapper.java) | 插入事务记录、插入日志条目、更新事务状态、查找僵尸事务                                                                                                                                                                                       |

**MyBatis 注解技巧**：

- `@Select` 写查询 SQL，使用 Java 15+ 的文本块 `"""..."""` 写多行 SQL
- `<script>` 标签内可以使用 MyBatis 动态 SQL（`<if>`, `<choose>`, `<foreach>` 等）
- `<script>` 中的 `<` 需要写成 `&lt;`
- 存储过程调用：`@Select("{ CALL sp_xxx(...) }")` + `@Options(statementType = StatementType.CALLABLE)`
- `@Param` 绑定方法参数到 SQL 中的 `#{paramName}`
- MyBatis 配置了 `map-underscore-to-camel-case: true`，所以数据库 `display_name` 自动映射为 Java 的 `displayName`，但在 SELECT 中用了别名的就按别名来

---

### 第 8 步：理解 AOP 事务审计（3 个文件）

**目标**：搞懂 `@BusinessTransaction` 注解如何实现自动审计。

| 文件                                                                                                     | 作用                                                                                                                                                                |
| -------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| [BusinessTransaction.java](src/main/java/com/student/management/common/BusinessTransaction.java)         | 注解定义，可配置 businessType、operation、tableName、recordIdArgIndex                                                                                               |
| [TransactionAuditAspect.java](src/main/java/com/student/management/common/TransactionAuditAspect.java)   | `@Around` 切面，拦截 `@BusinessTransaction` 注解的方法。自动生成 UUID 事务 ID，在独立事务中记录开始/成功/失败。自动从方法参数中提取 actor（SessionUser）和 recordId |
| [TransactionAuditContext.java](src/main/java/com/student/management/common/TransactionAuditContext.java) | ThreadLocal 持有当前事务 ID，让 Service 中的 `auditService.logStep()` 知道属于哪个事务                                                                              |

**工作流程**：

1. 方法被 `@BusinessTransaction` 标记
2. AOP 切面在方法执行前创建事务记录（status=started）
3. 方法内调用 `auditService.logStep()` 记录具体操作（INSERT/UPDATE/DELETE）
4. 方法正常结束 → 记录 committed
5. 方法抛异常 → 记录 rolled_back

---

### 第 9 步：理解 DTO 和辅助类（约 15 个文件）

| 类别 | 文件                                                                                                             | 作用                                                                                                    |
| ---- | ---------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------- |
| DTO  | `dto/LoginRequest.java`, `LoginResponse.java`, `ChangePasswordRequest.java`                                      | 认证相关请求/响应                                                                                       |
| DTO  | `dto/CreateUserRequest.java`, `StudentProfileRequest.java`, `TeacherRequest.java`                                | 用户/学生/教师创建请求                                                                                  |
| DTO  | `dto/CourseRequest.java`, `CreateOfferingRequest.java`                                                           | 课程和课程班创建请求                                                                                    |
| DTO  | `dto/SemesterRequest.java`, `NoticeRequest.java`                                                                 | 学期和通知创建请求                                                                                      |
| DTO  | `dto/SelectCourseRequest.java`, `DropCourseRequest.java`, `GradeRequest.java`                                    | 选课/退课/成绩录入请求                                                                                  |
| 工具 | [MapUtil.java](src/main/java/com/student/management/common/MapUtil.java)                                         | 从 `Map<String, Object>` 安全提取 long/string/boolean 值（因为 MyBatis 返回的是 `Map<String, Object>`） |
| 工具 | [PasswordUtil.java](src/main/java/com/student/management/common/PasswordUtil.java)                               | BCrypt 密码哈希和验证（strength=12）                                                                    |
| 配置 | [WebConfig.java](src/main/java/com/student/management/config/WebConfig.java)                                     | 注册 AuthInterceptor 到 `/api/**`，注册 CurrentUserArgumentResolver                                     |
| 配置 | [CurrentUserArgumentResolver.java](src/main/java/com/student/management/config/CurrentUserArgumentResolver.java) | 从 request attribute 取出 SessionUser 注入到 Controller 参数                                            |

---

### 第 10 步：理解前端（3 个文件）

| 文件                                               | 要点                                                                                  |
| -------------------------------------------------- | ------------------------------------------------------------------------------------- |
| [index.html](src/main/resources/static/index.html) | 极简 HTML，只有 `<div id="app">`、`<div id="toast">`、`<div id="modalRoot">` 三个容器 |
| [styles.css](src/main/resources/static/styles.css) | 纯 CSS，没有使用任何 CSS 框架                                                         |
| [app.js](src/main/resources/static/app.js)         | **约 2800 行的原生 JavaScript SPA**，无任何前端框架                                   |

**前端架构要点**：

- **路由**：基于 `state.route` 变量的客户端路由，不同角色有不同导航菜单（`navs` 对象定义）
- **状态管理**：全局 `state` 对象，包含 token、user、catalog、routeData、分页状态等。token 和用户信息持久化到 localStorage
- **渲染模式**：每种页面/弹窗对应一个 `render*()` 函数，返回 HTML 字符串，通过 `innerHTML` 渲染
- **事件处理**：全局事件委托，通过 `data-action` 属性识别用户操作
- **API 调用**：`api(path, options)` 函数封装 fetch，自动附带 Authorization 头，401 时自动跳转登录
- **三个角色视角**：
  - **管理员**：首页仪表盘（系统状态监控）、学期与选课控制、课程管理、课程班管理、学生管理、教师管理、通知发布、事务日志、数据库备份
  - **教师**：首页、任课课程、排课安排、成绩登录、通知公告
  - **学生**：首页、在线选课（课程卡片）、我的课表（周视图）、成绩总表（含绩点走势 SVG 折线图）、通知公告

---

## 第三部分：核心业务流程走读

### 流程 1：用户登录

```
前端 POST /api/auth/login { username, password }
  → AuthController.login()
    → AuthService.login()
      → AuthMapper.findUserByUsername()  // 查 users JOIN roles
      → PasswordUtil.matches()           // BCrypt 验证
      → AuthMapper.findStudentProfile()  // 查学生扩展信息
      → SessionRegistry.create()         // 生成 token，存入内存 + Redis
      → 返回 { token, user }
```

### 流程 2：学生选课

```
前端 POST /api/student/select { offeringId }
  → AuthInterceptor 验证 token + @RequireRole("student")
  → CurrentUserArgumentResolver 注入 SessionUser
  → StudentController.select()
    → StudentService.selectCourse()
      → 从 user.profile 提取 studentId
      → StudentMapper.callSelectCourse()  // 调用存储过程 sp_select_course
        → 存储过程内：
          1. 锁定学生行（FOR UPDATE）
          2. 检查选课阶段是否开放
          3. 锁定课程班行（FOR UPDATE）
          4. 检查课程班状态（必须是 selecting）
          5. 检查容量（selected < capacity）
          6. 检查是否重复选课
          7. 检查是否已通过该课程
          8. 检查时间冲突
          9. 检查学分上限
          10. INSERT INTO enrollments
      → clearTeachingCaches()  // 清除所有 Redis 缓存
```

### 流程 3：管理员开放选课

```
前端 POST /api/admin/semesters/{id}/selection/start
  → AdminController.startSemesterSelection()
    → AdminService.startSemesterSelection()
      1. 检查学期存在
      2. 检查不处于"登分中"状态（选课和登分不能同时进行）
      3. 锁定所有活跃阶段行（FOR UPDATE）
      4. 关闭所有当前选课学期
      5. 开启指定学期的选课阶段
      6. 清除缓存
```

---

## 第四部分：关键技术要点

### 1. 为什么用存储过程而不是 Java 代码处理选课？

选课涉及多表锁定和复杂校验（容量检查、时间冲突、学分上限、重复选课等）。存储过程在数据库层面执行，可以减少网络往返，并且 `SELECT ... FOR UPDATE` 的行锁在同一个数据库事务中更可靠。

### 2. Redis 缓存策略

- 缓存 Key 格式：`teaching-affairs:admin:users`、`teaching-affairs:student:123:schedule:all` 等
- TTL 默认 300 秒
- Redis 不可用时自动降级（backoff 30 秒），不影响业务
- 任何写操作后调用 `evictByPrefix("admin:", "student:", "teacher:")` 批量清除

### 3. 数据库锁顺序

代码和存储过程都遵循固定锁顺序：`students → semester_active_phases → course_offerings → enrollments → grades`，防止死锁。

### 4. 定时任务

两个 `@Scheduled` 任务：

- `BackupService.runScheduledBackup()`：每天 02:00 执行数据库备份
- `TransactionAuditService.compensateStaleStartedTransactions()`：每 5 分钟清理超过 10 分钟未完成的事务审计记录

---

## 第五部分：如何运行项目

### 前置条件

- JDK 17+
- MySQL 8.0+
- Redis（可选，不启动也能运行，缓存自动降级）
- Maven 3.6+

### 步骤

```bash
# 1. 创建数据库并导入表结构和数据
mysql -u root -p < database/schema.sql
mysql -u root -p < database/data.sql

# 2. 修改 application.yml 中的数据库连接信息（如需要）
#    默认：localhost:3306/test，用户名 ruoyi，密码 password

# 3. 启动项目
mvn spring-boot:run

# 4. 访问 http://localhost:8000
```

### 测试账号（来自 data.sql）

| 角色   | 用户名   | 密码       |
| ------ | -------- | ---------- |
| 管理员 | admin    | admin123   |
| 教师   | t001     | teacher001 |
| 学生   | s2024001 | student001 |

---

## 第六部分：学习建议

如果这是你第一次接触此类项目，建议按以下顺序深入：

1. **先跑起来**：启动项目，用三个角色的测试账号分别登录，把每个功能点一遍，建立感性认识
2. **跟踪一个请求**：以"学生选课"为例，从浏览器 F12 看网络请求 → Controller → Service → Mapper → 存储过程 → 数据库，完整跟一遍调用链
3. **改一个小功能**：比如给通知增加一个"置顶"字段，体会从数据库 → Mapper → Service → Controller → 前端的完整改动流程
4. **理解项目为什么这样组织**：对比经典的三层架构（Controller → Service → Mapper），思考这个项目额外加了什么（AOP 审计、Redis 缓存、ThreadLocal 用户上下文、存储过程处理核心业务）
