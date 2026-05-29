package com.student.management.service;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.student.management.common.ApiException;
import com.student.management.common.BusinessTransaction;
import com.student.management.common.MapUtil;
import com.student.management.common.PasswordUtil;
import com.student.management.common.RedisCacheService;
import com.student.management.dto.CourseRequest;
import com.student.management.dto.CreateOfferingRequest;
import com.student.management.dto.CreateUserRequest;
import com.student.management.dto.NoticeRequest;
import com.student.management.dto.SemesterRequest;
import com.student.management.dto.StudentProfileRequest;
import com.student.management.dto.TeacherRequest;
import com.student.management.mapper.AdminMapper;
import com.student.management.mapper.CommonMapper;
import com.student.management.security.SessionUser;
import org.springframework.stereotype.Service;

/**
 * 管理员业务服务，是项目中最大的 Service（约 910 行），涵盖全部管理功能。
 *
 * 设计要点：
 * - 大量使用 Redis 缓存加速只读查询，写操作后调用 clearTeachingCaches() 批量失效
 * - 通过 @BusinessTransaction 注解 + TransactionAuditAspect 实现自动审计
 * - 排课冲突校验在 Service 层和数据库触发器两层都有保护
 * - 学期选课/登分阶段切换时按固定顺序加锁防止死锁
 * - 系统状态监控（CPU/内存/磁盘/网络）通过 JMX 和 java.io 获取
 */
@Service
public class AdminService {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<Map<String, Object>>> LIST_TYPE = new TypeReference<>() {
    };

    private final AdminMapper adminMapper;
    private final CommonMapper commonMapper;
    private final RedisCacheService cache;
    private final TransactionAuditService auditService;

    public AdminService(AdminMapper adminMapper, CommonMapper commonMapper, RedisCacheService cache,
            TransactionAuditService auditService) {
        this.adminMapper = adminMapper;
        this.commonMapper = commonMapper;
        this.cache = cache;
        this.auditService = auditService;
    }

    /**
     * 管理员首页仪表盘：显示选课/登分开放状态 + 系统资源监控 + 最近通知。
     */
    public Map<String, Object> dashboard() {
        Map<String, Object> selectionSemester = commonMapper.selectionSemester();
        Map<String, Object> gradingSemester = commonMapper.gradingSemester();
        return mapOf(
                "summary", List.of(
                        item("学期选课开放状态", semesterPhaseText(selectionSemester, "选课中", "未开放选课")),
                        item("教务登分开放状态", semesterPhaseText(gradingSemester, "登分中", "未开放登分"))),
                "systemStatus", systemStatus(),
                "notices", commonMapper.listRecentNotices("admin", 6));
    }

    /** 格式化阶段文本：学期名 + 后缀，无学期时显示回退文案。 */
    private String semesterPhaseText(Map<String, Object> semester, String suffix, String fallback) {
        if (semester == null) {
            return fallback;
        }
        String name = MapUtil.stringValue(semester, "name");
        return (name == null || name.isBlank() ? "" : name) + suffix;
    }

    /** 用户列表，缓存 Key 固定为 "admin:users"，写操作后通过 clearTeachingCaches 失效。 */
    public List<Map<String, Object>> listUsers() {
        return cache.get("admin:users", LIST_TYPE, adminMapper::listUsers);
    }

    /**
     * 新建用户（通用）。系统管理员账号全局唯一，不允许通过此接口新增 admin 角色。
     * 邮箱按 username@role.school.edu.cn 规则自动生成。
     */
    @BusinessTransaction(businessType = "create_user", operation = "INSERT", tableName = "users")
    public Map<String, Object> createUser(CreateUserRequest request) {
        if ("admin".equals(request.role())) {
            throw new ApiException(400, "系统管理员账号只有一个，不能新增管理员");
        }
        Long roleId = adminMapper.roleIdByCode(request.role());
        if (roleId == null) {
            throw new ApiException(400, "角色不存在");
        }
        adminMapper.insertUser(
                request.username(),
                PasswordUtil.hash(request.password()),
                request.displayName(),
                defaultEmail(request.username(), request.role()),
                roleId);
        Long userId = adminMapper.lastInsertId();
        auditInsert("users", userId, "username=" + request.username());
        clearTeachingCaches();
        return message("用户已创建");
    }

    /** 教师列表（支持关键词搜索），缓存 Key 按 keyword 区分。 */
    public List<Map<String, Object>> listTeachers(String keyword) {
        return cache.get("admin:teachers:" + cache.keyPart(keyword), LIST_TYPE,
                () -> adminMapper.listTeachers(keyword));
    }

    /**
     * 新建教师：先在 users 表建通用用户（密码哈希为工号），再在 teachers 表建扩展信息。
     * 初始密码为工号本身，建议教师首次登录后修改。
     */
    @BusinessTransaction(businessType = "create_teacher", operation = "INSERT", tableName = "teachers")
    public Map<String, Object> createTeacher(TeacherRequest request) {
        Long roleId = adminMapper.roleIdByCode("teacher");
        adminMapper.insertUser(request.teacherNo(), PasswordUtil.hash(request.teacherNo()), request.name(),
                request.email(), roleId);
        Long userId = adminMapper.lastInsertId();
        auditInsert("users", userId, "teacher_no=" + request.teacherNo());
        adminMapper.insertTeacher(userId, request);
        Long teacherId = adminMapper.lastInsertId();
        auditInsert("teachers", teacherId, "user_id=" + userId);
        clearTeachingCaches();
        return message("教师已新增，初始密码为教师号");
    }

