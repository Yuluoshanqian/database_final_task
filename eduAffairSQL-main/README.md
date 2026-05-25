# 教务选课与成绩管理系统

这是一个面向学校日常教务场景的 B/S 管理系统，覆盖管理员、教师、学生三类角色，支持学期维护、课程基础信息维护、课程班安排、在线选课、成绩登记、成绩查询和通知公告等流程。

## 技术栈

- 后端：Java 17、Spring Boot 3.3.5、Spring MVC、MyBatis
- 数据库：MySQL 8.x
- 缓存：Spring Data Redis，可通过环境变量关闭
- 前端：原生 HTML、CSS、JavaScript 单页应用
- 构建：Maven

## 项目结构

```text
.
├── pom.xml
├── README.md
├── database/
│   ├── schema.sql
│   └── data.sql
├── scripts/
│   ├── backup_database.ps1
│   └── backup_database.sh
└── src/
    └── main/
        ├── java/com/student/management/
        │   ├── common/          # 通用响应、异常、缓存、密码和 Map 工具
        │   ├── config/          # MVC 配置、当前用户参数解析
        │   ├── controller/      # 登录、公共、管理员、教师、学生接口
        │   ├── dto/             # 请求和响应 DTO
        │   ├── mapper/          # MyBatis SQL 映射
        │   ├── security/        # 会话、角色注解、认证拦截器
        │   └── service/         # 业务服务
        └── resources/
            ├── application.yml
            └── static/
                ├── index.html
                ├── app.js
                └── styles.css
```

## 数据库说明

首次运行前需要按顺序手动导入 `database/schema.sql` 和 `database/data.sql`。项目不会在启动时自动 `ALTER TABLE`，需要调整表结构或初始化数据时，请修改 SQL 后重新导入。

主要数据表：

- `roles`、`users`：账号、角色和账号状态。
- `departments`、`majors`、`students`、`teachers`：院系、专业、学生和教师基础信息。
- `semesters`：学期名称、开始日期、结束日期和最大学分。
- `semester_active_phases`：当前开放阶段到学期的映射，`phase` 取 `selection`/`grading`，用于约束每个阶段最多一个学期且同一学期不能同时开放两个阶段。
- `courses`：课程基础信息，包括课程号、课程名、开课院系、学分和课程状态
- `course_offerings`、`course_offering_times`、`classrooms`：课程班、课程班上课时间段、教室和排课信息；已选人数由 `course_offering_stats` 视图动态统计，仅用于列表展示，容量判定以事务内锁定的基础表为准。
- `enrollments`：学生选课记录。
- `grades`：平时分、考试分；最终成绩和绩点由 `grade_results` 视图动态计算，最终成绩为整数。
- `notices`：通知公告。
- `transactions`：业务事务主表，保存一次事务的公共信息，包括事务号、业务类型、操作人、开始/结束时间和最终状态。
- `transaction_log_entries`：业务事务日志明细表，保存每一步写操作或状态变化，包括操作类型、目标表、记录 id、步骤状态和消息。
- `backup_records`：数据库逻辑备份记录，保存备份数据库、文件名、目录、文件大小、状态、触发方式、开始/结束时间和消息。

## 事务与并发控制设计

核心业务 DML 与建库、建表、建视图、建触发器等 DDL 分开处理。`database/schema.sql` 负责数据库结构、视图、触发器和存储过程，`database/data.sql` 只负责示例数据导入；运行期选课、退课、登分等业务修改不执行 DDL。

关键业务事务使用存储过程显式控制 ACID 边界：`sp_select_course`、`sp_student_drop_course`、`sp_admin_drop_course`、`sp_admin_drop_enrollment`、`sp_save_grade` 都在过程内部设置固定隔离级别 `READ COMMITTED`，并显式执行 `START TRANSACTION`、`COMMIT` 和异常处理中的 `ROLLBACK`。Spring 侧其它业务写操作统一使用 `@BusinessTransaction`，由审计切面通过 `TransactionTemplate` 固定 `READ_COMMITTED` 并统一提交或回滚；连接池初始化也执行 `SET SESSION TRANSACTION ISOLATION LEVEL READ COMMITTED`，避免不同连接使用各自默认隔离级别。

