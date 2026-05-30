# <center>教务管理系统项目设计总结文档</center>

本文档从<font color="red" size=2>**概念设计 → 逻辑设计 → 物理设计**</font>三个层次，对教务管理系统（eduAffairSQL）的完整架构进行说明。

---

## 目录

- [一、概念设计 — ER 图](#一概念设计--er-图)
- [二、逻辑设计 — 各表结构](#二逻辑设计--各表结构)
- [三、物理设计 — 管理员、教师、学生各端功能实现](#三物理设计--管理员教师学生各端功能实现)

---

## <font color="red">一、概念设计 — ER 图</font>

### 1.1 实体识别与核心关系

教务管理系统的核心业务围绕<strong>（学生）在什么时间（学期）选了哪位老师（教师）教的什么课（开课记录）</strong>展开。由此识别出以下核心实体：

| 实体                   | 说明                 | 核心属性                                           |
| ---------------------- | -------------------- | -------------------------------------------------- |
| **User**               | 系统用户（登录账号） | username, password_hash, email, role               |
| **Role**               | 用户角色             | code (admin/teacher/student)                       |
| **Student**            | 学生档案             | student_no, admission_year                         |
| **Teacher**            | 教师档案             | teacher_no, title                                  |
| **Department**         | 院系                 | name, phone                                        |
| **Major**              | 专业                 | name, duration_years                               |
| **Course**             | 课程目录             | code, name, credit                                 |
| **Classroom**          | 教室                 | building, room_no                                  |
| **Semester**           | 学期                 | name, start_date, end_date, max_credit             |
| **CourseOffering**     | 开课记录（课程实例） | capacity, exam_ratio, status                       |
| **CourseOfferingTime** | 上课时间安排         | day_of_week, start_section, end_section, week_type |
| **Enrollment**         | 选课记录             | status (selected/dropped)                          |
| **Grade**              | 成绩记录             | usual_score, exam_score                            |
| **Notice**             | 通知公告             | title, content, audience                           |
| **Transaction**        | 业务事务（审计）     | business_type, final_status                        |

### 1.2 全局关系图

```mermaid
flowchart TB
    %% ===================== 样式 =====================
    classDef entity fill:#ffffff,stroke:#2563eb,stroke-width:2px,color:#111827;
    classDef relation fill:#fff7ed,stroke:#ea580c,stroke-width:2px,color:#111827;

    %% ===================== 实体 =====================
    角色[角色]
    用户[用户]
    院系[院系]
    专业[专业]
    学生[学生]
    教师[教师]
    学期[学期]
    学期活动阶段[学期活动阶段]
    课程[课程]
    教室[教室]
    开课信息[开课信息]
    开课时间[开课时间]
    选课记录[选课记录]
    成绩[成绩]
    公告[公告]
    事务[事务]
    事务日志[事务日志]
    备份记录[备份记录]

    %% ===================== 关系 - 组织与人员 =====================
    拥有角色{拥有角色}
    用户档案学生{对应学生档案}
    用户档案教师{对应教师档案}
    院系开设专业{开设专业}
    专业包含学生{包含学生}
    院系管理教师{管理教师}
    院系开设课程{开设课程}

    角色 -- "1" --> 拥有角色
    拥有角色 -- "N" --> 用户

    用户 -- "1" --> 用户档案学生
    用户档案学生 -- "0..1" --> 学生

    用户 -- "1" --> 用户档案教师
    用户档案教师 -- "0..1" --> 教师

    院系 -- "1" --> 院系开设专业
    院系开设专业 -- "N" --> 专业

    专业 -- "1" --> 专业包含学生
    专业包含学生 -- "N" --> 学生

    院系 -- "1" --> 院系管理教师
    院系管理教师 -- "N" --> 教师

    院系 -- "1" --> 院系开设课程
    院系开设课程 -- "N" --> 课程

    %% ===================== 关系 - 课程教学 =====================
    学期当前阶段{设置当前阶段}
    课程安排开课{安排开课}
    学期包含开课{包含开课}
    教师授课{授课}
    开课安排时间{安排时间}
    教室作为地点{作为上课地点}

    学期 -- "1" --> 学期当前阶段
    学期当前阶段 -- "0..1" --> 学期活动阶段

    课程 -- "1" --> 课程安排开课
    课程安排开课 -- "N" --> 开课信息

    学期 -- "1" --> 学期包含开课
    学期包含开课 -- "N" --> 开课信息

    教师 -- "1" --> 教师授课
    教师授课 -- "N" --> 开课信息

    开课信息 -- "1" --> 开课安排时间
    开课安排时间 -- "N" --> 开课时间

    教室 -- "1" --> 教室作为地点
    教室作为地点 -- "N" --> 开课时间

    %% ===================== 关系 - 选课与成绩 =====================
    学生选课{选课}
    开课被选{被选}
    生成成绩{生成成绩}
    用户录入成绩{录入或更新成绩}

    学生 -- "1" --> 学生选课
    学生选课 -- "N" --> 选课记录

    开课信息 -- "1" --> 开课被选
    开课被选 -- "N" --> 选课记录

    选课记录 -- "1" --> 生成成绩
    生成成绩 -- "0..1" --> 成绩

    用户 -- "1" --> 用户录入成绩
    用户录入成绩 -- "N" --> 成绩

    %% ===================== 关系 - 系统辅助 =====================
    用户发布公告{发布公告}
    用户发起事务{发起事务}
    事务产生日志{产生日志}
    用户创建备份{创建备份}

    用户 -- "1" --> 用户发布公告
    用户发布公告 -- "N" --> 公告

    用户 -- "1" --> 用户发起事务
    用户发起事务 -- "N" --> 事务

    事务 -- "1" --> 事务产生日志
    事务产生日志 -- "N" --> 事务日志

    用户 -- "1" --> 用户创建备份
    用户创建备份 -- "N" --> 备份记录

    %% ===================== 应用样式 =====================
    class 角色,用户,院系,专业,学生,教师,学期,学期活动阶段,课程,教室,开课信息,开课时间,选课记录,成绩,公告,事务,事务日志,备份记录 entity;
    class 拥有角色,用户档案学生,用户档案教师,院系开设专业,专业包含学生,院系管理教师,院系开设课程,学期当前阶段,课程安排开课,学期包含开课,教师授课,开课安排时间,教室作为地点,学生选课,开课被选,生成成绩,用户录入成绩,用户发布公告,用户发起事务,事务产生日志,用户创建备份 relation;
```

### 1.3 ER 图（表结构）

```mermaid
erDiagram
    roles["角色"] {
        BIGINT id PK "编号"
        VARCHAR code UK "角色编码"
        VARCHAR name "角色名称"
    }

    users["用户"] {
        BIGINT id PK "编号"
        VARCHAR username UK "用户名"
        VARCHAR password_hash "密码哈希"
        VARCHAR display_name "显示名称"
        VARCHAR email UK "邮箱"
        BIGINT role_id FK "角色编号"
        ENUM status "状态"
        DATETIME created_at "创建时间"
    }

    departments["院系"] {
        BIGINT id PK "编号"
        VARCHAR name UK "院系名称"
        VARCHAR phone "联系电话"
    }

    majors["专业"] {
        BIGINT id PK "编号"
        BIGINT department_id FK "院系编号"
        VARCHAR name "专业名称"
        TINYINT duration_years "学制年限"
    }

    students["学生"] {
        BIGINT id PK "编号"
        BIGINT user_id FK "用户编号，唯一"
        VARCHAR student_no UK "学号"
        BIGINT major_id FK "专业编号"
        SMALLINT admission_year "入学年份"
    }

    teachers["教师"] {
        BIGINT id PK "编号"
        BIGINT user_id FK "用户编号，唯一"
        VARCHAR teacher_no UK "工号"
        BIGINT department_id FK "院系编号"
        VARCHAR title "职称"
    }

    semesters["学期"] {
        BIGINT id PK "编号"
        VARCHAR name UK "学期名称"
        DATE start_date "开始日期"
        DATE end_date "结束日期"
        DECIMAL max_credit "最大学分"
    }

    semester_active_phases["学期活动阶段"] {
        ENUM phase PK "阶段"
        BIGINT semester_id FK "学期编号，唯一"
    }

    courses["课程"] {
        BIGINT id PK "编号"
        VARCHAR code UK "课程编码"
        VARCHAR name "课程名称"
        BIGINT department_id FK "院系编号"
        DECIMAL credit "学分"
        ENUM status "状态"
    }

    classrooms["教室"] {
        BIGINT id PK "编号"
        VARCHAR building "教学楼"
        VARCHAR room_no "教室号"
    }

    course_offerings["开课信息"] {
        BIGINT id PK "编号"
        BIGINT course_id FK "课程编号"
        BIGINT semester_id FK "学期编号"
        BIGINT teacher_id FK "教师编号"
        SMALLINT capacity "容量"
        DECIMAL exam_ratio "考试占比"
        ENUM status "状态"
    }

    course_offering_times["开课时间"] {
        BIGINT id PK "编号"
        BIGINT offering_id FK "开课编号"
        BIGINT classroom_id FK "教室编号"
        TINYINT day_of_week "星期"
        TINYINT start_section "开始节次"
        TINYINT end_section "结束节次"
        TINYINT start_week "开始周"
        TINYINT end_week "结束周"
        ENUM week_type "周次类型"
    }

    enrollments["选课记录"] {
        BIGINT id PK "编号"
        BIGINT student_id FK "学生编号"
        BIGINT offering_id FK "开课编号"
        ENUM status "状态"
    }

    grades["成绩"] {
        BIGINT id PK "编号"
        BIGINT enrollment_id FK "选课记录编号，唯一"
        DECIMAL usual_score "平时成绩"
        DECIMAL exam_score "考试成绩"
        BIGINT updated_by FK "更新人编号"
        DATETIME updated_at "更新时间"
    }

    notices["公告"] {
        BIGINT id PK "编号"
        VARCHAR title "标题"
        TEXT content "内容"
        ENUM audience "受众"
        BIGINT created_by FK "创建人编号"
        DATETIME created_at "创建时间"
    }

    transactions["事务"] {
        CHAR transaction_id PK "事务编号"
        VARCHAR business_type "业务类型"
        BIGINT actor_user_id FK "操作用户编号"
        DATETIME started_at "开始时间"
        DATETIME ended_at "结束时间"
        ENUM final_status "最终状态"
    }

    transaction_log_entries["事务日志"] {
        BIGINT id PK "编号"
        CHAR transaction_id FK "事务编号"
        ENUM operation "操作类型"
        VARCHAR table_name "表名"
        BIGINT record_id "记录编号"
        ENUM status "状态"
        TEXT message "消息"
        DATETIME created_at "创建时间"
    }

    backup_records["备份记录"] {
        BIGINT id PK "编号"
        VARCHAR database_name "数据库名"
        VARCHAR file_name "文件名"
        VARCHAR backup_directory "备份目录"
        BIGINT file_size_bytes "文件大小字节"
        ENUM status "状态"
        ENUM trigger_type "触发类型"
        BIGINT created_by FK "创建人编号"
        DATETIME started_at "开始时间"
        DATETIME ended_at "结束时间"
        TEXT message "消息"
    }

    roles ||--o{ users : "拥有"
    users ||--o| students : "对应学生档案"
    users ||--o| teachers : "对应教师档案"
    departments ||--o{ majors : "开设"
    departments ||--o{ teachers : "管理"
    departments ||--o{ courses : "开设"
    majors ||--o{ students : "包含"

    semesters ||--o| semester_active_phases : "当前阶段"
    courses ||--o{ course_offerings : "安排开课"
    semesters ||--o{ course_offerings : "包含"
    teachers ||--o{ course_offerings : "授课"
    course_offerings ||--o{ course_offering_times : "安排时间"
    classrooms ||--o{ course_offering_times : "上课地点"

    students ||--o{ enrollments : "选课"
    course_offerings ||--o{ enrollments : "被选"
    enrollments ||--o| grades : "生成"
    users ||--o{ grades : "录入或更新"

    users ||--o{ notices : "发布"
    users ||--o{ transactions : "发起"
    transactions ||--o{ transaction_log_entries : "记录"
    users ||--o{ backup_records : "创建"
```

### 1.4 核心业务关系总结

| 关系                                | 类型 | 说明                         |
| ----------------------------------- | ---- | ---------------------------- |
| Role → User                         | 1:N  | 一个角色下有多个用户         |
| User → Student                      | 1:1  | 一个学生账号对应一个学生档案 |
| User → Teacher                      | 1:1  | 一个教师账号对应一个教师档案 |
| Department → Major                  | 1:N  | 一个院系下设多个专业         |
| Department → Teacher                | 1:N  | 一个院系有多位教师           |
| Major → Student                     | 1:N  | 一个专业有多名学生           |
| Department → Course                 | 1:N  | 一个院系开设多门课程         |
| Course → CourseOffering             | 1:N  | 一门课可在多学期多次开设     |
| Semester → CourseOffering           | 1:N  | 一个学期有多门开课           |
| Teacher → CourseOffering            | 1:N  | 一位教师可教授多门开课       |
| CourseOffering → CourseOfferingTime | 1:N  | 一门开课有多个上课时间段     |
| Classroom → CourseOfferingTime      | 1:N  | 教室在不同时间被多门课使用   |
| Student → Enrollment                | 1:N  | 一个学生有多条选课记录       |
| CourseOffering → Enrollment         | 1:N  | 一门开课被多个学生选择       |
| Enrollment → Grade                  | 1:1  | 一条选课记录最多一个成绩     |
| Semester → SemesterActivePhase      | 1:1  | 一个学期对应一个活跃阶段     |
| User → Notice                       | 1:N  | 一个用户可发布多条通知       |
| User → Transaction                  | 1:N  | 一个用户可发起多个业务事务   |
| Transaction → TransactionLogEntry   | 1:N  | 一个事务有多条操作日志       |

### 1.5 数据库原理

| 原理             | 应用                                                                                             | schema.sql 位置    |
| ---------------- | ------------------------------------------------------------------------------------------------ | ------------------ |
| **外键约束**     | `users.role_id → roles.id`，保证引用完整性，防止孤儿记录                                         | `schema.sql:41-51` |
| **UNIQUE 约束**  | `students.user_id` 与 `teachers.user_id` 均为 UNIQUE，确保一个用户最多对应一条档案               | `schema.sql:69-88` |
| **1:1 扩展模式** | 公共属性放 `users`，学生特有属性放 `students`，教师特有属性放 `teachers`，避免宽表冗余，符合 3NF | `schema.sql:41-88` |

---

## <font color="red">二、逻辑设计 — 各表结构</font>

### 2.1 表的五层分类

数据库共 **18 张表**，按职责分为五个层次：

```mermaid
flowchart TB
    classDef layer fill:#ffffff,stroke:#2563eb,stroke-width:2px,color:#111827;

    subgraph mgmt["管 理 层 (Management)"]
        direction TB
        notices["notices<br/>公告"]
        transactions["transactions<br/>业务事务"]
        txn_log["transaction_log<br/>_entries 事务日志"]
        backup["backup_records<br/>备份记录"]
        phases["semester_active<br/>_phases 学期阶段"]
    end

    subgraph biz["业 务 层 (Business)"]
        direction TB
        enrollments["enrollments<br/>选课记录"]
        grades["grades<br/>成绩"]
    end

    subgraph res["资 源 层 (Resource)"]
        direction TB
        offerings["course_offerings<br/>开课信息"]
        times["course_offering<br/>_times 排课时间"]
        courses["courses<br/>课程目录"]
        classrooms["classrooms<br/>教室"]
        semesters["semesters<br/>学期"]
    end

    subgraph profile["档 案 层 (Profile)"]
        direction TB
        students["students<br/>学生档案"]
        teachers["teachers<br/>教师档案"]
        depts["departments<br/>院系"]
        majors["majors<br/>专业"]
    end

    subgraph identity["基 础 层 (Identity)"]
        direction TB
        roles["roles<br/>角色"]
        users["users<br/>用户"]
    end

    mgmt --> biz
    biz --> res
    res --> profile
    profile --> identity

    class notices,transactions,txn_log,backup,phases,enrollments,grades,offerings,times,courses,classrooms,semesters,students,teachers,depts,majors,roles,users layer;
```

### 2.2 各表结构详解

#### 基础层

**roles** — 角色表

| 列名   | 类型        | 约束               | 说明                              |
| ------ | ----------- | ------------------ | --------------------------------- |
| `id`   | BIGINT      | PK, AUTO_INCREMENT | 角色 ID                           |
| `code` | VARCHAR(32) | NOT NULL, UNIQUE   | 角色编码（admin/teacher/student） |
| `name` | VARCHAR(64) | NOT NULL           | 角色显示名称                      |

**users** — 用户表（统一登录账号）

| 列名            | 类型                       | 约束                                | 说明                      |
| --------------- | -------------------------- | ----------------------------------- | ------------------------- |
| `id`            | BIGINT                     | PK, AUTO_INCREMENT                  | 用户 ID                   |
| `username`      | VARCHAR(64)                | NOT NULL, UNIQUE                    | 登录用户名                |
| `password_hash` | VARCHAR(255)               | NOT NULL                            | BCrypt 密码哈希（强度12） |
| `display_name`  | VARCHAR(80)                | NOT NULL                            | 显示姓名                  |
| `email`         | VARCHAR(120)               | NOT NULL, UNIQUE                    | 电子邮箱                  |
| `role_id`       | BIGINT                     | NOT NULL, FK → roles(id)            | 角色关联                  |
| `status`        | ENUM('enabled','disabled') | NOT NULL, DEFAULT 'enabled'         | 账号状态                  |
| `created_at`    | DATETIME                   | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 创建时间                  |

#### 档案层

```mermaid
erDiagram
    departments {
        bigint id PK
        varchar name UK "学院名称"
        varchar phone
    }

    majors {
        bigint id PK
        bigint department_id FK
        varchar name
        tinyint duration_years "1-8"
    }

    students {
        bigint id PK
        bigint user_id FK "唯一"
        varchar student_no UK
        bigint major_id FK
        smallint admission_year
    }

    teachers {
        bigint id PK
        bigint user_id FK "唯一"
        varchar teacher_no UK
        bigint department_id FK
        varchar title
    }

    departments ||--o{ majors : "1:N 开设"
    departments ||--o{ teachers : "1:N 管理"
    majors ||--o{ students : "1:N 包含"
```

#### 资源层 — 课程、开课与排课的三层模型

这是整个系统最核心的设计。**课程（courses）** 是抽象的教学内容定义，**开课（course_offerings）** 是课程在特定学期的具体实例，**排课时间（course_offering_times）** 是开课的具体上课安排。

```
courses（课程定义）               course_offerings（开课实例）         course_offering_times（时间安排）
┌──────────────────┐           ┌──────────────────────┐         ┌──────────────────────────┐
│ code: "CS101"    │───1:N───▶│ 2024春季学期, 张三老师  │───1:N──▶│ 周一 1-2节, 1-16周, 教101 │
│ name: "数据结构"  │           │ 容量: 60人, 考试比60%  │         │ 周三 3-4节, 1-16周, 教201 │
│ credit: 4.0      │           │ status: 'selecting'   │         │ week_type: 'all'          │
└──────────────────┘           └──────────────────────┘         └──────────────────────────┘
```

```mermaid
erDiagram
    courses {
        bigint id PK
        varchar code UK
        varchar name
        bigint department_id FK
        decimal credit "大于0"
        enum status
    }

    semesters {
        bigint id PK
        varchar name UK
        date start_date
        date end_date
        decimal max_credit
    }

    teachers {
        bigint id PK
        bigint user_id FK "唯一"
        varchar teacher_no UK
        bigint department_id FK
        varchar title
    }

    classrooms {
        bigint id PK
        varchar building
        varchar room_no
    }

    course_offerings {
        bigint id PK
        bigint course_id FK
        bigint semester_id FK
        bigint teacher_id FK
        smallint capacity "大于0"
        decimal exam_ratio "0-1"
        enum status "selecting closed deleted"
    }

    course_offering_times {
        bigint id PK
        bigint offering_id FK "ON DELETE CASCADE"
        bigint classroom_id FK
        tinyint day_of_week "1-7"
        tinyint start_section "1-12"
        tinyint end_section "1-12"
        tinyint start_week
        tinyint end_week
        enum week_type "all odd even"
    }

    courses ||--o{ course_offerings : "1:N"
    semesters ||--o{ course_offerings : "1:N"
    teachers ||--o{ course_offerings : "1:N"
    course_offerings ||--o{ course_offering_times : "1:N CASCADE"
    classrooms ||--o{ course_offering_times : "1:N"
```

#### 业务层 — 选课与成绩

```mermaid
erDiagram
    enrollments {
        bigint id PK
        bigint student_id FK
        bigint offering_id FK
        enum status "selected dropped"
    }

    grades {
        bigint id PK
        bigint enrollment_id FK "唯一"
        decimal usual_score "0-100 可为空"
        decimal exam_score "0-100 可为空"
        bigint updated_by FK
        datetime updated_at
    }

    enrollments ||--o| grades : "1:0..1 生成成绩"
```

#### 排课冲突检测

系统通过 `course_offering_times` 表的 AFTER INSERT / AFTER UPDATE 触发器实现了**教师时间冲突**、**教室占用冲突**和**学生课表冲突**的自动检测：

```
时间冲突判定逻辑（day_of_week 相同的情况下）：
  时间段重叠: NOT (A.end_section < B.start_section OR A.start_section > B.end_section)
  周次重叠:   NOT (A.end_week    < B.start_week    OR A.start_week    > B.end_week)
  单双周兼容: A.week_type = 'all' OR B.week_type = 'all' OR A.week_type = B.week_type
```

#### 管理层 — 审计与通知

```mermaid
erDiagram
    transactions {
        char transaction_id PK "UUID"
        varchar business_type "select_course save_grade 等"
        bigint actor_user_id FK "可为空"
        datetime started_at
        datetime ended_at
        enum final_status "started committed rolled_back failed"
    }

    transaction_log_entries {
        bigint id PK
        char transaction_id FK
        enum operation "START INSERT UPDATE DELETE UPSERT COMMIT ROLLBACK"
        varchar table_name
        bigint record_id
        enum status "started success failed rolled_back"
        text message
        datetime created_at
    }

    notices {
        bigint id PK
        varchar title
        text content
        enum audience "all teacher student"
        bigint created_by FK
        datetime created_at
    }

    transactions ||--o{ transaction_log_entries : "1:N"
```

### 2.3 视图

**course_offering_stats** — 开课统计视图： LEFT JOIN 聚合 `enrollments` 中 `status='selected'` 的记录数，为每门开课提供 `selected_count` 字段。

**grade_results** — 成绩结果视图：计算 `final_score = ROUND(usual × (1 − exam_ratio) + exam × exam_ratio, 0)`，并映射为 4.0 制绩点：

```
绩点映射（分段线性）:
  90-100 → 4.0    85-89 → 3.7    82-84 → 3.3    78-81 → 3.0
  75-77 → 2.7     72-74 → 2.3    68-71 → 2.0    66-67 → 1.7
  64-65 → 1.5     60-63 → 1.0     <60   → 0.0
```

### 2.4 触发器

| 触发器                                        | 时机                                  | 功能                                     |
| --------------------------------------------- | ------------------------------------- | ---------------------------------------- |
| `trg_semesters_no_overlap_before_insert`      | BEFORE INSERT ON semesters            | 学期日期不重叠 + start ≤ end             |
| `trg_semesters_no_overlap_before_update`      | BEFORE UPDATE ON semesters            | 同上，排除自身 ID                        |
| `trg_enrollments_capacity_before_insert`      | BEFORE INSERT ON enrollments          | 选课容量校验（FOR UPDATE）               |
| `trg_enrollments_capacity_before_update`      | BEFORE UPDATE ON enrollments          | 退课重选时容量校验                       |
| `trg_course_offerings_capacity_before_update` | BEFORE UPDATE ON course_offerings     | 容量降低 + 教师/教室冲突 + 学生课表冲突  |
| `trg_offering_times_schedule_after_insert`    | AFTER INSERT ON course_offering_times | 新增时间段时教师/教室冲突 + 学生课表冲突 |
| `trg_offering_times_schedule_after_update`    | AFTER UPDATE ON course_offering_times | 修改时间段时教师/教室冲突 + 学生课表冲突 |
| `trg_notices_admin_insert`                    | BEFORE INSERT ON notices              | 仅管理员可发布通知                       |
| `trg_notices_admin_update`                    | BEFORE UPDATE ON notices              | 仅管理员可修改通知                       |

### 2.5 存储过程

| 存储过程                   | 参数                                                              | 功能                                    |
| -------------------------- | ----------------------------------------------------------------- | --------------------------------------- |
| `sp_select_course`         | student_id, offering_id, actor_user_id                            | 学生选课（7项校验 + 双重容量检查）      |
| `sp_student_drop_course`   | student_id, enrollment_id, actor_user_id                          | 学生自主退课（校验成绩未录入）          |
| `sp_admin_drop_course`     | student_id, offering_id, actor_user_id                            | 管理员强制退课（无阶段限制）            |
| `sp_admin_drop_enrollment` | enrollment_id, actor_user_id                                      | 通过选课ID退课                          |
| `sp_save_grade`            | teacher_id, enrollment_id, usual_score, exam_score, actor_user_id | 教师录入成绩（校验教师归属 + 学期匹配） |

所有存储过程均遵循**审计先写 + 固定锁顺序 + READ COMMITTED 隔离级别**的设计原则。

---

## <font color="red">三、物理设计 — 管理员、教师、学生各端功能实现</font>

### 3.1 技术架构总览

```mermaid
flowchart TB
    classDef frontend fill:#dbeafe,stroke:#2563eb,stroke-width:2px,color:#111827;
    classDef backend fill:#ffffff,stroke:#2563eb,stroke-width:2px,color:#111827;
    classDef infra fill:#f3f4f6,stroke:#9ca3af,stroke-width:1px,color:#374151;

    subgraph front["前端 (SPA)"]
        direction TB
        html["index.html"]
        js["app.js<br/>(纯 JavaScript)"]
        css["styles.css"]
    end

    subgraph spring["Spring Boot 3.5.14 (Java 17)"]
        direction TB
        subgraph security["安全层"]
            direction TB
            interceptor["AuthInterceptor<br/>令牌解析 + 角色校验"]
            annotation["@RequireRole<br/>方法级注解"]
        end

        subgraph controllers["控制器层"]
            direction TB
            admin_ctrl["AdminController<br/>@RequireRole(admin)"]
            teacher_ctrl["TeacherController<br/>@RequireRole(teacher)"]
            student_ctrl["StudentController<br/>@RequireRole(student)"]
        end

        subgraph services["服务层"]
            direction TB
            admin_svc["AdminService<br/>(1035行)"]
            teacher_svc["TeacherService"]
            student_svc["StudentService"]
            backup_svc["BackupService"]
        end

        subgraph data["数据访问层"]
            direction TB
            mybatis["MyBatis 3.0.3<br/>注解式 SQL 映射"]
        end
    end

    subgraph storage["存储层"]
        direction TB
        mysql[("MySQL 8.x<br/>主存储")]
        redis[("Redis<br/>会话+缓存")]
        fs[("文件系统<br/>SQL 备份")]
    end

    front -- "HTTP JSON<br/>Authorization: Bearer token" --> spring
    security --> controllers
    controllers --> services
    services --> data
    mybatis --> storage

    class html,js,css frontend;
    class interceptor,annotation,admin_ctrl,teacher_ctrl,student_ctrl,admin_svc,teacher_svc,student_svc,backup_svc,mybatis backend;
    class mysql,redis,fs infra;
```

**关键技术点**：

| 层次     | 技术                               | 说明                                         |
| -------- | ---------------------------------- | -------------------------------------------- |
| 认证     | 自定义 Token (64位 UUID×2)         | 无需 Spring Security，轻量灵活               |
| 授权     | `@RequireRole` + AuthInterceptor   | 方法级注解，拦截器自动校验                   |
| 数据访问 | MyBatis 注解式 SQL                 | 无 XML 配置文件，`<script>` 标签处理动态 SQL |
| 缓存     | Redis + ConcurrentHashMap 双层     | Cache-aside 模式，Redis 故障自动降级         |
| 事务审计 | Spring AOP `@BusinessTransaction`  | 审计先写模式，REQUIRES_NEW 隔离              |
| 并发控制 | SELECT ... FOR UPDATE + 固定锁顺序 | 防止死锁和超选                               |
| 前端     | 原生 JavaScript SPA                | 无框架依赖，轻量部署                         |

---

### 3.2 管理员端功能

管理员拥有系统最高权限，所有接口均标注 `@RequireRole("admin")`，路由前缀 `/api/admin`。

#### 3.2.1 系统管理总览

```mermaid
flowchart TB
    classDef group fill:#ffffff,stroke:#2563eb,stroke-width:2px,color:#111827;
    classDef detail fill:#f3f4f6,stroke:#9ca3af,color:#374151

    subgraph admin["管理员功能模块"]
        direction TB

        subgraph u1["用户与角色"]
            A1["用户 CRUD"]
            A2["教师 CRUD + 启用/禁用"]
            A3["学生 CRUD + 启用/禁用"]
        end

        subgraph u2["课程与排课"]
            B1["课程目录 CRUD"]
            B2["开课创建/修改/删除"]
            B3["排课冲突自动检测"]
        end

        subgraph u3["学期与阶段控制"]
            C1["学期 CRUD"]
            C2["选课阶段 开始/停止"]
            C3["登分阶段 开始/停止"]
        end

        subgraph u4["教务操作"]
            D1["代学生选课"]
            D2["代学生退课(两种方式)"]
            D3["选课报表统计"]
        end

        subgraph u5["通知与公告"]
            E1["发布/修改/删除通知"]
            E2["触发器强制管理员权限"]
        end

        subgraph u6["审计与备份"]
            F1["事务日志分页查询"]
            F2["备份记录分页查询"]
            F3["手动触发 mysqldump 备份"]
        end
    end

    class u1,u2,u3,u4,u5,u6 group;
    class A1,A2,A3,B1,B2,B3,C1,C2,C3,D1,D2,D3,E1,E2,F1,F2,F3 detail;
```

#### 3.2.2 管理员接口总表

| API                                              | 方法 | 分类 | 说明                                  |
| ------------------------------------------------ | ---- | ---- | ------------------------------------- |
| `GET /api/admin/users`                           | 查询 | 用户 | 获取所有用户列表                      |
| `POST /api/admin/users`                          | 新增 | 用户 | 创建用户                              |
| `DELETE /api/admin/users/{userId}`               | 删除 | 用户 | 删除用户（不可删自己）                |
| `GET /api/admin/teachers?keyword=`               | 查询 | 教师 | 教师列表，支持搜索                    |
| `POST /api/admin/teachers`                       | 新增 | 教师 | 创建教师（自动创建 users 档案）       |
| `PUT /api/admin/teachers/{teacherId}`            | 修改 | 教师 | 更新教师信息                          |
| `POST /api/admin/teachers/{teacherId}/disable`   | 开关 | 教师 | 禁用教师                              |
| `POST /api/admin/teachers/{teacherId}/enable`    | 开关 | 教师 | 启用教师                              |
| `GET /api/admin/students?keyword=`               | 查询 | 学生 | 学生列表，支持搜索                    |
| `POST /api/admin/students`                       | 新增 | 学生 | 创建学生（自动创建 users 档案）       |
| `PUT /api/admin/students/{studentId}`            | 修改 | 学生 | 更新学生信息                          |
| `POST /api/admin/students/{studentId}/disable`   | 开关 | 学生 | 禁用学生                              |
| `POST /api/admin/students/{studentId}/enable`    | 开关 | 学生 | 启用学生                              |
| `GET /api/admin/courses?keyword=`                | 查询 | 课程 | 课程目录                              |
| `POST /api/admin/courses`                        | 新增 | 课程 | 创建课程                              |
| `POST /api/admin/courses/{id}/enable`            | 开关 | 课程 | 启用课程                              |
| `POST /api/admin/courses/{id}/disable`           | 开关 | 课程 | 停用课程                              |
| `GET /api/admin/offerings`                       | 查询 | 开课 | 开课列表，支持学期过滤                |
| `POST /api/admin/offerings`                      | 新增 | 开课 | 创建开课（含时间安排+冲突检测）       |
| `PUT /api/admin/offerings/{id}`                  | 修改 | 开课 | 修改开课                              |
| `DELETE /api/admin/offerings/{id}`               | 删除 | 开课 | 逻辑删除（status='deleted'）          |
| `GET /api/admin/offerings/{id}/roster`           | 查询 | 开课 | 选课名单                              |
| `GET /api/admin/offerings/{id}/grade-stats`      | 查询 | 开课 | 成绩统计                              |
| `POST /api/admin/semesters`                      | 新增 | 学期 | 创建学期                              |
| `PUT /api/admin/semesters/{id}`                  | 修改 | 学期 | 修改学期                              |
| `POST /api/admin/semesters/{id}/selection/start` | 开关 | 学期 | **开放选课**（互斥：关闭旧选课+登分） |
| `POST /api/admin/semesters/{id}/selection/stop`  | 开关 | 学期 | 关闭选课                              |
| `POST /api/admin/semesters/{id}/grading/start`   | 开关 | 学期 | **开放登分**（互斥：关闭旧登分+选课） |
| `POST /api/admin/semesters/{id}/grading/stop`    | 开关 | 学期 | 关闭登分                              |
| `POST /api/admin/teaching/select`                | 操作 | 代选 | 管理员代学生选课                      |
| `POST /api/admin/teaching/drop`                  | 操作 | 代退 | 管理员代学生退课（两种方式）          |
| `POST /api/admin/notices`                        | 新增 | 通知 | 发布通知（触发器验证管理员身份）      |
| `PUT /api/admin/notices/{id}`                    | 修改 | 通知 | 修改通知                              |
| `DELETE /api/admin/notices/{id}`                 | 删除 | 通知 | 删除通知                              |
| `GET /api/admin/logs?page=&pageSize=`            | 查询 | 审计 | 事务日志分页查询                      |
| `GET /api/admin/backups?page=&pageSize=`         | 查询 | 备份 | 备份记录分页查询                      |
| `POST /api/admin/backups/run`                    | 操作 | 备份 | 手动触发数据库备份                    |

#### 3.2.3 学期阶段状态机

```mermaid
flowchart LR
    classDef state fill:#ffffff,stroke:#2563eb,stroke-width:2px,color:#111827;
    classDef action fill:#fff7ed,stroke:#ea580c,stroke-width:1px,color:#111827;

    idle[空闲<br/>无活跃阶段] -->|"管理员操作"| selecting["selection 阶段<br/>学生可选课/退课<br/>教师不可登分"]

    selecting -->|"stop selection"| idle
    selecting -->|"start grading<br/>自动关闭 selection"| grading["grading 阶段<br/>教师可登分<br/>学生不可选课/退课"]

    grading -->|"stop grading"| idle
    grading -->|"start selection<br/>自动关闭 grading"| selecting

    class idle,selecting,grading state;
```

> **设计原理**: `phase` 作为 ENUM 主键确保系统全局同时只有**一条** selection 记录和**一条** grading 记录（最多两条）。开启新阶段时自动覆盖旧记录，选课与登分**互斥**——同一学期不能同时处于两个阶段。

#### 3.2.4 代选课与代退课

管理员可通过两种方式退课，灵活应对不同场景：

| 管理员退课方式    | 存储过程                   | 主要校验                                | 适用场景                     |
| ----------------- | -------------------------- | --------------------------------------- | ---------------------------- |
| 按学生 + 开课退课 | `sp_admin_drop_course`     | 学生存在、开课存在、选课为 selected     | 知道学生学号和课程序号       |
| 按选课记录退课    | `sp_admin_drop_enrollment` | 选课记录存在、学生存在、状态为 selected | 已知 enrollment_id，直接操作 |

与学生自主退课不同，管理员退课**不受学期阶段限制**，适合处理异常数据或逾期退课的人工审批场景。

---

### 3.3 教师端功能

教师端接口标注 `@RequireRole("teacher")`，路由前缀 `/api/teacher`，所有接口自动从 SessionUser 中获取教师身份。

```mermaid
flowchart LR
    classDef group fill:#ffffff,stroke:#2563eb,stroke-width:2px,color:#111827;
    classDef detail fill:#f3f4f6,stroke:#9ca3af,color:#374151;

    subgraph teacher["教师端功能"]
        direction TB

        subgraph t1["授课信息"]
            t1a["GET /courses<br/>我的开课列表"]
            t1b["GET /schedule<br/>我的课表"]
        end

        subgraph t2["名单查看"]
            t2a["GET /roster<br/>任意课程选课名单(含成绩)"]
            t2b["GET /grade-roster<br/>当前登分学期课程名单"]
        end

        subgraph t3["成绩管理"]
            t3a["GET /grade-courses<br/>登分学期我的课程"]
            t3b["POST /grades<br/>录入/修改成绩 → sp_save_grade"]
            t3c["GET /grade-stats<br/>成绩分布统计"]
        end
    end

    class t1,t2,t3 group;
    class t1a,t1b,t2a,t2b,t3a,t3b,t3c detail;
```

**成绩录入流程**：

```mermaid
flowchart TB
    classDef step fill:#ffffff,stroke:#2563eb,stroke-width:2px,color:#111827;
    classDef check fill:#fff7ed,stroke:#ea580c,stroke-width:1px,color:#111827;

    step1["1. GET /grade-courses<br/>获取当前登分学期的课程列表"] --> step2["2. GET /grade-roster?offeringId=<br/>获取课程选课学生名单"]
    step2 --> step3["3. POST /grades<br/>填写成绩并提交"]
    step3 --> check1{校验1: 操作者是<br/>该课程的授课教师?}
    check1 -->|"否"| reject["❌ 拒绝: 教师非课程拥有者"]
    check1 -->|"是"| check2{校验2: 当前学期<br/>处于 grading 阶段?}
    check2 -->|"否"| reject2["❌ 拒绝: 登分阶段未开放"]
    check2 -->|"是"| check3{校验3: 成绩值<br/>在 0-100 范围内?}
    check3 -->|"否"| reject3["❌ 拒绝: 成绩范围非法"]
    check3 -->|"是"| upsert["UPSERT grades<br/>ON DUPLICATE KEY UPDATE<br/>支持重复提交修正"]
    upsert --> success["✅ 成绩保存成功"]

    class step1,step2,step3,upsert,success step;
    class check1,check2,check3,reject,reject2,reject3 check;
```

> **roster 与 grade-roster 的区别**：`roster` 可查看任意课程班的选课名单（含已有成绩），`grade-roster` 仅返回**当前登分学期**中教师负责的课程班（用于录入成绩）。

---

### 3.4 学生端功能

学生端接口标注 `@RequireRole("student")`，路由前缀 `/api/student`。`SessionUser.profile` 中自动包含 `studentId`。

```mermaid
flowchart LR
    classDef group fill:#ffffff,stroke:#2563eb,stroke-width:2px,color:#111827;
    classDef detail fill:#f3f4f6,stroke:#9ca3af,color:#374151;

    subgraph student["学生端功能"]
        direction TB

        subgraph s1["选课管理"]
            s1a["GET /offerings<br/>可选课程列表<br/>(含容量+已选状态)"]
            s1b["POST /select<br/>选课 → sp_select_course"]
            s1c["POST /drop<br/>退课 → sp_student_drop_course"]
        end

        subgraph s2["课表与成绩"]
            s2a["GET /schedule<br/>我的课表"]
            s2b["GET /grades<br/>成绩查询(含总评+绩点)"]
            s2c["GET /transcript<br/>成绩单(按学期汇总GPA)"]
        end
    end

    class s1,s2 group;
    class s1a,s1b,s1c,s2a,s2b,s2c detail;
```

#### 3.4.1 选课业务流程

```mermaid
flowchart TB
    classDef step fill:#ffffff,stroke:#2563eb,stroke-width:2px,color:#111827;
    classDef result fill:#f3f4f6,stroke:#9ca3af,color:#374151;

    start["学生点击选课"] --> lock_student["1. 锁定学生 (FOR UPDATE)"]
    lock_student --> lock_phase["2. 锁定选课阶段 (FOR UPDATE)"]
    lock_phase --> lock_offering["3. 锁定开课记录 (FOR UPDATE)"]
    lock_offering --> checks["4. 七项业务校验"]

    checks --> c1{"① 学期是否开放?"}
    c1 -->|"否"| e1["❌ 仅开放选课学期可选"]
    c1 -->|"是"| c2{"② 开课状态是否 selecting?"}
    c2 -->|"否"| e2["❌ 课程不可选"]
    c2 -->|"是"| c3{"③ 容量已满?(第一次检查)"}
    c3 -->|"是"| e3["❌ 容量已满"]
    c3 -->|"否"| c4{"④ 是否重复选同一门课?"}
    c4 -->|"是"| e4["❌ 同一课程已选"]
    c4 -->|"否"| c5{"⑤ 是否之前已通过(≥60分)?"}
    c5 -->|"是"| e5["❌ 已通过不可重选"]
    c5 -->|"否"| c6{"⑥ 时间是否与课表冲突?"}
    c6 -->|"是"| e6["❌ 课程时间冲突"]
    c6 -->|"否"| c7{"⑦ 学分是否超上限?"}
    c7 -->|"是"| e7["❌ 学分超限"]
    c7 -->|"否"| cap_check["5. 容量二次检查<br/>(防 TOCTOU 竞争)"]
    cap_check --> cap_ok{容量仍有余?}
    cap_ok -->|"是"| upsert["6. UPSERT enrollments<br/>ON DUPLICATE KEY UPDATE"]
    cap_ok -->|"否"| e3
    upsert --> log["7. 记录审计日志"]
    log --> commit["8. COMMIT ✅"]

    class start,lock_student,lock_phase,lock_offering,checks,cap_check,upsert,log,commit step;
    class c1,c2,c3,c4,c5,c6,c7,cap_ok,e1,e2,e3,e4,e5,e6,e7 result;
```

#### 3.4.2 锁顺序设计（防止死锁）

所有存储过程严格按照以下固定顺序获取行锁（`FOR UPDATE`）：

```
students → semester_active_phases → course_offerings → enrollments → grades
```

这是数据库并发控制中经典的**死锁预防**策略——通过**锁顺序约定（Lock Ordering）** 避免循环等待。

#### 3.4.3 容量的双重检查

存储过程在执行业务校验和最终插入之前**两次检查容量**，形成 **TOCTOU（Time-of-Check-Time-of-Use）** 防护：即使在校验与写入间隙有其他事务提交了选课，第二次检查也能捕获并阻止超容。

#### 3.4.4 退课的三种场景

| 场景                | 存储过程                   | 调用者  | 业务规则                             |
| ------------------- | -------------------------- | ------- | ------------------------------------ |
| 学生自主退课        | `sp_student_drop_course`   | Student | 仅在选课阶段开放；**已出成绩不可退** |
| 管理员按学生+开课退 | `sp_admin_drop_course`     | Admin   | 无需校验学期阶段，强制退选           |
| 管理员按选课记录退  | `sp_admin_drop_enrollment` | Admin   | 通过 enrollment_id 直接操作          |

---

### 3.5 学生成绩模型

```mermaid
flowchart TB
    classDef table fill:#ffffff,stroke:#2563eb,stroke-width:2px,color:#111827;
    classDef compute fill:#fff7ed,stroke:#ea580c,stroke-width:1px,color:#111827;

    subgraph model["成绩模型"]
        grades["grades 表<br/>usual_score (平时成绩 0-100)<br/>exam_score  (考试成绩 0-100)"]
        exam_ratio["exam_ratio<br/>来自 course_offerings<br/>默认 0.60"]
        final["final_score<br/>= ROUND(usual × (1-r) + exam × r, 0)"]
        gpa["grade_point (4.0制)<br/>≥90→4.0 ≥85→3.7 ≥82→3.3<br/>≥78→3.0 ≥75→2.7 ≥72→2.3<br/>≥68→2.0 ≥66→1.7 ≥64→1.5<br/>≥60→1.0 <60→0.0"]

        grades --> final
        exam_ratio --> final
        final --> gpa
    end

    class grades,exam_ratio,final,gpa table;
```

所有计算通过 `grade_results` 视图在数据库层完成，前端无需重复实现。

---

### 3.6 两阶段事务审计模式

```mermaid
flowchart TB
    classDef phase1 fill:#dbeafe,stroke:#2563eb,stroke-width:2px,color:#111827;
    classDef phase2 fill:#ffffff,stroke:#2563eb,stroke-width:2px,color:#111827;
    classDef error fill:#fee2e2,stroke:#dc2626,stroke-width:2px,color:#111827;

    subgraph p1["第一阶段（独立事务）"]
        p1a["INSERT transactions (started)"]
        p1b["INSERT txn_log ('START')"]
        p1c["COMMIT ← 独立提交！"]
        p1a --> p1b --> p1c
    end

    subgraph p2["第二阶段（业务事务）"]
        p2a["START TRANSACTION"]
        p2b["SELECT ... FOR UPDATE<br/>业务校验..."]
        p2c["INSERT/UPDATE 业务数据"]
        p2d["INSERT txn_log ('UPSERT')"]
        p2e["COMMIT ✅"]
        p2a --> p2b --> p2c --> p2d --> p2e
    end

    subgraph err["异常处理（独立事务）"]
        err1["ROLLBACK 业务数据"]
        err2["START TRANSACTION (新)"]
        err3["UPDATE transactions → rolled_back"]
        err4["INSERT txn_log ('ROLLBACK', 错误信息)"]
        err5["COMMIT (审计记录独立持久化)"]
        err6["RESIGNAL (重新抛出异常)"]
        err1 --> err2 --> err3 --> err4 --> err5 --> err6
    end

    p1 --> p2
    p2 -->|"发生异常"| err

    class p1a,p1b,p1c phase1;
    class p2a,p2b,p2c,p2d,p2e phase2;
    class err1,err2,err3,err4,err5,err6 error;
```

> **设计核心**: 审计日志在独立的短事务中提前提交——即使后续业务失败回滚，**审计记录也不会丢失**。异常处理中再次使用新事务记录失败原因，实现操作全链路可追溯。

---

### 3.7 安全设计总览

```mermaid
flowchart TB
    classDef layer fill:#ffffff,stroke:#2563eb,stroke-width:2px,color:#111827;

    subgraph l1["1. 传输层"]
        direction TB
        l1a["Token 通过 Authorization: Bearer 头传递"]
        l1b["密码经 BCrypt(12) 哈希存储，永不明文传输"]
    end

    subgraph l2["2. 认证层"]
        direction TB
        l2a["64字符随机 Token（双 UUID），8小时滑动过期"]
        l2b["SessionRegistry 双层存储（内存 + Redis）"]
        l2c["登录失败返回通用错误信息，防用户枚举"]
    end

    subgraph l3["3. 授权层"]
        direction TB
        l3a["@RequireRole 方法级注解"]
        l3b["AuthInterceptor 拦截所有 /api/** 请求"]
        l3c["CurrentUserArgumentResolver 自动注入角色信息"]
        l3d["ThreadLocal 上下文隔离，请求结束自动清理"]
    end

    subgraph l4["4. 数据层"]
        direction TB
        l4a["触发器级权限校验（notices 仅管理员可操作）"]
        l4b["存储过程封装核心业务，应用层无直接写权限"]
        l4c["固定锁顺序防死锁"]
        l4d["双重容量检查防超选"]
    end

    subgraph l5["5. 审计层"]
        direction TB
        l5a["@BusinessTransaction AOP 自动审计"]
        l5b["审计先写模式确保失败操作也可追溯"]
        l5c["过期事务补偿机制（5分钟清理 stale 记录）"]
    end

    l1 --> l2 --> l3 --> l4 --> l5

    class l1a,l1b,l2a,l2b,l2c,l3a,l3b,l3c,l3d,l4a,l4b,l4c,l4d,l5a,l5b,l5c layer;
```

### 3.8 公共接口

| API                       | 方法 | 认证 | 说明                             |
| ------------------------- | ---- | ---- | -------------------------------- |
| `GET /api/health`         | 查询 | 无需 | 健康检查（Docker/K8s 探针）      |
| `GET /api/public/landing` | 查询 | 无需 | 登录页公开数据                   |
| `POST /api/auth/login`    | 操作 | 无需 | 用户登录，返回 token + user info |
| `POST /api/auth/logout`   | 操作 | 需要 | 登出（销毁 token）               |
| `POST /api/auth/password` | 操作 | 需要 | 修改密码（需旧密码验证）         |
| `GET /api/me`             | 查询 | 需要 | 获取当前用户信息和角色档案       |
| `GET /api/dashboard`      | 查询 | 需要 | 角色路由仪表盘                   |
| `GET /api/catalog`        | 查询 | 需要 | 全局目录（学期列表 + 公告列表）  |

### 3.9 索引策略总结

| 表                    | 索引                              | 类型   | 用途                   |
| --------------------- | --------------------------------- | ------ | ---------------------- |
| enrollments           | `uk_student_offering`             | UNIQUE | 防止学生重复选同一开课 |
| enrollments           | `idx_enrollments_offering_status` | 复合   | 统计开课已选人数       |
| enrollments           | `idx_enrollments_student_status`  | 复合   | 查询学生有效选课       |
| course_offering_times | `idx_offering_times_lookup`       | 复合   | 选课时查时间冲突       |
| course_offering_times | `idx_offering_times_room`         | 复合   | 检测教室占用冲突       |
| transactions          | `idx_transactions_started_at`     | 单列   | 按时间检索审计日志     |
| transactions          | `idx_transactions_actor`          | 单列   | 按操作人检索审计日志   |
| backup_records        | `idx_backup_records_started_at`   | 单列   | 备份记录时间排序       |
| backup_records        | `idx_backup_records_status`       | 单列   | 按状态筛选备份         |

### 3.10 数据库层物理设计要点

| 设计要点     | 具体实现                                                                   |
| ------------ | -------------------------------------------------------------------------- |
| **存储引擎** | 全部使用 InnoDB，支持事务和外键                                            |
| **字符集**   | utf8mb4 + utf8mb4_unicode_ci（支持 emoji 和全部 Unicode）                  |
| **外键约束** | 全部启用 FOREIGN_KEY_CHECKS，确保引用完整性                                |
| **索引策略** | 复合索引覆盖高频查询路径（选课冲突检测、学生课表、教室占用）               |
| **软删除**   | 几乎所有"删除"都是状态标记（`dropped`/`disabled`/`deleted`），保留可追溯性 |
| **行级锁**   | 选课等高并发场景使用 `SELECT ... FOR UPDATE` 配合固定锁顺序                |
| **审计**     | 应用层 AOP + 存储过程事务审计双重保障                                      |
| **备份**     | 定时（每天凌晨 2:00）+ 手动双模式，保留最近 10 份备份                      |
| **定时任务** | Spring `@Scheduled` 处理备份计划和过期事务补偿（每 5 分钟）                |

---

## 附录：关键数据库原理汇总

| 原理                  | 在项目中的应用                                                    | schema.sql 位置       |
| --------------------- | ----------------------------------------------------------------- | --------------------- |
| **ACID 事务**         | 选课/退课/登分存储过程的手动事务控制                              | `schema.sql:582-738`  |
| **悲观锁**            | `SELECT ... FOR UPDATE` 防止并发超选                              | `schema.sql:610-637`  |
| **死锁预防**          | 统一锁顺序 `students → phases → offerings → enrollments → grades` | `schema.sql:557-558`  |
| **隔离级别**          | `READ COMMITTED` 平衡一致性与并发性能                             | `schema.sql:598-600`  |
| **外键约束**          | 18 张物理表之间通过外键维护引用完整性                             | `schema.sql:41-247`   |
| **CHECK 约束**        | 成绩范围、学分>0、容量>0、日期范围、节次周次范围                  | `schema.sql:59-247`   |
| **UNIQUE 约束**       | 用户名、邮箱、学号、工号、选课唯一性、教室唯一性                  | `schema.sql:35-247`   |
| **ON DELETE CASCADE** | 开课删除时级联删除排课时间；学期删除时级联删除激活阶段            | `schema.sql:100-166`  |
| **触发器**            | 学期日期重叠、容量限制、排课冲突、通知权限                        | `schema.sql:251-512`  |
| **视图**              | 成绩总评与绩点计算、开课统计                                      | `schema.sql:514-555`  |
| **存储过程**          | 5 个核心业务操作的完整实现                                        | `schema.sql:559-1224` |
| **规范化（3NF）**     | users/students/teachers 分离，courses/offerings/times 分离        | `schema.sql:41-166`   |
| **复合索引**          | 按查询模式设计多列索引加速冲突检测和统计分析                      | `schema.sql:164-177`  |
| **幂等设计**          | `ON DUPLICATE KEY UPDATE` 实现选课和登分的重入安全                | `schema.sql:722-726`  |
| **审计分离**          | 业务事务与审计事务独立提交，保证审计完整性                        | `schema.sql:601-606`  |

---

## 附录：项目文件结构速查

```
eduAffairSQL-main/
├── pom.xml                              # Maven 构建配置 (Spring Boot 3.5.14)
├── database/
│   ├── schema.sql                       # DDL（18表 + 9触发器 + 2视图 + 5存储过程）
│   ├── data.sql                         # 种子数据
│   ├── schema.md                        # schema.sql 详细文档
│   └── explain.md                       # 设计合理性分析文档
├── backups/                             # 定时备份文件存放
├── scripts/
│   ├── backup_database.ps1              # Windows 备份脚本
│   └── backup_database.sh               # Unix 备份脚本
└── src/main/
    ├── java/com/student/management/
    │   ├── TeachingAffairsApplication.java
    │   ├── common/                      # ApiResponse, PasswordUtil, AOP审计
    │   ├── config/                      # WebConfig, CurrentUserArgumentResolver
    │   ├── security/                    # SessionUser, SessionRegistry, AuthInterceptor, @RequireRole
    │   ├── controller/                  # REST API 控制器（6个）
    │   ├── dto/                         # 请求/响应 DTO（全部 Java 17 record）
    │   ├── mapper/                      # MyBatis SQL 映射接口（6个）
    │   └── service/                     # 业务逻辑服务（7个）
    └── resources/
        ├── application.yml              # 应用配置（DB/Redis/MyBatis/Backup）
        └── static/
            ├── index.html               # SPA 入口
            ├── app.js                   # 前端逻辑（纯 JavaScript）
            └── styles.css               # 样式表
```