    @BusinessTransaction(businessType = "update_teacher", operation = "UPDATE", tableName = "teachers", recordIdArgIndex = 0)
    public Map<String, Object> updateTeacher(Long teacherId, TeacherRequest request) {
        Long userId = adminMapper.teacherUserId(teacherId);
        if (userId == null) {
            throw new ApiException(404, "教师不存在");
        }
        adminMapper.updateUserIdentity(userId, request.teacherNo(), request.name());
        auditUpdate("users", userId, "identity");
        adminMapper.updateUserEmail(userId, request.email());
        auditUpdate("users", userId, "email");
        adminMapper.updateTeacher(teacherId, request);
        auditUpdate("teachers", teacherId, "profile");
        clearTeachingCaches();
        return message("教师信息已更新");
    }

    @BusinessTransaction(businessType = "disable_teacher", operation = "UPDATE", tableName = "teachers", recordIdArgIndex = 0)
    public Map<String, Object> disableTeacher(Long teacherId) {
        Long userId = adminMapper.teacherUserId(teacherId);
        if (userId == null) {
            throw new ApiException(404, "教师不存在");
        }
        adminMapper.updateUserStatus(userId, "disabled");
        auditUpdate("users", userId, "status=disabled");
        clearTeachingCaches();
        return message("教师已弃用");
    }

    @BusinessTransaction(businessType = "enable_teacher", operation = "UPDATE", tableName = "teachers", recordIdArgIndex = 0)
    public Map<String, Object> enableTeacher(Long teacherId) {
        Long userId = adminMapper.teacherUserId(teacherId);
        if (userId == null) {
            throw new ApiException(404, "教师不存在");
        }
        adminMapper.updateUserStatus(userId, "enabled");
        auditUpdate("users", userId, "status=enabled");
        clearTeachingCaches();
        return message("教师已启用");
    }

    /** 学生列表（支持关键词搜索），缓存 Key 按 keyword 区分。 */
    public List<Map<String, Object>> listStudents(String keyword) {
        return cache.get("admin:students:" + cache.keyPart(keyword), LIST_TYPE,
                () -> adminMapper.listStudents(keyword));
    }

    /**
     * 新建学生：先在 users 表建通用用户（密码哈希为学号），再在 students 表建扩展信息。
     * 初始密码为学号本身，建议学生首次登录后修改。
     */
    @BusinessTransaction(businessType = "create_student", operation = "INSERT", tableName = "students")
    public Map<String, Object> createStudent(StudentProfileRequest request) {
        Long roleId = adminMapper.roleIdByCode("student");
        adminMapper.insertUser(request.studentNo(), PasswordUtil.hash(request.studentNo()), request.name(),
                request.email(), roleId);
        Long userId = adminMapper.lastInsertId();
        auditInsert("users", userId, "student_no=" + request.studentNo());
        adminMapper.insertStudent(userId, request);
        Long studentId = adminMapper.lastInsertId();
        auditInsert("students", studentId, "user_id=" + userId);
        clearTeachingCaches();
        return message("学生已新增，初始密码为学号");
    }

    @BusinessTransaction(businessType = "update_student", operation = "UPDATE", tableName = "students", recordIdArgIndex = 0)
    public Map<String, Object> updateStudent(Long studentId, StudentProfileRequest request) {
        Long userId = adminMapper.studentUserId(studentId);
        if (userId == null) {
            throw new ApiException(404, "学生不存在");
        }
        adminMapper.updateUserIdentity(userId, request.studentNo(), request.name());
        auditUpdate("users", userId, "identity");
        adminMapper.updateUserEmail(userId, request.email());
        auditUpdate("users", userId, "email");
        adminMapper.updateStudent(studentId, request);
        auditUpdate("students", studentId, "profile");
        clearTeachingCaches();
        return message("学生信息已更新");
    }

    @BusinessTransaction(businessType = "disable_student", operation = "UPDATE", tableName = "students", recordIdArgIndex = 0)
    public Map<String, Object> disableStudent(Long studentId) {
        Long userId = adminMapper.studentUserId(studentId);
        if (userId == null) {
            throw new ApiException(404, "学生不存在");
        }
        adminMapper.updateUserStatus(userId, "disabled");
        auditUpdate("users", userId, "status=disabled");
        clearTeachingCaches();
        return message("学生已弃用");
    }

    @BusinessTransaction(businessType = "enable_student", operation = "UPDATE", tableName = "students", recordIdArgIndex = 0)
    public Map<String, Object> enableStudent(Long studentId) {
        Long userId = adminMapper.studentUserId(studentId);
        if (userId == null) {
            throw new ApiException(404, "学生不存在");
        }
        adminMapper.updateUserStatus(userId, "enabled");
        auditUpdate("users", userId, "status=enabled");
        clearTeachingCaches();
        return message("学生已启用");
    }

    /**
     * "删除"用户：实际是软删除（设 status=disabled），保留历史数据。
     * 防呆：不能删除自己、不能删除唯一的 admin 账号。
     */
    @BusinessTransaction(businessType = "delete_user", operation = "UPDATE", tableName = "users", recordIdArgIndex = 1)
    public Map<String, Object> deleteUser(SessionUser currentUser, Long userId) {
        if (currentUser.id().equals(userId)) {
            throw new ApiException(400, "不能删除当前登录账号");
        }
        String role = adminMapper.roleCodeByUserId(userId);
        if (role == null) {
            throw new ApiException(404, "用户不存在");
        }
        if ("admin".equals(role)) {
            throw new ApiException(400, "系统管理员账号只有一个，不能删除");
        }
        adminMapper.updateUserStatus(userId, "disabled");
        auditUpdate("users", userId, "status=disabled");
        clearTeachingCaches();
        return message("用户已删除");
    }