关键读取使用基础表加锁，不依赖视图结果完成并发敏感判断。选课时先按统一顺序锁定 `students`、`semester_active_phases`、`course_offerings`，容量从 `enrollments` 基础表统计；在最终写入 `enrollments` 前再次在同一事务内复核容量。因此当课程只剩 1 个名额且两个学生并发选课时，两个事务会在同一个 `course_offerings` 行锁上串行化，最多一个事务可以提交成功。

统一加锁顺序为：`students` -> `semester_active_phases` -> `course_offerings` -> `enrollments` -> `grades`。需要先通过非锁定读取定位外键时，过程会在获得上述顺序锁后再次校验目标行，避免使用过期判断结果。Java 侧涉及课程班、选课或成绩的业务也按同一顺序显式加锁；例如软删除课程班会先锁该课程班下的学生，再锁相关开放阶段，再锁课程班、选课记录和成绩记录。

一致性的最后防线放在数据库层：主键、外键、唯一约束、`CHECK` 约束约束基础数据范围；触发器继续限制学期重叠、课程容量、排课冲突、管理员调整课程班时间后已选学生的课表冲突和公告发布人。业务校验失败、SQL 异常或触发器 `SIGNAL` 报错都会进入存储过程异常处理并显式回滚。

事务日志按 3NF 拆为 `transactions` 和 `transaction_log_entries`：每个事务在 `transactions` 中生成一条 `transaction_id` 主记录，保存 `business_type`、`actor_user_id`、开始/结束时间和最终状态；每个 `START`、业务表 `INSERT`/`UPDATE`/`UPSERT`/`DELETE`、`COMMIT` 或 `ROLLBACK` 步骤写入 `transaction_log_entries`，通过外键关联主记录。选课、退课、成绩录入由存储过程在数据库内写明细日志；管理员维护用户、师生、课程、课程班、学期、通知以及修改密码等 Spring 侧业务事务会把每一步数据库写入记录到同一套日志表。Java 侧成功日志和业务 DML 在同一事务中提交，失败日志使用独立事务补记；定时补偿任务会把长时间停留在 `started` 的事务标记为 `failed`，避免异常退出后日志状态悬挂。

## 核心功能

### 登录与公共页面

- 按管理员、教师、学生角色登录。
- 登录页展示当前学期、功能入口和通知公告。
- 认证通过后使用服务端会话令牌访问接口。

### 管理员

- 查看教务总览、课程容量、选课统计、通知和基础数据。
- 维护学期：新增学期、编辑自动判定出的当前学期起止日期和最大学分，并控制选课/登分开放窗口。
- 维护课程：查看课程号、课程名、开课院系、学分、课程状态；可新增课程、启用课程、弃用课程，不提供编辑或删除。
- 维护课程班：为当前学期新增和调整课程班，课程班包含教师、一个或多个上课时间段、每个时间段的教室、容量和期末占比；课程班选课名单中可由管理员手动退选学生。
- 删除课程班采用软删除：无选课、无成绩时只标记课程班为 `deleted`；有选课、无成绩时标记为 `deleted` 并把该课程班下选课状态改为 `dropped`；已有成绩或课程班所在学期已结束时禁止删除。
- 弃用课程不能再新建课程班；已创建的历史课程班不受影响。
- 维护学生和教师：可新增、修改、启用或弃用账号；弃用只限制登录，不清除历史选课、授课和成绩数据。学生管理展示学院、专业、学生邮箱、入学年份和账号状态。
- 发布、编辑和删除通知。
- 查看事务日志，按页展示业务事务摘要，每页最多 10 条；页面只显示操作人、时间、对象、动作和成功/失败结果，数据库中仍保留更细的步骤明细。
- 查看逻辑备份记录，并可手动触发一次数据库备份；系统也会按定时配置每天执行一次备份。

### 教师

- 查看自己负责的课程班、学生名单和统计数据。
- 成绩登记面向管理员已开放登分的学期课程班，录入平时分和考试分后通过视图查看最终成绩与绩点。
- 最终成绩按比例计算后四舍五入为整数，绩点按四舍五入后的最终成绩计算。

### 学生

