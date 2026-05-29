# 教务管理系统数据库设计文档

## 概述

本文档为教务管理系统（eduAffairSQL）的数据库 `schema.sql` 设计说明，涵盖数据库中所有表、视图、存储过程和触发器的详细定义。

- **数据库名称**: `test`
- **字符集**: `utf8mb4`
- **排序规则**: `utf8mb4_unicode_ci`
- **存储引擎**: InnoDB（所有表）

---

## 目录

1. [基础信息表](#1-基础信息表)
   - [roles — 角色表](#roles--角色表)
   - [users — 用户表](#users--用户表)
   - [departments — 院系表](#departments--院系表)
   - [majors — 专业表](#majors--专业表)
2. [人员档案表](#2-人员档案表)
   - [students — 学生表](#students--学生表)
   - [teachers — 教师表](#teachers--教师表)
3. [学期与课程表](#3-学期与课程表)
   - [semesters — 学期表](#semesters--学期表)
   - [semester_active_phases — 学期活跃阶段表](#semester_active_phases--学期活跃阶段表)
   - [courses — 课程表](#courses--课程表)
   - [classrooms — 教室表](#classrooms--教室表)
   - [course_offerings — 开课记录表](#course_offerings--开课记录表)
   - [course_offering_times — 课程时间安排表](#course_offering_times--课程时间安排表)
4. [选课与成绩表](#4-选课与成绩表)
   - [enrollments — 选课记录表](#enrollments--选课记录表)
   - [grades — 成绩表](#grades--成绩表)
5. [通知与审计表](#5-通知与审计表)
   - [notices — 通知公告表](#notices--通知公告表)
   - [transactions — 业务事务表](#transactions--业务事务表)
   - [transaction_log_entries — 事务日志表](#transaction_log_entries--事务日志表)
   - [backup_records — 备份记录表](#backup_records--备份记录表)
6. [触发器](#6-触发器)
7. [视图](#7-视图)
8. [存储过程](#8-存储过程)
9. [ER 关系总览](#9-er-关系总览)

---

## 1. 基础信息表

### roles — 角色表

系统角色定义表，所有用户的角色均引用此表。

| 列名   | 类型          | 约束               | 说明                                         |
| ------ | ------------- | ------------------ | -------------------------------------------- |
| `id`   | `BIGINT`      | PK, AUTO_INCREMENT | 主键                                         |
| `code` | `VARCHAR(32)` | NOT NULL, UNIQUE   | 角色编码（如 `admin`、`teacher`、`student`） |
| `name` | `VARCHAR(64)` | NOT NULL           | 角色显示名称                                 |

### users — 用户表

系统所有用户的统一登录账号表。通过 `role_id` 区分用户类型，具体档案信息存储在 `students` 或 `teachers` 表中。

| 列名            | 类型                         | 约束                                  | 说明                 |
| --------------- | ---------------------------- | ------------------------------------- | -------------------- |
| `id`            | `BIGINT`                     | PK, AUTO_INCREMENT                    | 主键                 |
| `username`      | `VARCHAR(64)`                | NOT NULL, UNIQUE                      | 登录用户名           |
| `password_hash` | `VARCHAR(255)`               | NOT NULL                              | 密码哈希值           |
| `display_name`  | `VARCHAR(80)`                | NOT NULL                              | 显示名称（真实姓名） |
| `email`         | `VARCHAR(120)`               | NOT NULL, UNIQUE                      | 电子邮箱             |
| `role_id`       | `BIGINT`                     | NOT NULL, FK → `roles(id)`            | 所属角色             |
| `status`        | `ENUM('enabled','disabled')` | NOT NULL, DEFAULT `'enabled'`         | 账号启用状态         |
| `created_at`    | `DATETIME`                   | NOT NULL, DEFAULT `CURRENT_TIMESTAMP` | 创建时间             |

### departments — 院系表

学校院系/部门信息表。

| 列名    | 类型          | 约束               | 说明                                 |
| ------- | ------------- | ------------------ | ------------------------------------ |
| `id`    | `BIGINT`      | PK, AUTO_INCREMENT | 主键                                 |
| `name`  | `VARCHAR(80)` | NOT NULL, UNIQUE   | 院系名称（如"计算机科学与技术学院"） |
| `phone` | `VARCHAR(32)` | NULL               | 院系联系电话                         |

### majors — 专业表

各院系下设的专业信息，由院系-专业名称联合唯一约束。

| 列名             | 类型          | 约束                                  | 说明           |
| ---------------- | ------------- | ------------------------------------- | -------------- |
| `id`             | `BIGINT`      | PK, AUTO_INCREMENT                    | 主键           |
| `department_id`  | `BIGINT`      | NOT NULL, FK → `departments(id)`      | 所属院系       |
| `name`           | `VARCHAR(80)` | NOT NULL                              | 专业名称       |
| `duration_years` | `TINYINT`     | NOT NULL, DEFAULT `4`, CHECK `1~8`    | 学制年限（年） |
|                  |               | **联合唯一**: `(department_id, name)` |                |

---

## 2. 人员档案表

### students — 学生表

学生角色对应的档案信息，与 `users` 表一对一关联。

| 列名             | 类型          | 约束                               | 说明         |
| ---------------- | ------------- | ---------------------------------- | ------------ |
| `id`             | `BIGINT`      | PK, AUTO_INCREMENT                 | 主键         |
| `user_id`        | `BIGINT`      | NOT NULL, UNIQUE, FK → `users(id)` | 关联用户账号 |
| `student_no`     | `VARCHAR(32)` | NOT NULL, UNIQUE                   | 学号         |
| `major_id`       | `BIGINT`      | NOT NULL, FK → `majors(id)`        | 所属专业     |
| `admission_year` | `SMALLINT`    | NOT NULL, CHECK `1900~2100`        | 入学年份     |

### teachers — 教师表

教师角色对应的档案信息，与 `users` 表一对一关联。

| 列名            | 类型          | 约束                               | 说明                               |
| --------------- | ------------- | ---------------------------------- | ---------------------------------- |
| `id`            | `BIGINT`      | PK, AUTO_INCREMENT                 | 主键                               |
| `user_id`       | `BIGINT`      | NOT NULL, UNIQUE, FK → `users(id)` | 关联用户账号                       |
| `teacher_no`    | `VARCHAR(32)` | NOT NULL, UNIQUE                   | 教师工号                           |
| `department_id` | `BIGINT`      | NOT NULL, FK → `departments(id)`   | 所属院系                           |
| `title`         | `VARCHAR(64)` | NOT NULL, DEFAULT `'讲师'`         | 职称（如"教授"、"副教授"、"讲师"） |

> **设计要点**: 当 `users.role_id` 指向学生角色时，`students` 表存在对应记录；指向教师角色时，`teachers` 表存在对应记录。这种"基表+扩展表"的模式避免了多角色场景下的字段冗余。

---

## 3. 学期与课程表

### semesters — 学期表

学期信息表，记录每个学期的起止日期和学分上限。触发器会自动校验日期范围无重叠。

| 列名         | 类型           | 约束                                  | 说明                                 |
| ------------ | -------------- | ------------------------------------- | ------------------------------------ |
| `id`         | `BIGINT`       | PK, AUTO_INCREMENT                    | 主键                                 |
| `name`       | `VARCHAR(64)`  | NOT NULL, UNIQUE                      | 学期名称（如"2024-2025学年第1学期"） |
| `start_date` | `DATE`         | NOT NULL                              | 学期开始日期                         |
| `end_date`   | `DATE`         | NOT NULL                              | 学期结束日期                         |
| `max_credit` | `DECIMAL(5,1)` | NOT NULL, DEFAULT `30.0`, CHECK `> 0` | 该学期学生可选最大总学分             |
|              |                | **CHECK**: `start_date <= end_date`   |                                      |
|              |                | **CHECK**: `max_credit > 0`           |                                      |

### semester_active_phases — 学期活跃阶段表

定义当前学期所处的业务阶段（选课阶段 / 成绩录入阶段）。该表最多只有 **两条** 记录（每种阶段各一条），通过 `semester_id` 指向各自活跃的学期。

| 列名          | 类型                          | 约束                                                     | 说明                                             |
| ------------- | ----------------------------- | -------------------------------------------------------- | ------------------------------------------------ |
| `phase`       | `ENUM('selection','grading')` | **PK**                                                   | 阶段类型（`selection`=选课, `grading`=成绩录入） |
| `semester_id` | `BIGINT`                      | NOT NULL, UNIQUE, FK → `semesters(id)` ON DELETE CASCADE | 当前活跃的学期                                   |

> **设计要点**: 此表充当全局开关——选课存储过程检查 `phase='selection'` 是否存在记录，成绩录入检查 `phase='grading'`。管理员通过更新此表即可控制当前学期允许的操作类型。

### courses — 课程表

课程目录，定义学校开设的所有课程。

| 列名            | 类型                         | 约束                             | 说明                   |
| --------------- | ---------------------------- | -------------------------------- | ---------------------- |
| `id`            | `BIGINT`                     | PK, AUTO_INCREMENT               | 主键                   |
| `code`          | `VARCHAR(32)`                | NOT NULL, UNIQUE                 | 课程编号（如 `CS101`） |
| `name`          | `VARCHAR(100)`               | NOT NULL                         | 课程名称               |
| `department_id` | `BIGINT`                     | NOT NULL, FK → `departments(id)` | 开课院系               |
| `credit`        | `DECIMAL(3,1)`               | NOT NULL, CHECK `> 0`            | 学分                   |
| `status`        | `ENUM('enabled','disabled')` | NOT NULL, DEFAULT `'enabled'`    | 课程启用状态           |

### classrooms — 教室表

教室/教学场地信息。

| 列名       | 类型          | 约束                                | 说明       |
| ---------- | ------------- | ----------------------------------- | ---------- |
| `id`       | `BIGINT`      | PK, AUTO_INCREMENT                  | 主键       |
| `building` | `VARCHAR(64)` | NOT NULL                            | 教学楼名称 |
| `room_no`  | `VARCHAR(32)` | NOT NULL                            | 房间号     |
|            |               | **联合唯一**: `(building, room_no)` |            |

### course_offerings — 开课记录表

每学期具体开设的课程实例。一门课程（`courses`）可以在不同学期多次开设，每次开设为一个 `course_offering`。

| 列名          | 类型                                   | 约束                                  | 说明                                                            |
| ------------- | -------------------------------------- | ------------------------------------- | --------------------------------------------------------------- |
| `id`          | `BIGINT`                               | PK, AUTO_INCREMENT                    | 主键                                                            |
| `course_id`   | `BIGINT`                               | NOT NULL, FK → `courses(id)`          | 所属课程                                                        |
| `semester_id` | `BIGINT`                               | NOT NULL, FK → `semesters(id)`        | 所属学期                                                        |
| `teacher_id`  | `BIGINT`                               | NOT NULL, FK → `teachers(id)`         | 授课教师                                                        |
| `capacity`    | `SMALLINT`                             | NOT NULL, CHECK `> 0`                 | 选课容量上限                                                    |
| `exam_ratio`  | `DECIMAL(4,2)`                         | NOT NULL, DEFAULT `0.60`, CHECK `0~1` | 期末考试成绩占比（期末 : 平时 = exam_ratio : (1-exam_ratio)）   |
| `status`      | `ENUM('selecting','closed','deleted')` | NOT NULL, DEFAULT `'selecting'`       | 开课状态（`selecting`=可选, `closed`=关闭, `deleted`=逻辑删除） |

**final_score 计算公式**:

```
final_score = ROUND(usual_score × (1 - exam_ratio) + exam_score × exam_ratio, 0)
```

例如 `exam_ratio = 0.60` 表示平时成绩占 40%、期末考试成绩占 60%。

### course_offering_times — 课程时间安排表

每门开课的具体时间与地点安排。一个 `course_offering` 可以有多个时间段（如理论课+实验课）。

| 列名            | 类型                       | 约束                                                                 | 说明                                            |
| --------------- | -------------------------- | -------------------------------------------------------------------- | ----------------------------------------------- |
| `id`            | `BIGINT`                   | PK, AUTO_INCREMENT                                                   | 主键                                            |
| `offering_id`   | `BIGINT`                   | NOT NULL, FK → `course_offerings(id)` ON DELETE CASCADE              | 所属开课记录                                    |
| `classroom_id`  | `BIGINT`                   | NOT NULL, FK → `classrooms(id)`                                      | 上课教室                                        |
| `day_of_week`   | `TINYINT`                  | NOT NULL, COMMAND `'1-7, Monday is 1'`, CHECK `1~7`                  | 星期几（1=周一, 7=周日）                        |
| `start_section` | `TINYINT`                  | NOT NULL, CHECK `1~12`                                               | 开始节次（第几节课开始）                        |
| `end_section`   | `TINYINT`                  | NOT NULL, CHECK `1~12`                                               | 结束节次（第几节课结束）                        |
| `start_week`    | `TINYINT`                  | NOT NULL, DEFAULT `1`, CHECK `1~30`                                  | 起始教学周                                      |
| `end_week`      | `TINYINT`                  | NOT NULL, DEFAULT `16`, CHECK `1~30`                                 | 结束教学周                                      |
| `week_type`     | `ENUM('all','odd','even')` | NOT NULL, DEFAULT `'all'`                                            | 周次类型（`all`=每周, `odd`=单周, `even`=双周） |
|                 |                            | **CHECK**: `start_section <= end_section`                            |                                                 |
|                 |                            | **CHECK**: `start_week <= end_week`                                  |                                                 |
|                 |                            | **INDEX**: `(offering_id, day_of_week, start_section, end_section)`  | 查询某课程时间安排                              |
|                 |                            | **INDEX**: `(classroom_id, day_of_week, start_section, end_section)` | 查询教室占用情况                                |

---

## 4. 选课与成绩表

### enrollments — 选课记录表

学生选课的核心关联表，记录学生选择的课程实例。

| 列名          | 类型                         | 约束                                      | 说明                                        |
| ------------- | ---------------------------- | ----------------------------------------- | ------------------------------------------- |
| `id`          | `BIGINT`                     | PK, AUTO_INCREMENT                        | 主键                                        |
| `student_id`  | `BIGINT`                     | NOT NULL, FK → `students(id)`             | 学生                                        |
| `offering_id` | `BIGINT`                     | NOT NULL, FK → `course_offerings(id)`     | 所选开课                                    |
| `status`      | `ENUM('selected','dropped')` | NOT NULL, DEFAULT `'selected'`            | 选课状态（`selected`=已选, `dropped`=已退） |
|               |                              | **联合唯一**: `(student_id, offering_id)` | 同一学生对同一开课最多一条记录              |
|               |                              | **INDEX**: `(offering_id, status)`        | 查询某课程的选课学生                        |
|               |                              | **INDEX**: `(student_id, status)`         | 查询某学生的选课列表                        |

> **设计要点**: 退课使用 `status='dropped'` 而非物理删除，保留选课历史记录。触发器在 INSERT/UPDATE 时会校验容量是否超限。

### grades — 成绩表

选课对应的成绩记录，与 `enrollments` 一对一关联。

| 列名            | 类型           | 约束                                            | 说明         |
| --------------- | -------------- | ----------------------------------------------- | ------------ |
| `id`            | `BIGINT`       | PK, AUTO_INCREMENT                              | 主键         |
| `enrollment_id` | `BIGINT`       | NOT NULL, UNIQUE, FK → `enrollments(id)`        | 关联选课记录 |
| `usual_score`   | `DECIMAL(5,2)` | NULL, CHECK `0~100`                             | 平时成绩     |
| `exam_score`    | `DECIMAL(5,2)` | NULL, CHECK `0~100`                             | 期末考试成绩 |
| `updated_by`    | `BIGINT`       | NULL, FK → `users(id)`                          | 成绩录入人   |
| `updated_at`    | `DATETIME`     | NOT NULL, DEFAULT `CURRENT_TIMESTAMP` ON UPDATE | 最后更新时间 |

> **设计要点**: `usual_score` 和 `exam_score` 允许为 NULL（表示尚未录入成绩），但一旦录入必须满足 0~100 范围。`grade_results` 视图自动计算 `final_score` 与绩点。

---

## 5. 通知与审计表

### notices — 通知公告表

系统公告/通知，按受众分类。

| 列名         | 类型                              | 约束                                  | 说明     |
| ------------ | --------------------------------- | ------------------------------------- | -------- |
| `id`         | `BIGINT`                          | PK, AUTO_INCREMENT                    | 主键     |
| `title`      | `VARCHAR(120)`                    | NOT NULL                              | 公告标题 |
| `content`    | `TEXT`                            | NOT NULL                              | 公告正文 |
| `audience`   | `ENUM('all','teacher','student')` | NOT NULL, DEFAULT `'all'`             | 目标受众 |
| `created_by` | `BIGINT`                          | NOT NULL, FK → `users(id)`            | 发布人   |
| `created_at` | `DATETIME`                        | NOT NULL, DEFAULT `CURRENT_TIMESTAMP` | 发布时间 |

> **权限控制**: 触发器 `trg_notices_admin_insert` 和 `trg_notices_admin_update` 强制要求发布人角色为 `admin`。

### transactions — 业务事务表

应用层事务追踪表，记录选课、退课、成绩录入等关键业务流程的完整生命周期。

| 列名             | 类型                                                 | 约束                                  | 说明                                                                                                           |
| ---------------- | ---------------------------------------------------- | ------------------------------------- | -------------------------------------------------------------------------------------------------------------- |
| `transaction_id` | `CHAR(36)`                                           | **PK**                                | UUID 格式事务标识                                                                                              |
| `business_type`  | `VARCHAR(64)`                                        | NOT NULL                              | 业务类型（`select_course`、`student_drop_course`、`admin_drop_course`、`admin_drop_enrollment`、`save_grade`） |
| `actor_user_id`  | `BIGINT`                                             | NULL, FK → `users(id)`                | 操作发起人                                                                                                     |
| `started_at`     | `DATETIME`                                           | NOT NULL, DEFAULT `CURRENT_TIMESTAMP` | 开始时间                                                                                                       |
| `ended_at`       | `DATETIME`                                           | NULL                                  | 结束时间                                                                                                       |
| `final_status`   | `ENUM('started','committed','rolled_back','failed')` | NOT NULL, DEFAULT `'started'`         | 最终状态                                                                                                       |
|                  |                                                      | **INDEX**: `(started_at)`             |                                                                                                                |
|                  |                                                      | **INDEX**: `(actor_user_id)`          |                                                                                                                |

> **设计要点**: 此表与存储过程配合使用——每个存储过程开始时插入一条 `started` 记录，成功提交后更新为 `committed`，异常处理中回滚后更新为 `rolled_back` 并记录错误信息。实现了"审计日志先写"模式，即使在异常情况下也能追踪到失败原因。

### transaction_log_entries — 事务日志表

事务操作的详细步骤日志，记录每一步 SQL 操作。

| 列名             | 类型                                                                    | 约束                                          | 说明              |
| ---------------- | ----------------------------------------------------------------------- | --------------------------------------------- | ----------------- |
| `id`             | `BIGINT`                                                                | PK, AUTO_INCREMENT                            | 主键              |
| `transaction_id` | `CHAR(36)`                                                              | NOT NULL, FK → `transactions(transaction_id)` | 关联事务          |
| `operation`      | `ENUM('START','INSERT','UPDATE','DELETE','UPSERT','COMMIT','ROLLBACK')` | NOT NULL                                      | 操作类型          |
| `table_name`     | `VARCHAR(64)`                                                           | NULL                                          | 操作的表名        |
| `record_id`      | `BIGINT`                                                                | NULL                                          | 操作的记录 ID     |
| `status`         | `ENUM('started','success','failed','rolled_back')`                      | NOT NULL                                      | 步骤执行状态      |
| `message`        | `TEXT`                                                                  | NULL                                          | 附加信息/错误消息 |
| `created_at`     | `DATETIME`                                                              | NOT NULL, DEFAULT `CURRENT_TIMESTAMP`         | 日志时间          |
|                  |                                                                         | **INDEX**: `(transaction_id, id)`             | 按事务查询日志    |
|                  |                                                                         | **INDEX**: `(created_at)`                     | 按时间查询日志    |

### backup_records — 备份记录表

数据库备份操作的记录表，追踪备份文件的状态和元信息。

| 列名               | 类型                                           | 约束                                       | 说明                  |
| ------------------ | ---------------------------------------------- | ------------------------------------------ | --------------------- |
| `id`               | `BIGINT`                                       | PK, AUTO_INCREMENT                         | 主键                  |
| `database_name`    | `VARCHAR(64)`                                  | NOT NULL                                   | 被备份的数据库名      |
| `file_name`        | `VARCHAR(255)`                                 | NOT NULL                                   | 备份文件名            |
| `backup_directory` | `VARCHAR(512)`                                 | NOT NULL                                   | 备份文件路径          |
| `file_size_bytes`  | `BIGINT`                                       | NULL, CHECK `>= 0`                         | 文件大小（字节）      |
| `status`           | `ENUM('started','success','failed','deleted')` | NOT NULL, DEFAULT `'started'`              | 备份状态              |
| `trigger_type`     | `ENUM('scheduled','manual')`                   | NOT NULL, DEFAULT `'scheduled'`            | 触发方式（定时/手动） |
| `created_by`       | `BIGINT`                                       | NULL, FK → `users(id)`                     | 发起人                |
| `started_at`       | `DATETIME`                                     | NOT NULL, DEFAULT `CURRENT_TIMESTAMP`      | 开始时间              |
| `ended_at`         | `DATETIME`                                     | NULL                                       | 结束时间              |
| `message`          | `TEXT`                                         | NULL                                       | 备注/错误信息         |
|                    |                                                | **联合唯一**: `(database_name, file_name)` |                       |
|                    |                                                | **INDEX**: `(started_at)`                  |                       |
|                    |                                                | **INDEX**: `(status)`                      |                       |

---

## 6. 触发器

### 学期日期校验触发器

#### trg_semesters_no_overlap_before_insert

- **时机**: `BEFORE INSERT ON semesters`
- **功能**: 确保新学期不与已有学期日期重叠；确保 `start_date <= end_date`

#### trg_semesters_no_overlap_before_update

- **时机**: `BEFORE UPDATE ON semesters`
- **功能**: 同 INSERT 校验，但排除自身 ID，防止误判自己与自己重叠

### 选课容量校验触发器

#### trg_enrollments_capacity_before_insert

- **时机**: `BEFORE INSERT ON enrollments`
- **功能**: 当新选课状态为 `selected` 时，校验当前已选人数是否已达课程容量上限。使用 `FOR UPDATE` 锁定课程记录防止并发超选。

#### trg_enrollments_capacity_before_update

- **时机**: `BEFORE UPDATE ON enrollments`
- **功能**: 当选课状态从 `dropped` 变为 `selected`、或更换开课班级时，重新校验容量。

### 开课修改校验触发器

#### trg_course_offerings_capacity_before_update

- **时机**: `BEFORE UPDATE ON course_offerings`
- **功能**: 修改开课记录时，执行三个维度的校验：
  1. **容量校验**: 新容量不得低于当前已选（`selected` 状态）人数
  2. **教师/教室时间冲突**: 修改后检查是否与同教师或同教室的其他开课在时间段上冲突（考虑 `day_of_week`、节次范围、周次范围、`week_type`）
  3. **学生课表冲突**: 已选该课的学生，修改后的时间是否与其他已选课程冲突

### 课程时间冲突校验触发器

#### trg_offering_times_schedule_after_insert / trg_offering_times_schedule_after_update

- **时机**: `AFTER INSERT/UPDATE ON course_offering_times`
- **功能**: 插入或修改课程时间安排时校验：
  1. **时间冲突**: 同一 `offering` 内部时间段不重叠；与同教师、同教室的其他开课不冲突
  2. **学生课表冲突**: 已选该课学生的其他课程在时间段上无冲突
- **冲突判定条件**（同时满足视为冲突）：
  - 同一天 (`day_of_week` 相同)
  - 节次重叠（不满足 `end_section < start_section` 或 `start_section > end_section`）
  - 周次重叠（不满足 `end_week < start_week` 或 `start_week > end_week`）
  - 周类型匹配（一方为 `all`、或双方相同）

### 通知权限校验触发器

#### trg_notices_admin_insert / trg_notices_admin_update

- **时机**: `BEFORE INSERT/UPDATE ON notices`
- **功能**: 校验 `created_by` 用户的角色编码必须为 `admin`，确保只有管理员能发布通知。

---

## 7. 视图

### course_offering_stats — 开课统计视图

提供每门开课的已选人数统计。

| 输出列           | 来源                           | 说明                                |
| ---------------- | ------------------------------ | ----------------------------------- |
| `id`             | `course_offerings.id`          | 开课记录 ID                         |
| `course_id`      | `course_offerings.course_id`   | 课程 ID                             |
| `semester_id`    | `course_offerings.semester_id` | 学期 ID                             |
| `teacher_id`     | `course_offerings.teacher_id`  | 教师 ID                             |
| `capacity`       | `course_offerings.capacity`    | 容量上限                            |
| `exam_ratio`     | `course_offerings.exam_ratio`  | 考试占比                            |
| `status`         | `course_offerings.status`      | 开课状态                            |
| `selected_count` | `COUNT(*)`                     | 当前已选人数（`status='selected'`） |

**SQL 逻辑**: 通过 `LEFT JOIN` 聚合同 `offering_id` 下 `status='selected'` 的选课记录数，无选课时返回 0。

### grade_results — 成绩结果视图

计算每位学生的最终成绩和对应绩点（GPA）。

| 输出列          | 说明           |
| --------------- | -------------- |
| `id`            | 成绩记录 ID    |
| `enrollment_id` | 选课记录 ID    |
| `usual_score`   | 平时成绩       |
| `exam_score`    | 期末考试成绩   |
| `final_score`   | 综合成绩       |
| `grade_point`   | 绩点（4.0 制） |
| `updated_by`    | 录入人         |
| `updated_at`    | 更新时间       |

**final_score 计算公式**:

```
ROUND(usual_score × (1 - exam_ratio) + exam_score × exam_ratio, 0)
```

**绩点映射表（5 分一段）**:

| 分数区间 | 绩点               |
| -------- | ------------------ |
| ≥ 90     | 4.0                |
| 85–89    | 3.7                |
| 82–84    | 3.3                |
| 78–81    | 3.0                |
| 75–77    | 2.7                |
| 72–74    | 2.3                |
| 68–71    | 2.0                |
| 66–67    | 1.7                |
| 64–65    | 1.5                |
| 60–63    | 1.0                |
| < 60     | 0.0（不及格）      |
| NULL     | NULL（成绩未录入） |

---

## 8. 存储过程

所有存储过程均遵循以下设计原则：

- **两阶段事务**: 先写入 `transactions` + `transaction_log_entries` 做审计预记录（短事务），再进行业务操作（长事务），确保异常时审计不丢失。
- **固定锁顺序**: 按 `students → semester_active_phases → course_offerings → enrollments → grades` 顺序获取行锁，避免死锁。
- **`FOR UPDATE`**: 关键数据行（学生、学期阶段、开课记录、选课记录）在操作前加排他锁。
- **`READ COMMITTED`**: 业务事务使用 `READ COMMITTED` 隔离级别以降低锁竞争。
- **异常处理**: 使用 `DECLARE EXIT HANDLER FOR SQLEXCEPTION` 捕获异常，回滚业务事务后在审计表中记录失败原因后重新抛出。

### sp_select_course — 学生选课

```
sp_select_course(
  IN p_student_id  BIGINT,   -- 学生 ID
  IN p_offering_id BIGINT,   -- 开课记录 ID
  IN p_actor_user_id BIGINT  -- 操作人用户 ID
)
```

**业务流程**:

1. 生成 UUID 作为事务 ID，写入 `transactions` 表（短事务提交）
2. 按序锁定并校验：
   - `students` — 学生是否存在
   - `semester_active_phases` — 选课阶段是否开放
   - `course_offerings` → `courses` → `semesters` — 开课记录是否存在及其关联信息
3. 业务规则校验（六项检查，任一项不通过则 `SIGNAL` 报错）：
   - **学期匹配**: 开课所属学期必须等于当前选课学期
   - **开课状态**: 开课状态必须为 `selecting`
   - **容量检查（第一次）**:
   - **重复选课**: 同一课程在同一学期不能重复选择
   - **已通过检查**: 同一课程若之前学期已通过（final_score ≥ 60），不可再选
   - **时间冲突**: 与已选课程的时间段不能冲突
   - **学分上限**: 当前学期已选学分 + 新课程学分 ≤ 学期学分上限
4. 容量二次检查（防止并发竞争）
5. `INSERT ... ON DUPLICATE KEY UPDATE` 写入选课记录
6. 更新 `transactions` 状态为 `committed`，提交事务

### sp_student_drop_course — 学生自主退课

```
sp_student_drop_course(
  IN p_student_id     BIGINT,  -- 学生 ID
  IN p_enrollment_id  BIGINT,  -- 选课记录 ID
  IN p_actor_user_id  BIGINT   -- 操作人用户 ID
)
```

**业务流程**:

1. 审计事务预记录
2. 校验选课记录属于该学生
3. 按序锁定 `students` → `semester_active_phases` → `course_offerings` → `enrollments`
4. 检查成绩表：如已有完整成绩（`usual_score` 和 `exam_score` 均不为 NULL），则不可退课
5. 业务规则校验：
   - **学期匹配**: 开课所属学期必须等于当前选课学期
   - **成绩限制**: 已出成绩的选课不可退
6. 更新 `enrollments.status = 'dropped'`
7. 更新事务状态为 `committed`

### sp_admin_drop_course — 管理员强制退课

```
sp_admin_drop_course(
  IN p_student_id    BIGINT,  -- 学生 ID
  IN p_offering_id   BIGINT,  -- 开课记录 ID
  IN p_actor_user_id BIGINT   -- 操作人用户 ID
)
```

与 `sp_student_drop_course` 的区别：

- 通过 `(student_id, offering_id)` 定位选课记录（而非 `(student_id, enrollment_id)`）
- 不检查成绩（管理员可强制退掉已有成绩的课程）
- 不检查学期阶段（管理员可在非选课阶段操作）

### sp_admin_drop_enrollment — 通过选课 ID 退课

```
sp_admin_drop_enrollment(
  IN p_enrollment_id BIGINT,  -- 选课记录 ID
  IN p_actor_user_id BIGINT   -- 操作人用户 ID
)
```

与 `sp_admin_drop_course` 类似，但直接通过 `enrollment_id` 定位，并从该记录中解析出 `student_id` 和 `offering_id`。

### sp_save_grade — 教师录入成绩

```
sp_save_grade(
  IN p_teacher_id    BIGINT,       -- 教师 ID
  IN p_enrollment_id BIGINT,       -- 选课记录 ID
  IN p_usual_score   DECIMAL(5,2), -- 平时成绩
  IN p_exam_score    DECIMAL(5,2), -- 期末考试成绩
  IN p_actor_user_id BIGINT        -- 操作人用户 ID
)
```

**业务流程**:

1. 参数校验：成绩必须在 0~100 范围内且不为 NULL
2. 按序锁定 `students` → `semester_active_phases` → `course_offerings` → `enrollments` → `grades`
3. 业务规则校验：
   - **教师归属**: 操作教师必须是该开课记录的授课教师
   - **学期匹配**: 开课所属学期必须等于当前成绩录入学期
4. `INSERT ... ON DUPLICATE KEY UPDATE` 写入/更新成绩
5. 更新事务状态为 `committed`

---

## 9. ER 关系总览

```
roles (1) ──────< (N) users
                        │
            ┌───────────┴───────────┐
            │ (1)                   │ (1)
            ▼                       ▼
        students               teachers
            │                       │
            │ (N:1) majors           │ (N:1) departments
            │         │              │
            │         │ (N:1)        │
            │    departments         │
            │                       │
            ▼                       ▼
        enrollments ◄──────── course_offerings ──────< (N) courses ──> departments
            │                       │
            │ (1:1)                 │ (1:N)
            ▼                       ▼
         grades            course_offering_times ──> classrooms
                                │
                                │ (N:1)
                                ▼
                           classrooms

semesters ──< semester_active_phases (1:1)
semesters ──< course_offerings

users ──< notices
users ──< transactions ──< transaction_log_entries
users ──< backup_records
```

---

## 附录：业务锁顺序

为避免死锁，所有数据修改操作必须遵循以下固定锁顺序：

```
1. students         (学生表)
2. semester_active_phases (学期阶段表)
3. course_offerings (开课记录表)
4. enrollments      (选课记录表)
5. grades           (成绩表)
```

该顺序在 `schema.sql` 第 557 行以注释形式标注，并在所有存储过程中严格遵循。