    /**
     * 管理员目录：聚合学期/院系/专业/教师/课程/教室全部基础数据，
     * 供前端表单的 select 选项使用。缓存 Key 固定，写操作后批量失效。
     */
    public Map<String, Object> catalog() {
        return cache.get("admin:catalog", MAP_TYPE, () -> {
            Map<String, Object> currentSemester = commonMapper.currentSemester();
            Map<String, Object> selectionSemester = commonMapper.selectionSemester();
            Map<String, Object> gradingSemester = commonMapper.gradingSemester();
            return mapOf(
                    "semesters", adminMapper.semesters(),
                    "departments", adminMapper.departments(),
                    "majors", adminMapper.majors(),
                    "teachers", adminMapper.teachers(),
                    "courses", adminMapper.courses(),
                    "classrooms", adminMapper.classrooms(),
                    "currentSemester", currentSemester,
                    "selectionSemester", selectionSemester,
                    "gradingSemester", gradingSemester,
                    "selectionOpen", selectionSemester != null,
                    "gradingOpen", gradingSemester != null);
        });
    }

    public List<Map<String, Object>> listOfferings(String keyword, boolean currentOnly, Long semesterId) {
        return cache.get(
                "admin:offerings:" + currentOnly + ":" + cache.keyPart(semesterId) + ":" + cache.keyPart(keyword),
                LIST_TYPE,
                () -> withOfferingTimes(adminMapper.listOfferings(keyword, currentOnly, semesterId), "id"));
    }

    public List<Map<String, Object>> listCourses(String keyword) {
        return cache.get("admin:courses:" + cache.keyPart(keyword), LIST_TYPE,
                () -> adminMapper.listCourses(keyword));
    }

    @BusinessTransaction(businessType = "create_course", operation = "INSERT", tableName = "courses")
    public Map<String, Object> createCourse(CourseRequest request) {
        adminMapper.insertCourse(request);
        Long courseId = adminMapper.lastInsertId();
        auditInsert("courses", courseId, "code=" + request.code());
        clearTeachingCaches();
        return message("课程已新增");
    }

    @BusinessTransaction(businessType = "enable_course", operation = "UPDATE", tableName = "courses", recordIdArgIndex = 0)
    public Map<String, Object> enableCourse(Long courseId) {
        int updated = adminMapper.updateCourseStatus(courseId, "enabled");
        if (updated == 0) {
            throw new ApiException(404, "课程不存在");
        }
        auditUpdate("courses", courseId, "status=enabled");
        clearTeachingCaches();
        return message("课程已启用");
    }

    @BusinessTransaction(businessType = "disable_course", operation = "UPDATE", tableName = "courses", recordIdArgIndex = 0)
    public Map<String, Object> disableCourse(Long courseId) {
        int updated = adminMapper.updateCourseStatus(courseId, "disabled");
        if (updated == 0) {
            throw new ApiException(404, "课程不存在");
        }
        auditUpdate("courses", courseId, "status=disabled");
        clearTeachingCaches();
        return message("课程已弃用");
    }

    /**
     * 新建课程班：校验时间合法性 → 校验教师/教室冲突 → 校验课程未弃用 → 插入。
     * 先锁定活跃阶段（保证读一致），再在数据库层触发排课冲突触发器二次保护。
     */
    @BusinessTransaction(businessType = "create_offering", operation = "INSERT", tableName = "course_offerings")
    public Map<String, Object> createOffering(CreateOfferingRequest request) {
        lockActivePhasesInOrder(request.semesterId());
        validateOfferingTimes(request.times());
        validateOfferingResourceConflicts(null, request);
        validateCourseForNewOffering(request.courseId());
        validateExamRatio(request.examRatio());
        adminMapper.insertOffering(request);
        Long offeringId = adminMapper.lastInsertId();
        auditInsert("course_offerings", offeringId, "course_id=" + request.courseId());
        adminMapper.insertOfferingTimes(offeringId, request.times());
        auditInsert("course_offering_times", offeringId,
                "offering_id=" + offeringId + ", rows=" + request.times().size());
        clearTeachingCaches();
        return message("开课计划已创建");
    }

    /**
     * 修改课程班：锁定相关行 → 校验冲突 → 删除旧时间段 → 更新 → 插入新时间段。
     * 采用"删旧插新"而非逐条比对，避免复杂的时间段 diff 逻辑。
     */
    @BusinessTransaction(businessType = "update_offering", operation = "UPSERT", tableName = "course_offerings", recordIdArgIndex = 0)
    public Map<String, Object> updateOffering(Long offeringId, CreateOfferingRequest request) {
        validateOfferingTimes(request.times());
        validateExamRatio(request.examRatio());
        validateEditableOfferingStatus(request.status());
        lockOfferingMutation(offeringId, request.semesterId());
        validateOfferingResourceConflicts(offeringId, request);
        validateOfferingStudentScheduleConflicts(offeringId, request);
        if (adminMapper.countEditableOfferingById(offeringId) == 0) {
            throw new ApiException(404, "课程班不存在或已删除");
        }
        int deletedTimes = adminMapper.deleteOfferingTimes(offeringId);
        auditDelete("course_offering_times", offeringId, "offering_id=" + offeringId + ", rows=" + deletedTimes);
        int updated = adminMapper.updateOffering(offeringId, request);
        if (updated == 0) {
            throw new ApiException(404, "课程班不存在或已删除");
        }
        auditUpdate("course_offerings", offeringId, "status=" + request.status());
        adminMapper.insertOfferingTimes(offeringId, request.times());
        auditInsert("course_offering_times", offeringId,
                "offering_id=" + offeringId + ", rows=" + request.times().size());
        clearTeachingCaches();
        return message("课程信息已更新");
    }