- 只在管理员开放选课的学期内选课和退课。
- 当前学期不能重复选同一门课程。
- 历史学期已通过课程不能重复选；历史挂科课程允许重修。
- 选课时检查容量、学分上限、时间冲突和单双周冲突；学分上限取自所在学期的最大学分。
- 可查看当前课表、已选课程、可选课程、个人成绩和绩点趋势。
- 学生首页平均绩点按全部学期已出成绩课程的学分加权平均计算。

## 学期状态规则

`semesters` 表只保存学期名称、起止日期和最大学分等学期基础信息；选课与登分的当前开放阶段保存在 `semester_active_phases` 表。学期展示状态仍由当前日期和学期起止日期动态计算：

| 状态 | 计算规则 | 含义 |
| --- | --- | --- |
| `not_started` | 当前日期早于 `start_date` | 未开始 |
| `active` | 当前日期位于 `start_date` 和 `end_date` 之间，含首尾日期 | 进行中 |
| `archived` | 当前日期晚于 `end_date` | 已归档 |

系统不存储当前学期标记。当前学期按系统日期自动判定：优先选择当前日期位于起止日期内的进行中学期；如果当前日期处于两个学期之间，则选择未来开始日期最早的学期；如果所有学期均已结束，则选择最近结束的学期。`max_credit` 保存该学期的选课最大学分。

选课与登分由管理员手动开放或关闭：同一时间最多一个学期处于选课开放状态，最多一个学期处于登分开放状态，且同一学期不能同时开放选课和登分。`semester_active_phases` 使用 `phase` 主键和 `semester_id` 唯一约束维护这些规则。学生选课除要求课程班状态为 `selecting` 外，还要求目标学期正处于选课开放状态；教师保存成绩要求目标学期正处于登分开放状态。

## 课程与课程班规则

- 课程是基础数据，只包含课程号、课程名、开课院系、学分和状态。
- 课程可以启用或弃用。弃用只影响后续新建课程班，不影响已有课程班、历史选课和历史成绩。
- 课程班是具体开课安排，关联课程、学期、教师、容量和成绩比例；上课时间段及其教室独立保存在 `course_offering_times`，选课容量由数据库触发器限制。
- 一个课程班至少需要一个上课时间段，每个时间段包含星期、开始节、结束节、起始周、结束周和单双周类型。
- 时间段单双周使用枚举：`all` 表示全部，`odd` 表示单周，`even` 表示双周。
- 时间冲突检查会同时考虑星期、节次、起止周和单双周；全部周次与单周/双周均视为冲突，单周和双周可共用同一时段。
- 管理员调整已有选课的课程班时间时，会先在 Java 事务内按已锁定基础表校验该课程班下所有已选学生的其他课程；数据库触发器再次兜底，禁止提交会造成学生同一时间上两门课的更新。

## 成绩与绩点规则

最终成绩由 `grade_results` 视图按课程班期末占比动态计算，平时占比为 `1 - 期末占比`：

```text
最终成绩 = ROUND(平时分 * (1 - 期末占比) + 考试分 * 期末占比, 0)
```

`grades` 表只保存平时分和考试分，不保存最终成绩和绩点。`grade_results.final_score` 使用 `ROUND(..., 0)` 计算为整数，绩点按四舍五入后的最终成绩计算。

| 最终成绩 | 绩点 |
| --- | --- |
| 90-100 | 4.0 |
| 85-89 | 3.7 |
| 82-84 | 3.3 |
| 78-81 | 3.0 |
| 75-77 | 2.7 |
| 72-74 | 2.3 |
| 68-71 | 2.0 |
| 66-67 | 1.7 |
| 64-65 | 1.5 |
| 60-63 | 1.0 |
| 0-59 | 0.0 |

## 使用方法

### 1. 准备环境

- JDK 17
- Maven 3.8+
- MySQL 8.x
- Redis 7.x，可选

### 2. 导入数据库

在 MySQL 命令行中执行：

```sql
SOURCE E:/Student/database/schema.sql;
SOURCE E:/Student/database/data.sql;
```

或在系统终端执行：

```bash
mysql -u root -p < database/schema.sql
mysql -u root -p test < database/data.sql
```

导入后会创建 `test` 数据库、表结构、触发器、存储过程和示例数据。

### 3. 配置环境变量

可直接使用 `src/main/resources/application.yml` 中的默认值，也可以覆盖：

cmd：

