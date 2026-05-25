package com.student.management.service;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.student.management.common.ApiException;
import com.student.management.common.MapUtil;
import com.student.management.common.RedisCacheService;
import com.student.management.dto.GradeRequest;
import com.student.management.mapper.CommonMapper;
import com.student.management.mapper.TeacherMapper;
import com.student.management.security.SessionUser;
import org.springframework.stereotype.Service;

/**
 * 教师业务服务。
 *
 * 成绩录入通过存储过程 sp_save_grade 实现，存储过程内校验：
 * - 成绩必须在 0-100 之间
 * - 当前必须是登分阶段
 * - 教师必须是该课程班的授课教师
 * - 选课记录必须是 selected 状态
 */
@Service
public class TeacherService {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<Map<String, Object>>> LIST_TYPE = new TypeReference<>() {
    };

    private final TeacherMapper teacherMapper;
    private final CommonMapper commonMapper;
    private final RedisCacheService cache;

    public TeacherService(TeacherMapper teacherMapper, CommonMapper commonMapper, RedisCacheService cache) {
        this.teacherMapper = teacherMapper;
        this.commonMapper = commonMapper;
        this.cache = cache;
    }

    /** 教师仪表盘：负责课程数 + 授课学生数 + 平均成绩 + 最近通知。 */
    public Map<String, Object> dashboard(SessionUser user) {
        Long teacherId = teacherId(user);
        return cache.get("teacher:" + teacherId + ":dashboard", MAP_TYPE, () -> {
            Map<String, Object> row = teacherMapper.dashboard(teacherId);
            return AdminService.mapOf(
                    "summary", List.of(
                            AdminService.item("负责课程", valueOrZero(row, "offeringCount")),
                            AdminService.item("授课学生", valueOrZero(row, "studentCount")),
                            AdminService.item("平均成绩", row.get("avgScore") == null ? "暂无" : row.get("avgScore"))
                    ),
                    "notices", commonMapper.listRecentNotices("teacher", 6)
            );
        });
    }

    /** 教师所有学期课程班列表（含上课时间）。 */
    public List<Map<String, Object>> courses(SessionUser user) {
        Long teacherId = teacherId(user);
        return cache.get("teacher:" + teacherId + ":courses", LIST_TYPE,
                () -> withOfferingTimes(teacherMapper.courses(teacherId), "id"));
    }

    /** 教师当前学期排课安排（限当前学期）。 */
    public List<Map<String, Object>> schedule(SessionUser user) {
        Long teacherId = teacherId(user);
        return cache.get("teacher:" + teacherId + ":schedule", LIST_TYPE,
                () -> withOfferingTimes(teacherMapper.schedule(teacherId), "id"));
    }

    /**
     * 待登分课程列表：仅当前登分学期中教师授课的课程班。
     * 额外返回 studentCount/gradedCount/missingGradeCount 用于进度展示。
     */
    public List<Map<String, Object>> gradeCourses(SessionUser user) {
        Long teacherId = teacherId(user);
        return cache.get("teacher:" + teacherId + ":grade-courses", LIST_TYPE,
                () -> withOfferingTimes(teacherMapper.gradeCourses(teacherId), "id"));
    }

    /** 查看任意课程班的学生名单（含已有成绩）。 */
    public List<Map<String, Object>> roster(SessionUser user, Long offeringId) {
        Long teacherId = teacherId(user);
        return cache.get("teacher:" + teacherId + ":roster:" + offeringId, LIST_TYPE,
                () -> teacherMapper.roster(teacherId, offeringId));
    }

    /**
     * 登分名册：仅返回当前登分学期中教师授课课程班的学生名单。
     * 与 roster 的核心区别：限定在 grading 阶段 + 教师是自己的课程班。
     */
    public List<Map<String, Object>> gradeRoster(SessionUser user, Long offeringId) {
        Long teacherId = teacherId(user);
        return cache.get("teacher:" + teacherId + ":grade-roster:" + offeringId, LIST_TYPE,
                () -> teacherMapper.gradeRoster(teacherId, offeringId));
    }

    /** 录入成绩：调用存储过程 sp_save_grade，内部做完整的权限和阶段校验。 */
    public Map<String, Object> saveGrade(SessionUser user, GradeRequest request) {
        teacherMapper.callSaveGrade(teacherId(user), request.enrollmentId(), request.usualScore(), request.examScore(), user.id());
        clearTeachingCaches();
        return AdminService.message("成绩已保存");
    }

    /** 从 SessionUser.profile 提取 teacherId，不存在则拒绝访问。 */
    private Long teacherId(SessionUser user) {
        if (!user.profile().containsKey("teacherId")) {
            throw new ApiException(403, "当前账号不是教师账号");
        }
        return MapUtil.longValue(user.profile(), "teacherId");
    }

    /** null 安全的数值提取，null 时返回 0。 */
    private Object valueOrZero(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? 0 : value;
    }

    /** 课程成绩统计：平均分、不及格率、优秀/良好/及格/不及格人数。 */
    public Map<String, Object> courseGradeStats(SessionUser user, Long offeringId) {
        Long teacherId = teacherId(user);
        return cache.get("teacher:" + teacherId + ":grade-stats:" + offeringId, MAP_TYPE,
                () -> AdminService.nullToEmpty(teacherMapper.courseGradeStats(teacherId, offeringId)));
    }

    private void clearTeachingCaches() {
        cache.evictByPrefix("admin:", "student:", "teacher:");
    }

    private List<Map<String, Object>> withOfferingTimes(List<Map<String, Object>> rows, String idKey) {
        List<Long> offeringIds = AdminService.offeringIds(rows, idKey);
        List<Map<String, Object>> times = offeringIds.isEmpty() ? List.of() : commonMapper.offeringTimes(offeringIds);
        return AdminService.attachOfferingTimes(rows, times, idKey);
    }

}