    /**
     * 删除课程班：软删除（status=deleted），先退选所有已选学生再标记。
     * 保护条件：学期已结束不能删除、已有成绩不能删除。
     */
    @BusinessTransaction(businessType = "delete_offering", operation = "UPDATE", tableName = "course_offerings", recordIdArgIndex = 0)
    public Map<String, Object> deleteOffering(Long offeringId) {
        lockOfferingCascadeForDelete(offeringId);
        Map<String, Object> snapshot = adminMapper.offeringDeleteSnapshot(offeringId);
        if (snapshot == null) {
            throw new ApiException(404, "课程班不存在");
        }
        adminMapper.lockEnrollmentsByOffering(offeringId);
        adminMapper.lockGradesByOffering(offeringId);
        if ("deleted".equals(MapUtil.stringValue(snapshot, "status"))) {
            throw new ApiException(400, "课程班已删除");
        }
        if (MapUtil.booleanValue(snapshot, "semesterEnded")) {
            throw new ApiException(400, "课程班所在学期已结束，不能删除");
        }
        if (MapUtil.longValue(snapshot, "gradeCount") > 0) {
            throw new ApiException(400, "该课程班已有成绩，不能删除");
        }
        long selectedCount = MapUtil.longValue(snapshot, "selectedEnrollmentCount");
        if (selectedCount > 0) {
            int dropped = adminMapper.dropSelectedEnrollmentsByOffering(offeringId);
            auditUpdate("enrollments", offeringId, "offering_id=" + offeringId + ", dropped=" + dropped);
        }
        int updated = adminMapper.markOfferingDeleted(offeringId);
        if (updated == 0) {
            throw new ApiException(400, "课程班已删除");
        }
        auditUpdate("course_offerings", offeringId, "status=deleted");
        clearTeachingCaches();
        return message(selectedCount > 0 ? "课程班已删除，相关选课已退选" : "课程班已删除");
    }

    @BusinessTransaction(businessType = "create_semester", operation = "INSERT", tableName = "semesters")
    public Map<String, Object> createSemester(SemesterRequest request) {
        validateSemesterRange(null, request);
        adminMapper.insertSemester(request);
        Long semesterId = adminMapper.lastInsertId();
        auditInsert("semesters", semesterId, "name=" + request.name());
        clearTeachingCaches();
        return message("学期已新建，可开始维护该学期课程");
    }

    @BusinessTransaction(businessType = "update_semester", operation = "UPDATE", tableName = "semesters", recordIdArgIndex = 0)
    public Map<String, Object> updateSemester(Long semesterId, SemesterRequest request) {
        if (adminMapper.countSemesterById(semesterId) == 0) {
            throw new ApiException(400, "学期不存在");
        }
        lockActivePhasesInOrder(semesterId);
        validateSemesterRange(semesterId, request);
        int updated = adminMapper.updateSemester(semesterId, request);
        if (updated == 0) {
            throw new ApiException(400, "学期不存在");
        }
        auditUpdate("semesters", semesterId, "range/max_credit");
        clearTeachingCaches();
        return message("学期信息已更新");
    }

    /**
     * 开放选课：先关闭所有当前选课学期（全局同时只能有一个选课学期），
     * 再开启指定学期的选课阶段。与登分互斥——选课中不能登分。
     */
    @BusinessTransaction(businessType = "start_selection", operation = "UPSERT", tableName = "semester_active_phases", recordIdArgIndex = 0)
    public Map<String, Object> startSemesterSelection(Long semesterId) {
        ensureSemesterExists(semesterId);
        if ("archived".equals(adminMapper.semesterStatusById(semesterId))) {
            throw new ApiException(400, "已归档学期不能开始选课");
        }
        if (adminMapper.countOpenGradingBySemester(semesterId) > 0) {
            throw new ApiException(400, "该学期正在登分，不能同时开放选课");
        }
        adminMapper.lockAllActivePhases();
        int closed = adminMapper.closeAllSelectionSemesters();
        auditDelete("semester_active_phases", null, "phase=selection, rows=" + closed);
        adminMapper.updateSemesterSelectionOpen(semesterId, true);
        auditUpsert("semester_active_phases", semesterId, "phase=selection");
        clearTeachingCaches();
        return message("选课已开放");
    }

    @BusinessTransaction(businessType = "stop_selection", operation = "DELETE", tableName = "semester_active_phases", recordIdArgIndex = 0)
    public Map<String, Object> stopSemesterSelection(Long semesterId) {
        ensureSemesterExists(semesterId);
        lockActivePhasesInOrder(semesterId);
        int updated = adminMapper.updateSemesterSelectionOpen(semesterId, false);
        auditDelete("semester_active_phases", semesterId, "phase=selection, rows=" + updated);
        clearTeachingCaches();
        return message("选课已关闭");
    }