```bat
set "DB_HOST=127.0.0.1"
set "DB_PORT=3306"
set "DB_NAME=test"
set "DB_USER=root"
set "DB_PASSWORD=password"
set "PORT=8000"
set "REDIS_HOST=127.0.0.1"
set "REDIS_PORT=6379"
set "REDIS_PASSWORD="
set "REDIS_DATABASE=0"
set "CACHE_ENABLED=true"
set "CACHE_TTL_SECONDS=300"
set "CACHE_REDIS_BACKOFF_SECONDS=30"
set "BACKUP_ENABLED=true"
set "BACKUP_WINDOWS_SCRIPT_PATH=scripts/backup_database.ps1"
set "BACKUP_UNIX_SCRIPT_PATH=scripts/backup_database.sh"
set "BACKUP_DIR=backups"
set "BACKUP_RETAIN_COUNT=10"
set "BACKUP_CRON=0 0 2 * * *"
```

PowerShell：

```powershell
$env:DB_HOST="127.0.0.1"
$env:DB_PORT="3306"
$env:DB_NAME="test"
$env:DB_USER="root"
$env:DB_PASSWORD="password"
$env:PORT="8000"
$env:REDIS_HOST="127.0.0.1"
$env:REDIS_PORT="6379"
$env:REDIS_PASSWORD=""
$env:REDIS_DATABASE="0"
$env:CACHE_ENABLED="true"
$env:CACHE_TTL_SECONDS="300"
$env:CACHE_REDIS_BACKOFF_SECONDS="30"
$env:BACKUP_ENABLED="true"
$env:BACKUP_WINDOWS_SCRIPT_PATH="scripts/backup_database.ps1"
$env:BACKUP_UNIX_SCRIPT_PATH="scripts/backup_database.sh"
$env:BACKUP_DIR="backups"
$env:BACKUP_RETAIN_COUNT="10"
$env:BACKUP_CRON="0 0 2 * * *"
```

Redis 默认启用。如果没有 Redis，可关闭缓存：

cmd：

```bat
set "CACHE_ENABLED=false"
```

PowerShell：

```powershell
$env:CACHE_ENABLED="false"
```

### 4. 逻辑备份

`scripts/backup_database.ps1` 和 `scripts/backup_database.sh` 是应用外部的逻辑备份脚本，真正执行 `mysqldump` 生成 `.sql` 文件，并写入 `backup_records`。应用默认按操作系统自动选择脚本：Windows 使用 `.ps1`，Linux/macOS 使用 `.sh`。如果显式设置 `BACKUP_SCRIPT_PATH`，则会优先使用指定脚本。

脚本默认使用 `DB_HOST`、`DB_PORT`、`DB_NAME`、`DB_USER`、`DB_PASSWORD`、`BACKUP_DIR` 等环境变量。服务器需要能在 `PATH` 中找到 `mysql` 和 `mysqldump`。

Windows 手动执行：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\backup_database.ps1
```

Linux/macOS 手动执行：

```bash
bash ./scripts/backup_database.sh
```

应用内已启用 Spring 定时任务，默认每天 `02:00` 调用该脚本；脚本会保留最近 10 个成功备份文件，较旧文件会删除并将记录标记为 `deleted`。如果不希望应用自动备份，可设置：

```powershell
$env:BACKUP_ENABLED="false"
```

### 5. 启动项目

开发启动：

```bash
mvn clean spring-boot:run
```

打包运行：

```bash
mvn clean package
java -jar target/teaching-affairs-management-1.0.0.jar
```

访问地址：

```text
http://localhost:8000
```

### 6. 默认账号

`database/data.sql` 中包含示例账号：

| 角色 | 用户名 | 密码 |
| --- | --- | --- |
| 管理员 | `admin` | `admin` |
| 教师 | `t1` | `t1` |
| 教师 | `t2` | `t2` |
| 教师 | `t3` | `t3` |
| 学生 | `s1` | `s1` |
| 学生 | `s2` | `s2` |
| 学生 | `s3` | `s3` |

## 常用验证命令

前端脚本语法检查：

```bash
node --check src/main/resources/static/app.js
```

Java 编译：

```bash
mvn "-Dmaven.repo.local=E:\Student\.m2\repository" compile
```

运行测试：

```bash
mvn "-Dmaven.repo.local=E:\Student\.m2\repository" test
```
