# 教务管理系统数据库设计合理性分析

本文档对 `schema.sql` 中各表的数据项（列）及约束条件的合理性进行逐项分析，解释"为什么需要这个字段"和"为什么需要这个约束"。

---

## 目录

1. [roles — 角色表](#1-roles--角色表)
2. [users — 用户表](#2-users--用户表)
3. [departments — 院系表](#3-departments--院系表)
4. [majors — 专业表](#4-majors--专业表)
5. [students — 学生表](#5-students--学生表)
6. [teachers — 教师表](#6-teachers--教师表)
7. [semesters — 学期表](#7-semesters--学期表)
8. [semester_active_phases — 学期活跃阶段表](#8-semester_active_phases--学期活跃阶段表)
9. [courses — 课程表](#9-courses--课程表)
10. [classrooms — 教室表](#10-classrooms--教室表)
11. [course_offerings — 开课记录表](#11-course_offerings--开课记录表)
12. [course_offering_times — 课程时间安排表](#12-course_offering_times--课程时间安排表)
13. [enrollments — 选课记录表](#13-enrollments--选课记录表)
14. [grades — 成绩表](#14-grades--成绩表)
15. [notices — 通知公告表](#15-notices--通知公告表)
16. [transactions & transaction_log_entries — 事务审计表](#16-transactions--transaction_log_entries--事务审计表)
17. [backup_records — 备份记录表](#17-backup_records--备份记录表)
18. [存储过程设计约束分析](#18-存储过程设计约束分析)
19. [视图设计合理性](#19-视图设计合理性)
20. [整体架构设计思想总结](#20-整体架构设计思想总结)

---

## 1. roles — 角色表

### 字段合理性

| 字段 | 为什么需要 |
|------|-----------|
| `id` (BIGINT PK) | 自增主键，作为 `users` 表的外键引用目标。使用 BIGINT 而非 INT 是因为教务系统用户量可能较大，预留足够主键空间。 |
| `code` (VARCHAR(32) UNIQUE) | **机器可读的角色标识**，供后端代码做权限判断（如 `if role.code == 'admin'`）。与 `name` 分离的原因：`code` 是程序逻辑依赖的不可变标识，`name` 是可以随时调整的显示名称。 |
| `name` (VARCHAR(64)) | **人类可读的角色名称**（如"系统管理员"），用于界面展示。与 `code` 分离遵循了"标识与展示分离"原则。 |

### 约束合理性

| 约束 | 为什么需要 |
|------|-----------|
| `code UNIQUE` | 角色编码是权限判断的凭据，重复编码会导致权限逻辑混乱。如果两个角色共用同一个 `code`，代码无法区分其权限范围。 |
| `code NOT NULL` | 没有编码的角色无法被程序引用，形同虚设。 |
| `name NOT NULL` | 角色必须有一个可展示的名称，否则在用户管理界面无法区分。 |

> **设计思想**: 为什么需要独立的 `roles` 表而不是在 `users` 表中用一个 `role` 枚举字段？
>
> 因为角色可能随时间扩展（如增加"辅导员"、"督导"等角色），枚举字段 ALTER TABLE 代价高且有数据风险；独立表使角色管理更灵活，且可为每个角色关联更多元数据（如权限描述、创建时间等）。

---

## 2. users — 用户表

### 字段合理性

| 字段 | 为什么需要 |
|------|-----------|
| `id` (BIGINT PK) | 系统内部用户唯一标识，所有与用户关联的表（`students`、`teachers`、`notices`、`transactions` 等）都通过此 ID 建立关联。内部 ID 与业务编号（学号/工号）分离，避免业务编号变更导致大面积级联更新。 |
| `username` (VARCHAR(64) UNIQUE) | 登录凭据。长度 64 足以容纳学号、工号或自定义用户名。UNIQUE 确保不会有两个用户使用相同账号登录。 |
| `password_hash` (VARCHAR(255)) | 存储密码的哈希值（非明文）。255 长度兼容 bcrypt/argon2 等主流哈希算法输出。**绝不存储明文密码**，这是安全底线。 |
| `display_name` (VARCHAR(80)) | 用户真实姓名或昵称，在界面上显示。与 `username`（登录用）分离。80 字符足以容纳中文姓名（含少数民族复姓）和英文名。 |
| `email` (VARCHAR(120) UNIQUE) | 用于找回密码、发送通知。120 字符是 RFC 5321 规定的邮箱地址最大长度。UNIQUE 确保一个邮箱只能注册一个账号。 |
| `role_id` (FK → roles) | 将用户与角色关联。使用外键而非枚举的原因：① 角色可动态管理；② 支持未来扩展为"一个用户多角色"时只需改为关联表。 |
| `status` (ENUM 'enabled','disabled') | 软禁用机制——不需要删除用户数据即可禁止登录（如教职工离职、学生毕业）。比物理删除更安全，保留历史数据关联。 |
| `created_at` (DATETIME, DEFAULT CURRENT_TIMESTAMP) | 记录账号创建时间，用于审计和统计分析。默认值由数据库自动填充，确保数据一致性。 |

### 约束合理性

| 约束 | 为什么需要 |
|------|-----------|
| `username UNIQUE` | 登录系统的唯一标识，重复将导致无法区分用户。 |
| `email UNIQUE` | 邮箱是找回密码的唯一通道，一个邮箱绑定多个账号会导致安全漏洞。 |
| `role_id NOT NULL` | 每个用户必须有明确的角色，无角色用户无法确定权限范围，属于数据不完整。 |
| `status DEFAULT 'enabled'` | 新创建的用户默认启用，符合业务常识。 |

### 架构决策：基表 + 扩展表模式

为什么用户信息不全部放在 `users` 表中，而是拆分为 `users` → `students` / `teachers`？

- **避免稀疏列**: 如果放在一张表，学生没有"职称"，教师没有"学号"和"入学年份"，大量列为 NULL，浪费存储且查询语义不清晰。
- **职责分离**: 登录认证只需查 `users` 表；学生管理查 `students` 表；教师管理查 `teachers` 表。互不干扰。
- **扩展性**: 未来新增"辅导员"角色，只需新增 `counselors` 表，不影响现有结构。

---

## 3. departments — 院系表

### 字段合理性

| 字段 | 为什么需要 |
|------|-----------|
| `id` (BIGINT PK) | 院系唯一标识，被 `majors`、`teachers`、`courses` 等多张表引用。独立表使院系信息集中管理。 |
| `name` (VARCHAR(80) UNIQUE) | 院系名称（如"计算机科学与技术学院"）。UNIQUE 防止重复创建同名院系。80 字符足够容纳中文全称。 |
| `phone` (VARCHAR(32) NULL) | 院系办公电话。允许 NULL 因为并非所有院系信息录入时就有联系方式。 |

### 约束合理性

| 约束 | 为什么需要 |
|------|-----------|
| `name UNIQUE` | 业务上不应存在两个同名的院系，这是现实世界的唯一性约束在数据库中的映射。 |

---

## 4. majors — 专业表

### 字段合理性

| 字段 | 为什么需要 |
|------|-----------|
| `id` (BIGINT PK) | 专业唯一标识。 |
| `department_id` (FK → departments) | 专业必须归属于某个院系。这是现实世界关系的映射——每个专业由一个院系开设。 |
| `name` (VARCHAR(80)) | 专业名称。注意这里没有单独 UNIQUE 约束，而是与 `department_id` 联合唯一（见下方）。 |
| `duration_years` (TINYINT, DEFAULT 4) | 学制年限。TINYINT 足够（1~255），默认 4 年覆盖大多数本科专业。 |

### 约束合理性

| 约束 | 为什么需要 |
|------|-----------|
| `UNIQUE (department_id, name)` | 不同院系可以有同名专业（如多个学院都有"软件工程"专业），但在同一院系内专业名不可重复。这是精准的业务约束——全局唯一会误杀跨院系同名专业。 |
| `CHECK (duration_years BETWEEN 1 AND 8)` | 学制合理范围约束。1 年（短期培训性质）到 8 年（本硕博连读），超出此范围明显是数据录入错误。 |
| `department_id NOT NULL` | 专业脱离院系无意义。 |
| `duration_years NOT NULL, DEFAULT 4` | 大多数专业为 4 年制本科，提供合理默认值减少录入工作。 |

---

## 5. students — 学生表

### 字段合理性

| 字段 | 为什么需要 |
|------|-----------|
| `id` (BIGINT PK) | 学生内部 ID，被 `enrollments` 表引用。与 `user_id` 不同，`id` 是学生档案的内部标识。 |
| `user_id` (FK → users, UNIQUE) | 与登录账号一对一关联。UNIQUE 确保一个账号只能绑定一个学生档案。 |
| `student_no` (VARCHAR(32) UNIQUE) | 学号，学校分配的唯一业务标识。与 `user_id` 分离——学号可能因转专业等变更，但内部 ID 保持不变。 |
| `major_id` (FK → majors) | 学生所属专业。这是学生的核心属性，选课、毕业审核都依赖此字段。 |
| `admission_year` (SMALLINT) | 入学年份。SMALLINT 足够（范围 -32768~32767）。配合学制年限可自动计算预计毕业年份；也用于按年级筛选学生。 |

### 约束合理性

| 约束 | 为什么需要 |
|------|-----------|
| `user_id UNIQUE` | 一个登录账号只能对应一个学生档案。 |
| `student_no UNIQUE` | 学号在学校内必须唯一。 |
| `CHECK (admission_year BETWEEN 1900 AND 2100)` | 排除明显不合理的数据（如录入错误导致年份为 200 或 9999）。范围宽泛覆盖所有可能的历史数据和未来数据。 |
| `major_id NOT NULL` | 学生必须有专业归属，否则选课、毕业审核等业务无法进行。 |
| `admission_year NOT NULL` | 年级信息是所有教务统计（在籍人数、毕业审核等）的基础维度。 |

---

## 6. teachers — 教师表

### 字段合理性

| 字段 | 为什么需要 |
|------|-----------|
| `id` (BIGINT PK) | 教师内部 ID，被 `course_offerings` 表引用为授课教师。 |
| `user_id` (FK → users, UNIQUE) | 与登录账号一对一关联。 |
| `teacher_no` (VARCHAR(32) UNIQUE) | 教师工号，学校人事系统的唯一标识。 |
| `department_id` (FK → departments) | 教师所属院系。用于按院系统计师资，也用于课程归属院系的关联查询。 |
| `title` (VARCHAR(64), DEFAULT '讲师') | 职称（教授、副教授、讲师、助教等）。默认值"讲师"覆盖最常见的初始职称。 |

### 约束合理性

| 约束 | 为什么需要 |
|------|-----------|
| `user_id UNIQUE` | 一对一关联，同 `students` 表。 |
| `teacher_no UNIQUE` | 工号唯一性。 |
| `department_id NOT NULL` | 教师必须归属某个院系。 |
| `title NOT NULL, DEFAULT '讲师'` | 职称是教师的基础属性，不允许为 NULL；默认值减少新教师录入时的工作量。 |

---

## 7. semesters — 学期表

### 字段合理性

| 字段 | 为什么需要 |
|------|-----------|
| `id` (BIGINT PK) | 学期唯一标识，被 `semester_active_phases` 和 `course_offerings` 引用。 |
| `name` (VARCHAR(64) UNIQUE) | 学期名称（如"2024-2025学年第1学期"）。UNIQUE 防止重复创建。 |
| `start_date` (DATE) | 学期开始日期，定义学期的精确边界。用于判断日期是否在学期范围内。 |
| `end_date` (DATE) | 学期结束日期。与 `start_date` 共同定义学期的时间跨度。 |
| `max_credit` (DECIMAL(5,1), DEFAULT 30.0) | 该学期学生可选总学分上限。不同学期可能有不同的学分政策（如大一上学期限制 25 学分，高年级放宽到 35 学分）。使用 DECIMAL(5,1) 而非 INT 是因为学分可能有 0.5 学分的情况。 |

### 约束合理性

| 约束 | 为什么需要 |
|------|-----------|
| `name UNIQUE` | 业务上不会有两个同名学期。 |
| `CHECK (start_date <= end_date)` | **基本逻辑校验**——开始日期不能晚于结束日期。同时触发器 `trg_semesters_no_overlap_*` 进一步确保学期之间不重叠。 |
| `CHECK (max_credit > 0)` | 学分为负数或零无意义，属于数据异常。 |
| `start_date NOT NULL` / `end_date NOT NULL` | 没有起止日期的学期无法参与任何时间相关判断。 |

### 触发器补充说明

`trg_semesters_no_overlap_before_insert` 和 `trg_semesters_no_overlap_before_update` 确保学期时间段互不重叠。这是强制性业务规则：
- 某一天不能同时属于两个学期，否则该日期的数据无法确定归属学期。
- 虽然表级 CHECK 约束能验证 `start_date <= end_date`，但**跨行唯一性约束**（时间段互不重叠）只能通过触发器或应用层实现，因为 MySQL 的 CHECK 约束不支持子查询。

---

## 8. semester_active_phases — 学期活跃阶段表

### 字段合理性

| 字段 | 为什么需要 |
|------|-----------|
| `phase` (ENUM 'selection','grading', PK) | 阶段类型——选课阶段和成绩录入阶段。ENUM 作为主键确保每种阶段最多一条记录（即最多两条记录）。 |
| `semester_id` (FK → semesters, UNIQUE) | 指向当前活跃的学期。 |

### 约束合理性

| 约束 | 为什么需要 |
|------|-----------|
| `phase` 作为主键 | 使用 ENUM 列作为主键是一个巧妙的设计——它利用主键的唯一性自动保证每种阶段类型只能有一条记录。这意味着系统同时只有**一个选课学期**和**一个成绩录入学期**。 |
| `semester_id UNIQUE` | 确保每个学期最多被引用一次——不同阶段不能指向同一个学期（因为选课和成绩录入不应在同一学期同时开放）。 |
| `ON DELETE CASCADE` | 当学期被删除时，对应的阶段引用也自动清除，避免悬垂引用。 |

> **设计思想**: 这是一个"全局状态"表，充当系统的**业务开关**。存储过程通过检查此表来决定是否允许选课或录入成绩，无需在应用层维护全局变量。

---

## 9. courses — 课程表

### 字段合理性

| 字段 | 为什么需要 |
|------|-----------|
| `id` (BIGINT PK) | 课程内部 ID。 |
| `code` (VARCHAR(32) UNIQUE) | 课程编号（如 "CS101"），学校课程目录中的编码，具有业务含义。UNIQUE 确保课程体系的一致性和可追溯性。 |
| `name` (VARCHAR(100)) | 课程名称。100 字符足够容纳中文全称。 |
| `department_id` (FK → departments) | 开课院系，标识课程的"归属单位"。用于按院系统计课程资源。 |
| `credit` (DECIMAL(3,1)) | 课程学分。DECIMAL(3,1) 支持 0.5 学分（如实验课 1.5 学分），范围 0.1~999.9。 |
| `status` (ENUM 'enabled','disabled') | 课程软开关。已停开的课程保留历史记录但不再参与新开课安排。 |

### 约束合理性

| 约束 | 为什么需要 |
|------|-----------|
| `code UNIQUE` | 课程编码全局唯一。 |
| `CHECK (credit > 0)` | 学分必须为正数。 |
| `department_id NOT NULL` | 每门课程必须有归属院系。 |
| `credit NOT NULL` | 学分是课程核心属性，与选课、毕业审核、GPA 计算都直接相关。 |
| `status DEFAULT 'enabled'` | 新增课程默认可选。 |

---

## 10. classrooms — 教室表

### 字段合理性

| 字段 | 为什么需要 |
|------|-----------|
| `id` (BIGINT PK) | 教室内部 ID。 |
| `building` (VARCHAR(64)) | 教学楼名称。 |
| `room_no` (VARCHAR(32)) | 房间号。与 `building` 分离而非合并为一个字段——方便按教学楼筛选、统计各教学楼教室数量。 |

### 约束合理性

| 约束 | 为什么需要 |
|------|-----------|
| `UNIQUE (building, room_no)` | 同一教学楼内房间号不能重复，但不同教学楼可以有同名房间号（如"A101"和"B101"）。这是精准的联合唯一约束。 |
| `building NOT NULL` / `room_no NOT NULL` | 缺少任一部分都无法定位教室。 |

---

## 11. course_offerings — 开课记录表

### 字段合理性

| 字段 | 为什么需要 |
|------|-----------|
| `id` (BIGINT PK) | 开课记录内部 ID。 |
| `course_id` (FK → courses) | 指向课程目录中的哪门课。同一门课可以在不同学期多次开设。 |
| `semester_id` (FK → semesters) | 该开课所属学期。 |
| `teacher_id` (FK → teachers) | 授课教师。注意这里是 `teacher_id` 而非 `user_id`，因为并非所有用户都是教师。 |
| `capacity` (SMALLINT) | 选课容量上限。SMALLINT（-32768~32767）足够。选课存储过程据此判断是否还有余量。 |
| `exam_ratio` (DECIMAL(4,2), DEFAULT 0.60) | 考试成绩在总评中的占比。不同课程可能有不同的考核方式（如实验课 exam_ratio 较低，理论课较高）。默认 0.60 即考试成绩占 60%、平时成绩占 40%。 |
| `status` (ENUM 'selecting','closed','deleted') | 开课生命周期状态。使用三态而非二态：`selecting`（可选）、`closed`（不可选但保留记录）、`deleted`（逻辑删除）。区分 `closed` 和 `deleted` 便于区分"正常结束的课程"和"误创建需要删除的课程"。 |

### 约束合理性

| 约束 | 为什么需要 |
|------|-----------|
| `CHECK (capacity > 0)` | 容量为零的课程无意义。 |
| `CHECK (exam_ratio >= 0 AND exam_ratio <= 1)` | 考试占比必须是合法的比例值（0%~100%）。 |
| `exam_ratio DEFAULT 0.60` | 最常见的考核比例：60%考试 + 40%平时。 |
| 所有外键 NOT NULL | 缺少课程、学期或教师信息的开课记录无法运作。 |

### 架构决策：为什么 course_offerings 不合并到 courses 表中？

`courses` 是**课程模板/目录**，`course_offerings` 是**课程实例**。例如"高等数学"这门课（courses 中一条记录）可能在 2024 秋季学期由张老师开设（一个 offering）、2025 春季学期由李老师开设（另一个 offering）。分为两张表是为了：
- 课程元信息（编号、名称、学分）不随每次开设变化，避免冗余。
- 不同的开设可以有不同的容量、考试比例、授课教师。

---

## 12. course_offering_times — 课程时间安排表

### 字段合理性

| 字段 | 为什么需要 |
|------|-----------|
| `id` (BIGINT PK) | 时间段记录内部 ID。 |
| `offering_id` (FK → course_offerings, ON DELETE CASCADE) | 所属开课记录。CASCADE 删除：删除开课时自动清除时间段。 |
| `classroom_id` (FK → classrooms) | 上课教室。 |
| `day_of_week` (TINYINT, 1~7) | 星期几（1=周一, 7=周日）。使用 TINYINT 而非 ENUM 便于数值比较和范围计算。 |
| `start_section` / `end_section` (TINYINT, 1~12) | 节次范围。一天最多 12 节课覆盖绝大多数高校排课场景（上午 4 节 + 下午 4 节 + 晚上 3 节 = 11 节，预留到 12）。 |
| `start_week` / `end_week` (TINYINT, 1~30) | 教学周范围。默认 1~16 周，30 周的上限覆盖最长学期（含考试周和小学期）。 |
| `week_type` (ENUM 'all','odd','even') | 单双周标识。高校常见的排课方式——某些课程仅单周或双周上课（如实验课与理论课交替）。 |

### 约束合理性

| 约束 | 为什么需要 |
|------|-----------|
| `CHECK (day_of_week BETWEEN 1 AND 7)` | 限制在合法星期范围内。 |
| `CHECK (start_section BETWEEN 1 AND 12)` | 节次合法范围。 |
| `CHECK (end_section BETWEEN 1 AND 12)` | 同上。 |
| `CHECK (start_section <= end_section)` | 开始节次必须在结束节次之前。 |
| `CHECK (start_week BETWEEN 1 AND 30)` / `(end_week BETWEEN 1 AND 30)` | 周次合法范围。 |
| `CHECK (start_week <= end_week)` | 开始周必须在结束周之前。 |
| `INDEX (offering_id, day_of_week, start_section, end_section)` | 查询某课程的所有时间段，高频访问模式。 |
| `INDEX (classroom_id, day_of_week, start_section, end_section)` | 教室冲突检测的核心索引——查询某教室在某时间段是否已被占用。触发器中的冲突检测严重依赖此索引。 |

### 设计细节：为什么拆分为独立的 times 表？

一门课程可能有多个时间段（如周一 3-4 节在 A101 上理论课 + 周三 5-6 节在 B203 上实验课）。如果直接在 `course_offerings` 中冗余存储，会造成：
- 字段爆炸（多个 time_1_*、time_2_* 字段）
- 无法灵活支持不同时间数量
- 冲突检测难度指数级增加

独立表使每个时间段都是独立的行，查询、冲突检测都基于标准 SQL，逻辑清晰。

---

## 13. enrollments — 选课记录表

### 字段合理性

| 字段 | 为什么需要 |
|------|-----------|
| `id` (BIGINT PK) | 选课记录内部 ID。 |
| `student_id` (FK → students) | 选课学生。 |
| `offering_id` (FK → course_offerings) | 所选的开课记录。 |
| `status` (ENUM 'selected','dropped') | 选课状态。使用 ENUM 而非布尔值——虽目前只有两种状态，但未来可能扩展（如 `auditing` 旁听、`waitlisted` 候补）。ENUM 比 TINYINT 自解释性更强。 |

### 约束合理性

| 约束 | 为什么需要 |
|------|-----------|
| `UNIQUE (student_id, offering_id)` | 同一学生不能对同一开课重复选课。这是核心业务约束——即使历史上退过课，也只能有一条记录（退课后再次选课通过触发器将 `dropped` 更新为 `selected`）。 |
| `INDEX (offering_id, status)` | 查询某课程当前选课人数（`COUNT(*) WHERE status='selected'`），这是容量检查的高频操作。 |
| `INDEX (student_id, status)` | 查询某学生当前的选课列表，也是高频操作。 |

### 架构决策：逻辑删除（status='dropped'）vs 物理删除

退课使用状态标记而非物理删除行：
- **历史追踪**: 保留选课-退课历史，便于后续分析（退课率、课程热度等）。
- **审计合规**: 教育数据通常需要保留完整的操作痕迹。
- **数据恢复**: 误操作退课可快速恢复，物理删除则需从备份还原。
- **触发器和存储过程只需 UPDATE**: 比 DELETE + INSERT 逻辑更简单。

---

## 14. grades — 成绩表

### 字段合理性

| 字段 | 为什么需要 |
|------|-----------|
| `id` (BIGINT PK) | 成绩记录内部 ID。 |
| `enrollment_id` (FK → enrollments, UNIQUE) | 与选课记录一对一关联。UNIQUE 确保每个选课最多一条成绩记录。 |
| `usual_score` (DECIMAL(5,2) NULL) | 平时成绩。允许 NULL 表示成绩尚未录入。DECIMAL(5,2) 支持两位小数。 |
| `exam_score` (DECIMAL(5,2) NULL) | 期末考试成绩。同上。 |
| `updated_by` (FK → users, NULL) | 成绩录入人（教师），用于审计追溯——谁在什么时候录入了成绩。 |
| `updated_at` (DATETIME, ON UPDATE CURRENT_TIMESTAMP) | 最后修改时间，自动更新。用于追踪成绩变更时间线。 |

### 约束合理性

| 约束 | 为什么需要 |
|------|-----------|
| `enrollment_id UNIQUE` | 一个选课记录最多一个成绩。 |
| `CHECK (usual_score IS NULL OR usual_score BETWEEN 0 AND 100)` | 如果已录入，则必须在 0~100 范围内。使用 `IS NULL OR` 的写法允许 NULL（未录入状态）通过约束检查。 |
| `CHECK (exam_score IS NULL OR exam_score BETWEEN 0 AND 100)` | 同上。 |
| `updated_by NULL` | 允许 NULL——如果成绩是系统迁移导入的，可能没有录入人信息。 |

### 架构决策：为什么 grades 与 enrollments 分离？

- **选课与成绩是不同生命周期阶段**: 学生选课后可能退课（无需成绩），也可能选课后教师尚未录入成绩。
- **职责分离**: 选课管理关注容量和冲突，成绩管理关注分数和绩点计算。
- **一对一关系**: 一个选课记录最多一个成绩，`enrollment_id UNIQUE` 完美建模。
- **允许分批录入**: 平时成绩和考试成绩可以分别录入（任一可为 NULL）。

---

## 15. notices — 通知公告表

### 字段合理性

| 字段 | 为什么需要 |
|------|-----------|
| `id` (BIGINT PK) | 通知内部 ID。 |
| `title` (VARCHAR(120)) | 通知标题。120 字符足以容纳中文标题。 |
| `content` (TEXT) | 通知正文。TEXT 类型支持长文本（最多 65535 字节），满足公告正文需求。 |
| `audience` (ENUM 'all','teacher','student') | 目标受众。按角色推送不同通知——教师看到教学安排通知，学生看到选课通知，全体用户看到系统维护通知。 |
| `created_by` (FK → users) | 发布人。触发器强制校验发布人必须是管理员。 |
| `created_at` (DATETIME, DEFAULT CURRENT_TIMESTAMP) | 发布时间。 |

### 约束合理性

| 约束 | 为什么需要 |
|------|-----------|
| `created_by NOT NULL` | 必须有发布人。 |
| `audience DEFAULT 'all'` | 默认面向全体用户。 |

### 触发器补充说明

`trg_notices_admin_insert` 和 `trg_notices_admin_update` 在数据库层面强制只有管理员角色（`role.code = 'admin'`）的用户才能发布和修改通知。这是**数据库级别的权限控制**——即使应用层有 bug 绕过了权限检查，数据库层仍能兜底，满足纵深防御原则。

---

## 16. transactions & transaction_log_entries — 事务审计表

### 为什么需要应用层事务追踪？

MySQL 的 `COMMIT`/`ROLLBACK` 只保证 ACID，但不会告诉你"上一次选课操作因为什么原因失败了"。在教务系统中，以下场景需要可追溯的审计日志：

- 学生投诉"明明选了课为什么没选上"
- 教师质疑"为什么我的学生成绩被覆盖了"
- 管理员排查"谁在什么时候进行了批量退课操作"

### transactions 表字段合理性

| 字段 | 为什么需要 |
|------|-----------|
| `transaction_id` (CHAR(36) PK) | UUID v4 格式，全局唯一，不依赖自增序列（适合分布式场景）。 |
| `business_type` (VARCHAR(64)) | 区分不同业务操作（选课、退课、录成绩），便于按业务类型筛选审计日志。 |
| `actor_user_id` (FK → users, NULL) | 操作发起人。允许 NULL 以防系统自动任务触发。 |
| `started_at` / `ended_at` | 记录事务起止时间——可以分析操作耗时，发现性能瓶颈。 |
| `final_status` (ENUM) | 四种状态：`started`（进行中）、`committed`（成功）、`rolled_back`（回滚）、`failed`（异常终止）。 |

### transaction_log_entries 表字段合理性

| 字段 | 为什么需要 |
|------|-----------|
| `transaction_id` (FK → transactions) | 关联到事务。 |
| `operation` (ENUM 7种操作类型) | 区分不同 DML 操作——"INSERT into enrollments"、"UPDATE grades" 等。与 `table_name`、`record_id` 配合可精确还原每一步数据变更。 |
| `table_name` (VARCHAR(64) NULL) | 被操作的表。ROLLBACK 和 COMMIT 等元操作不涉及具体表，故允许 NULL。 |
| `record_id` (BIGINT NULL) | 被操作的记录 ID，配合 `table_name` 可定位到具体数据行。 |
| `status` (ENUM) | 每个步骤的执行结果。 |
| `message` (TEXT NULL) | 附加信息——成功时记录上下文，失败时记录错误消息。 |
| `created_at` | 日志时间戳，按时间还原操作顺序。 |

### 设计模式：审计日志先写 (Audit-First Pattern)

存储过程的设计模式：
1. **第一个短事务**: INSERT `transactions` + `transaction_log_entries`，立即 COMMIT。
2. **第二个长事务**: 执行业务逻辑。
3. **异常处理**: 在 EXIT HANDLER 中 ROLLBACK 业务事务，然后用一个独立短事务更新 `transactions` 状态并记录失败日志后 COMMIT。

这种设计的精妙之处：
- 即使业务事务失败回滚，**审计记录已经持久化**（因为第一步是短事务已提交）。
- 不会出现"操作失败了但没有任何记录"的审计盲区。
- 错误信息被记录在 `transaction_log_entries.message` 中，运维人员可以直接从数据库查到失败原因。

---

## 17. backup_records — 备份记录表

### 字段合理性

| 字段 | 为什么需要 |
|------|-----------|
| `id` (BIGINT PK) | 备份记录内部 ID。 |
| `database_name` (VARCHAR(64)) | 被备份的数据库名。 |
| `file_name` (VARCHAR(255)) | 备份文件名。 |
| `backup_directory` (VARCHAR(512)) | 备份文件存储路径。512 字符适应深层目录结构。 |
| `file_size_bytes` (BIGINT NULL) | 备份文件大小，用于容量监控。NULL 表示备份尚未完成或文件大小未知。 |
| `status` (ENUM 'started','success','failed','deleted') | 备份生命周期状态。`deleted` 用于标记已清理的过期备份。 |
| `trigger_type` (ENUM 'scheduled','manual') | 区分定时自动备份和手动触发的备份。 |
| `created_by` (FK → users, NULL) | 手动备份的发起人。定时备份时为 NULL。 |
| `started_at` / `ended_at` | 备份耗时，用于监控备份性能。 |
| `message` (TEXT NULL) | 备份失败时的错误信息。 |

### 约束合理性

| 约束 | 为什么需要 |
|------|-----------|
| `UNIQUE (database_name, file_name)` | 同一数据库不会产生同名备份文件。 |
| `CHECK (file_size_bytes IS NULL OR file_size_bytes >= 0)` | 文件大小不能为负数。允许 NULL。 |
| `INDEX (started_at)` | 按时间查看备份历史。 |
| `INDEX (status)` | 快速查找失败或进行中的备份。 |

---

## 18. 存储过程设计约束分析

### 为什么使用存储过程而非应用层代码实现业务逻辑？

五个存储过程封装了核心业务逻辑：

1. `sp_select_course` — 学生选课
2. `sp_student_drop_course` — 学生自主退课
3. `sp_admin_drop_course` — 管理员强制退课
4. `sp_admin_drop_enrollment` — 通过选课 ID 退课
5. `sp_save_grade` — 教师录入成绩

使用存储过程的理由：

| 理由 | 说明 |
|------|------|
| **原子性保证** | 选课涉及多表读写和复杂条件判断，存储过程中的事务管理确保要么全部成功要么全部回滚。 |
| **减少网络往返** | 应用层实现需要多次 SELECT + 判断 + INSERT/UPDATE，网络往返次数多；存储过程一次调用完成所有操作。 |
| **并发安全** | `FOR UPDATE` 行锁 + 固定锁顺序 + 容量二次检查，有效防止超选。应用层很难精确控制锁的时序。 |
| **审计一致性** | 审计日志先写模式要求事务管理与业务逻辑紧密配合，存储过程能精确控制每个 COMMIT 的时间点。 |
| **权限最小化** | 应用层账号只需 `EXECUTE` 权限，无需对表的直接 INSERT/UPDATE/DELETE 权限。 |

### 固定锁顺序的必要性

```
students → semester_active_phases → course_offerings → enrollments → grades
```

这是数据库并发控制的经典实践——**所有事务按相同顺序获取锁，杜绝死锁**。

假设两个并发事务：
- 事务 A 先锁 students 再锁 enrollments
- 事务 B 先锁 enrollments 再锁 students

如果 A 锁了 students 等 enrollments，同时 B 锁了 enrollments 等 students，形成死锁。**固定锁顺序**消除了这种循环等待的可能性。

### 具体业务规则的合理性

#### 选课时的六项校验

| 校验项目 | 为什么需要 |
|-----------|-----------|
| 学期必须为当前选课学期 | 防止学生在不对的学期选课（如暑假期间选了秋季课程但尚未开放）。 |
| 开课状态必须为 selecting | 已关闭或已删除的课程不应有新选课。 |
| 容量未满（两次检查） | **双重检查锁定模式**的应用——第一次检查在锁定后、条件验证之前，第二次检查在条件验证之后、写入之前。防止 TOCTOU（Time-of-check to Time-of-use）竞争条件。 |
| 不能重复选同一门课 | 同一学期选了两次"高等数学"（不同老师的不同 offering）通常不被允许。 |
| 已通过的课程不能重选 | 高数上学期已及格，下学期不能再选一遍刷分。这是教务制度在数据库层的硬编码。 |
| 课程时间不能冲突 | 两门课在同一时间段，学生分身乏术。 |
| 学期总学分不超过上限 | 防止学生超负荷选课，维护教学秩序。 |

#### 退课时的成绩校验

- **学生自主退课**: 已有完整成绩的课程不可退。因为成绩已经产生效力（可能已用于 GPA 计算或奖学金评定）。
- **管理员退课**: 无此限制，因为管理员需要处理异常情况（如成绩录入错误需撤销）。

#### 成绩录入时的教师归属校验

`teacher_id` 必须等于 `course_offerings.teacher_id`——只有开课教师和其指定的助教/教师团队才能录入成绩，防止越权篡改他人课程的成绩。

---

## 19. 视图设计合理性

### course_offering_stats — 开课统计视图

**为什么需要视图而不是直接查表？**

将开课信息与选课人数统计封装为一个视图：
- **消除重复 SQL**: 多个页面（课程列表、选课管理、统计分析）都需要查"课程容量和已选人数"，视图一次定义到处使用。
- **LEFT JOIN + 子查询聚合**: 未有人选的课程 `selected_count = 0`（而非消失），数据完整性更好。

### grade_results — 成绩结果视图

**为什么需要视图？**

- **计算逻辑封装**: `final_score` 和 `grade_point` 的计算规则较复杂（涉及从 `course_offerings` 获取 `exam_ratio`）。视图将计算逻辑封装在数据库层，确保所有消费方（报表、导出、API）使用同一套计算标准。
- **派生数据不存储**: `final_score` 是派生数据——当 `usual_score` 或 `exam_score` 更新时自动重新计算，不存在"源数据和派生数据不一致"的问题。
- **绩点映射表集中管理**: 绩点转换规则（60→1.0, 90→4.0 等）在视图中统一定义，如需调整只需修改视图定义。

---

## 20. 整体架构设计思想总结

### 1. 分层建模

```
基础层  → roles, users
档案层  → students, teachers, departments, majors
资源层  → courses, classrooms, semesters
业务层  → course_offerings, course_offering_times, enrollments, grades
管理层  → notices, transactions, transaction_log_entries, backup_records, semester_active_phases
```

每层依赖下层，形成清晰的依赖方向，避免了跨层循环引用。

### 2. 软删除与逻辑状态

几乎所有"删除"操作都是逻辑性的：
- `courses.status = 'disabled'`
- `course_offerings.status = 'deleted'`
- `enrollments.status = 'dropped'`
- `users.status = 'disabled'`

这体现了**教务数据的不可销毁性**——学生成绩、选课记录等信息具有长期保存价值和法律合规要求，物理删除会带来不可逆的数据损失。

### 3. 数据库层约束 vs 应用层约束

设计将大量业务约束下沉到数据库层（CHECK 约束、触发器、存储过程），而非仅依赖应用代码：

- **CHECK 约束**: 最基本的数值范围、格式校验，防止脏数据写入。
- **触发器**: 跨行复杂约束（学期不重叠、容量不超限、时间不冲突、权限校验），这些约束应用层也可能实现，但数据库层实现是最后防线。
- **存储过程**: 多步骤事务性操作，确保原子性和一致性。
- **外键约束**: 引用完整性，防止孤儿记录。

这种**纵深防御**策略确保即使应用层有 bug，数据库层面仍能维护数据一致性。

### 4. 审计优先设计

`transactions` + `transaction_log_entries` 构成了完整的操作审计链，并通过"审计先写"模式确保：**无论操作成功与否，审计记录都不会丢失**。这是企业级应用的标准做法。

### 5. 适度反范式化

注意到 `course_offering_times` 中存储了 `day_of_week`、`start_section` 等看似可以从其他表计算得出的字段——这是合理的反范式化，因为排课冲突检测是高频操作，如果每次都要 JOIN 多张表计算时间范围，性能会严重下降。直接存储在 `course_offering_times` 中并建立复合索引，使冲突检测查询走索引覆盖，性能最优。