    /**
     * 开放登分：先关闭所有当前登分学期（全局同时只能有一个登分学期），
     * 再开启指定学期的登分阶段。与选课互斥——登分中不能选课。
     * 未开始的学期不能登分（但可以选课）。
     */
    @BusinessTransaction(businessType = "start_grading", operation = "UPSERT", tableName = "semester_active_phases", recordIdArgIndex = 0)
    public Map<String, Object> startSemesterGrading(Long semesterId) {
        ensureSemesterExists(semesterId);
        if ("not_started".equals(adminMapper.semesterStatusById(semesterId))) {
            throw new ApiException(400, "未开始学期不能开始登分");
        }
        if (adminMapper.countOpenSelectionBySemester(semesterId) > 0) {
            throw new ApiException(400, "该学期正在选课，不能同时开放登分");
        }
        adminMapper.lockAllActivePhases();
        int closed = adminMapper.closeAllGradingSemesters();
        auditDelete("semester_active_phases", null, "phase=grading, rows=" + closed);
        adminMapper.updateSemesterGradingOpen(semesterId, true);
        auditUpsert("semester_active_phases", semesterId, "phase=grading");
        clearTeachingCaches();
        return message("登分已开放");
    }

    @BusinessTransaction(businessType = "stop_grading", operation = "DELETE", tableName = "semester_active_phases", recordIdArgIndex = 0)
    public Map<String, Object> stopSemesterGrading(Long semesterId) {
        ensureSemesterExists(semesterId);
        lockActivePhasesInOrder(semesterId);
        int updated = adminMapper.updateSemesterGradingOpen(semesterId, false);
        auditDelete("semester_active_phases", semesterId, "phase=grading, rows=" + updated);
        clearTeachingCaches();
        return message("登分已关闭");
    }

    public List<Map<String, Object>> enrollmentReport() {
        return cache.get("admin:enrollment-report", LIST_TYPE, adminMapper::enrollmentReport);
    }

    public List<Map<String, Object>> courseRoster(Long offeringId) {
        return cache.get("admin:course-roster:" + offeringId, LIST_TYPE,
                () -> adminMapper.courseRoster(offeringId));
    }

    /**
     * 管理员代学生选课：通过学号或用户名查找 studentId，再调用选课存储过程。
     * 存储过程内部处理全部校验逻辑。
     */
    public Map<String, Object> adminSelectCourse(SessionUser user, String studentNo, Long offeringId) {
        Long studentId = adminMapper.studentIdByNoOrUsername(studentNo);
        if (studentId == null) {
            throw new ApiException(404, "学生不存在");
        }
        adminMapper.adminSelectCourse(studentId, offeringId, user.id());
        clearTeachingCaches();
        return message("已为学生选课");
    }

    /** 管理员代学生退课：通过学号+课程班ID定位选课记录。 */
    public Map<String, Object> adminDropCourse(SessionUser user, String studentNo, Long offeringId) {
        Long studentId = adminMapper.studentIdByNoOrUsername(studentNo);
        if (studentId == null) {
            throw new ApiException(404, "学生不存在");
        }
        adminMapper.adminDropCourse(studentId, offeringId, user.id());
        clearTeachingCaches();
        return message("已为学生退课");
    }

    /** 管理员代学生退课：通过选课记录ID直接退选，不需要学号和课程班ID。 */
    public Map<String, Object> adminDropEnrollment(SessionUser user, Long enrollmentId) {
        adminMapper.adminDropEnrollment(enrollmentId, user.id());
        clearTeachingCaches();
        return message("已退选");
    }

    public Map<String, Object> studentTeachingInfo(String studentNo) {
        Long studentId = adminMapper.studentIdByNoOrUsername(studentNo);
        if (studentId == null) {
            throw new ApiException(404, "学生不存在");
        }
        return cache.get("admin:student-teaching:" + studentId, MAP_TYPE, () -> mapOf(
                "enrollments", withOfferingTimes(adminMapper.studentEnrollments(studentId), "offeringId"),
                "transcript", adminMapper.studentTranscript(studentId)));
    }

    public Map<String, Object> courseGradeStats(Long offeringId) {
        return cache.get("admin:course-grade-stats:" + offeringId, MAP_TYPE,
                () -> nullToEmpty(adminMapper.courseGradeStats(offeringId)));
    }

    /**
     * 事务日志分页查询，前端传入的 page/pageSize 会被 clamp 到安全范围（pageSize 上限 10）。
     * 返回 rows + 分页元信息，前端据此渲染分页控件。
     */
    public Map<String, Object> transactionLogs(Integer page, Integer pageSize) {
        int safePageSize = clampPageSize(pageSize);
        long total = adminMapper.countTransactionSummaries();
        int totalPages = totalPages(total, safePageSize);
        int safePage = clampPage(page, totalPages);
        int offset = (safePage - 1) * safePageSize;
        return mapOf(
                "rows", adminMapper.listTransactionSummaries(safePageSize, offset),
                "page", safePage,
                "pageSize", safePageSize,
                "total", total,
                "totalPages", totalPages);
    }

    @BusinessTransaction(businessType = "create_notice", operation = "INSERT", tableName = "notices")
    public Map<String, Object> createNotice(SessionUser user, NoticeRequest request) {
        adminMapper.insertNotice(request, user.id());
        Long noticeId = adminMapper.lastInsertId();
        auditInsert("notices", noticeId, "audience=" + request.audience());
        clearTeachingCaches();
        return message("通知已发布");
    }

    @BusinessTransaction(businessType = "update_notice", operation = "UPDATE", tableName = "notices", recordIdArgIndex = 0)
    public Map<String, Object> updateNotice(Long noticeId, NoticeRequest request) {
        int updated = adminMapper.updateNotice(noticeId, request);
        if (updated == 0) {
            throw new ApiException(404, "通知不存在");
        }
        auditUpdate("notices", noticeId, "audience=" + request.audience());
        clearTeachingCaches();
        return message("通知已更新");
    }

    @BusinessTransaction(businessType = "delete_notice", operation = "DELETE", tableName = "notices", recordIdArgIndex = 0)
    public Map<String, Object> deleteNotice(Long noticeId) {
        int deleted = adminMapper.deleteNotice(noticeId);
        if (deleted == 0) {
            throw new ApiException(404, "通知不存在");
        }
        auditDelete("notices", noticeId, "rows=" + deleted);
        clearTeachingCaches();
        return message("通知已删除");
    }

    /** 构建 { "label": ..., "value": ... } 的键值对条目。 */
    static Map<String, Object> item(String label, Object value) {
        return mapOf("label", label, "value", value == null ? "-" : value);
    }

    /** 构建 { "message": "..." }，用于返回操作成功提示。 */
    static Map<String, Object> message(String message) {
        return mapOf("message", message);
    }

    /** null 安全的 Map 转换，null 时返回空 Map 避免前端解析出错。 */
    static Map<String, Object> nullToEmpty(Map<String, Object> map) {
        return map == null ? Map.of() : map;
    }

    /** 简易 Map 构造器：接受 (key1, val1, key2, val2, ...) 的可变参数，保持插入顺序。 */
    static Map<String, Object> mapOf(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return map;
    }

    /**
     * 写操作后清除所有教学相关缓存的前缀。
     * 前缀 admin:/student:/teacher: 分别对应各自角色的缓存命名空间。
     */
    private void clearTeachingCaches() {
        cache.evictByPrefix("admin:", "student:", "teacher:");
    }

    /** 记录 INSERT 审计日志（快捷方法）。 */
    private void auditInsert(String tableName, Long recordId, String message) {
        auditService.logStep("INSERT", tableName, recordId, "success", message);
    }

    /** 记录 UPDATE 审计日志（快捷方法）。 */
    private void auditUpdate(String tableName, Long recordId, String message) {
        auditService.logStep("UPDATE", tableName, recordId, "success", message);
    }

    /** 记录 DELETE 审计日志（快捷方法）。 */
    private void auditDelete(String tableName, Long recordId, String message) {
        auditService.logStep("DELETE", tableName, recordId, "success", message);
    }

    /** 记录 UPSERT 审计日志（快捷方法）。 */
    private void auditUpsert(String tableName, Long recordId, String message) {
        auditService.logStep("UPSERT", tableName, recordId, "success", message);
    }

    /**
     * 修改课程班前加锁：锁定已选学生 → 锁定活跃阶段 → 锁定课程班自身 → 锁定所有选课记录。
     * 这把锁确保修改期间不会有新学生选课或被其他操作并发修改。
     */
    private void lockOfferingMutation(Long offeringId, Long requestedSemesterId) {
        Long existingSemesterId = adminMapper.offeringSemesterId(offeringId);
        if (existingSemesterId == null) {
            throw new ApiException(404, "课程班不存在或已删除");
        }
        adminMapper.lockSelectedStudentsByOffering(offeringId);
        lockActivePhasesInOrder(existingSemesterId, requestedSemesterId);
        Long lockedOfferingId = adminMapper.lockEditableOfferingById(offeringId);
        if (lockedOfferingId == null) {
            throw new ApiException(404, "课程班不存在或已删除");
        }
        adminMapper.lockEnrollmentsByOffering(offeringId);
    }

    /** 删除课程班前锁定：已选学生 + 该课程班所属学期的活跃阶段。 */
    private void lockOfferingCascadeForDelete(Long offeringId) {
        adminMapper.lockSelectedStudentsByOffering(offeringId);
        adminMapper.lockActivePhasesByOffering(offeringId);
    }

    /**
     * 按学期 ID 升序锁定活跃阶段行，防止死锁。
     * 多学期操作可能涉及不同学期的 semester_active_phases 行，
     * 统一按 ID 排序后再加锁，确保多个并发操作以相同顺序获取锁。
     */
    private void lockActivePhasesInOrder(Long... semesterIds) {
        List<Long> ids = new ArrayList<>();
        for (Long semesterId : semesterIds) {
            if (semesterId != null && !ids.contains(semesterId)) {
                ids.add(semesterId);
            }
        }
        ids.sort(Long::compareTo);
        if (!ids.isEmpty()) {
            adminMapper.lockActivePhasesBySemesters(ids);
        }
    }

    static int clampPageSize(Integer pageSize) {
        if (pageSize == null) {
            return 10;
        }
        return Math.max(1, Math.min(pageSize, 10));
    }

    static int clampPage(Integer page, int totalPages) {
        int current = page == null ? 1 : page;
        return Math.max(1, Math.min(current, Math.max(totalPages, 1)));
    }

    static int totalPages(long total, int pageSize) {
        if (total <= 0) {
            return 1;
        }
        return (int) Math.ceil((double) total / (double) pageSize);
    }

    /** 按角色生成默认邮箱地址。 */
    private String defaultEmail(String username, String role) {
        String domain = switch (role) {
            case "teacher" -> "teacher.school.edu.cn";
            case "student" -> "student.school.edu.cn";
            default -> "school.edu.cn";
        };
        return username + "@" + domain;
    }

    /**
     * 系统资源状态监控：通过 JMX 获取 CPU/内存（回退到 Runtime API），
     * 通过 java.io 获取磁盘，通过 NetworkInterface 获取网络状态。
     * 所有异常静默处理——监控失败不应影响首页正常展示。
     */
    private Map<String, Object> systemStatus() {
        java.lang.management.OperatingSystemMXBean baseOs = ManagementFactory.getOperatingSystemMXBean();
        double cpuLoad = -1;
        long totalMemory = -1;
        long freeMemory = -1;
        if (baseOs instanceof com.sun.management.OperatingSystemMXBean os) {
            cpuLoad = os.getCpuLoad();
            totalMemory = os.getTotalMemorySize();
            freeMemory = os.getFreeMemorySize();
        }
        if (totalMemory <= 0) {
            Runtime runtime = Runtime.getRuntime();
            totalMemory = runtime.maxMemory();
            freeMemory = runtime.freeMemory() + (runtime.maxMemory() - runtime.totalMemory());
        }
        long usedMemory = Math.max(totalMemory - freeMemory, 0);

        long totalDisk = 0;
        long freeDisk = 0;
        File[] roots = File.listRoots();
        if (roots != null) {
            for (File root : roots) {
                totalDisk += Math.max(root.getTotalSpace(), 0);
                freeDisk += Math.max(root.getFreeSpace(), 0);
            }
        }
        long usedDisk = Math.max(totalDisk - freeDisk, 0);

        return mapOf(
                "cpu", usageMetric("cpu", percentFromLoad(cpuLoad), baseOs.getAvailableProcessors() + " cores"),
                "memory",
                usageMetric("memory", percent(usedMemory, totalMemory), formatCapacity(usedMemory, totalMemory)),
                "disk", usageMetric("disk", percent(usedDisk, totalDisk), formatCapacity(usedDisk, totalDisk)),
                "network", networkStatus());
    }

    /** 构建单条监控指标 { label, value, detail }。 */
    private Map<String, Object> usageMetric(String label, double value, String detail) {
        return mapOf("label", label, "value", value, "detail", detail);
    }

    /**
     * 网络状态检测：遍历所有非回环、非虚拟的网络接口，统计活跃数。
     * 至少有一个活跃接口即视为在线。
     */
    private Map<String, Object> networkStatus() {
        int activeAdapters = 0;
        int availableAdapters = 0;
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (networkInterface.isLoopback() || networkInterface.isVirtual()) {
                    continue;
                }
                availableAdapters++;
                if (networkInterface.isUp()) {
                    activeAdapters++;
                }
            }
        } catch (SocketException ignored) {
            return mapOf(
                    "label", "network",
                    "value", 0.0,
                    "status", "unknown",
                    "activeAdapters", 0,
                    "availableAdapters", 0);
        }
        boolean online = activeAdapters > 0;
        return mapOf(
                "label", "network",
                "value", online ? 100.0 : 0.0,
                "status", online ? "online" : "offline",
                "activeAdapters", activeAdapters,
                "availableAdapters", Math.max(availableAdapters, activeAdapters));
    }

    /** CPU 负载转百分比（0-1 小数乘 100）。 */
    private double percentFromLoad(double load) {
        if (load < 0) {
            return 0.0;
        }
        return round(load * 100);
    }

    /** 计算使用百分比，total<=0 时返回 0 避免除零。 */
    private double percent(long used, long total) {
        if (total <= 0) {
            return 0.0;
        }
        return round((double) used / (double) total * 100.0);
    }

    /** 保留一位小数。 */
    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private String formatCapacity(long used, long total) {
        if (total <= 0) {
            return "无法读取";
        }
        double gib = 1024.0 * 1024.0 * 1024.0;
        return String.format(Locale.ROOT, "%.1f / %.1f GB", used / gib, total / gib);
    }

    public List<Map<String, Object>> teacherOfferings(Long teacherId, Long semesterId) {
        return cache.get("admin:teacher-offerings:" + teacherId + ":" + cache.keyPart(semesterId), LIST_TYPE,
                () -> withOfferingTimes(adminMapper.teacherOfferings(teacherId, semesterId), "id"));
    }

    public List<Map<String, Object>> studentEnrollments(Long studentId, Long semesterId) {
        return cache.get("admin:student-enrollments:" + studentId + ":" + cache.keyPart(semesterId), LIST_TYPE,
                () -> withOfferingTimes(adminMapper.studentEnrollmentsBySemester(studentId, semesterId), "offeringId"));
    }

    /**
     * 为课程班/选课列表批量附加上课时间信息。
     * 先从行集合中提取所有 offeringId，批量查询 course_offering_times，
     * 再按 offeringId 分组挂载到每行。这样避免了 N+1 查询问题。
     */
    private List<Map<String, Object>> withOfferingTimes(List<Map<String, Object>> rows, String idKey) {
        List<Long> offeringIds = offeringIds(rows, idKey);
        List<Map<String, Object>> times = offeringIds.isEmpty() ? List.of() : commonMapper.offeringTimes(offeringIds);
        return attachOfferingTimes(rows, times, idKey);
    }

    /** 从行集合中提取不重复的 offeringId 列表。 */
    static List<Long> offeringIds(List<Map<String, Object>> rows, String idKey) {
        List<Long> ids = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Object value = row.get(idKey);
            if (value == null) {
                continue;
            }
            Long id = value instanceof Number number ? number.longValue() : Long.valueOf(String.valueOf(value));
            if (!ids.contains(id)) {
                ids.add(id);
            }
        }
        return ids;
    }

    /**
     * 将上课时间按 offeringId 分组挂载到每行数据上。
     * 同时提取第一条时间记录的字段平铺到行上（dayOfWeek 等），方便前端直接读取。
     */
    static List<Map<String, Object>> attachOfferingTimes(List<Map<String, Object>> rows,
            List<Map<String, Object>> times,
            String idKey) {
        Map<Long, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        for (Map<String, Object> time : times) {
            Long offeringId = MapUtil.longValue(time, "offeringId");
            grouped.computeIfAbsent(offeringId, ignored -> new ArrayList<>()).add(time);
        }
        for (Map<String, Object> row : rows) {
            Object value = row.get(idKey);
            if (value == null) {
                continue;
            }
            Long offeringId = value instanceof Number number ? number.longValue() : Long.valueOf(String.valueOf(value));
            List<Map<String, Object>> rowTimes = grouped.getOrDefault(offeringId, List.of());
            row.put("times", rowTimes);
            if (!rowTimes.isEmpty()) {
                Map<String, Object> first = rowTimes.get(0);
                row.put("dayOfWeek", first.get("dayOfWeek"));
                row.put("startSection", first.get("startSection"));
                row.put("endSection", first.get("endSection"));
                row.put("startWeek", first.get("startWeek"));
                row.put("endWeek", first.get("endWeek"));
                row.put("weekType", first.get("weekType"));
                row.put("classroomId", first.get("classroomId"));
                row.put("classroom", first.get("classroom"));
            }
        }
        return rows;
    }

    /** 校验学期日期范围合法且不与已有学期重叠。新建时 semesterId 传 null。 */
    private void validateSemesterRange(Long semesterId, SemesterRequest request) {
        LocalDate startDate;
        LocalDate endDate;
        try {
            startDate = LocalDate.parse(request.startDate());
            endDate = LocalDate.parse(request.endDate());
        } catch (DateTimeParseException | NullPointerException ex) {
            throw new ApiException(400, "学期日期格式不正确");
        }
        if (startDate.isAfter(endDate)) {
            throw new ApiException(400, "学期开始日期不能晚于结束日期");
        }
        if (adminMapper.countOverlappingSemesters(semesterId, request.startDate(), request.endDate()) > 0) {
            throw new ApiException(400, "学期日期范围不能与已有学期重叠");
        }
    }

    /** 快速检查学期存在性，不存在抛 400。 */
    private void ensureSemesterExists(Long semesterId) {
        if (adminMapper.countSemesterById(semesterId) == 0) {
            throw new ApiException(400, "学期不存在");
        }
    }

    /** 期末占比校验：null 允许（使用默认值 0.6），非 null 必须在 0-1 之间。 */
    private void validateExamRatio(Double examRatio) {
        if (examRatio == null) {
            return;
        }
        if (examRatio < 0 || examRatio > 1) {
            throw new ApiException(400, "期末占比必须在 0 到 1 之间");
        }
    }

    /** 课程班编辑时只能设置 selecting 或 closed，不允许设置为 deleted。 */
    private void validateEditableOfferingStatus(String status) {
        if (!"selecting".equals(status) && !"closed".equals(status)) {
            throw new ApiException(400, "课程班状态只能是选课中或已结课");
        }
    }

    /**
     * 校验上课时间合法性：至少一个时间段、节次不能倒置、周次不能倒置、
     * 同一课程班内的多个时间段不能互相冲突。
     */
    private void validateOfferingTimes(List<CreateOfferingRequest.OfferingTimeRequest> times) {
        if (times == null || times.isEmpty()) {
            throw new ApiException(400, "至少需要一个上课时间段");
        }
        for (int i = 0; i < times.size(); i += 1) {
            CreateOfferingRequest.OfferingTimeRequest time = times.get(i);
            if (time.startSection() > time.endSection()) {
                throw new ApiException(400, "开始节次不能大于结束节次");
            }
            if (time.startWeek() > time.endWeek()) {
                throw new ApiException(400, "起始周不能大于结束周");
            }
            for (int j = i + 1; j < times.size(); j += 1) {
                if (timeConflict(time, times.get(j))) {
                    throw new ApiException(400, "同一课程班内的上课时间段不能互相冲突");
                }
            }
        }
    }

    /**
     * 判断两个上课时间段是否冲突：同一天 + 节次重叠 + 周次重叠 + 单双周重叠。
     */
    private boolean timeConflict(CreateOfferingRequest.OfferingTimeRequest left,
            CreateOfferingRequest.OfferingTimeRequest right) {
        if (!left.dayOfWeek().equals(right.dayOfWeek())) {
            return false;
        }
        boolean sectionOverlap = !(left.endSection() < right.startSection()
                || left.startSection() > right.endSection());
        boolean weekOverlap = !(left.endWeek() < right.startWeek()
                || left.startWeek() > right.endWeek());
        boolean weekTypeOverlap = "all".equals(left.weekType())
                || "all".equals(right.weekType())
                || left.weekType().equals(right.weekType());
        return sectionOverlap && weekOverlap && weekTypeOverlap;
    }

    /** 检验新时间段与已有课程班的教师/教室冲突。offeringId 为 null 表示新建。 */
    private void validateOfferingResourceConflicts(Long offeringId, CreateOfferingRequest request) {
        int conflicts = adminMapper.countOfferingResourceConflicts(offeringId, request.semesterId(),
                request.teacherId(), request.times());
        if (conflicts > 0) {
            throw new ApiException(400, "同一时间教师或教室已有课程安排");
        }
    }

    /** 检验修改课程班时间后是否会导致已选学生课表冲突。offeringId 不能为 null。 */
    private void validateOfferingStudentScheduleConflicts(Long offeringId, CreateOfferingRequest request) {
        int conflicts = adminMapper.countOfferingStudentScheduleConflicts(offeringId, request.semesterId(),
                request.times());
        if (conflicts > 0) {
            throw new ApiException(400, "student course time conflict after offering time change");
        }
    }

    /** 新建课程班时必须确保课程未弃用。 */
    private void validateCourseForNewOffering(Long courseId) {
        String status = adminMapper.courseStatus(courseId);
        if (status == null) {
            throw new ApiException(404, "课程不存在");
        }
        if (!"enabled".equals(status)) {
            throw new ApiException(400, "弃用课程不能新建课程班");
        }
    }
}
