package com.student.management.service;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.student.management.common.ApiException;
import com.student.management.common.MapUtil;
import com.student.management.common.RedisCacheService;
import com.student.management.mapper.CommonMapper;
import com.student.management.mapper.StudentMapper;
import com.student.management.security.SessionUser;
import org.springframework.stereotype.Service;

/**
 * 学生业务服务。
 *
 * 选课/退课通过调用 MySQL 存储过程实现（sp_select_course / sp_student_drop_course），
 * 存储过程内部处理所有校验（容量/冲突/学分上限/已通过课程）和行锁。
 * Service 层调用后清除缓存，确保下次查询拿到最新数据。
 *
 * studentId 从 SessionUser.profile 中提取，不在方法参数中显式传递。
 */
@Service
public class StudentService {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<Map<String, Object>>> LIST_TYPE = new TypeReference<>() {
    };

    private final StudentMapper studentMapper;
    private final CommonMapper commonMapper;
    private final RedisCacheService cache;

    public StudentService(StudentMapper studentMapper, CommonMapper commonMapper, RedisCacheService cache) {
        this.studentMapper = studentMapper;
        this.commonMapper = commonMapper;
        this.cache = cache;
    }

    /** 学生仪表盘：已选课程数 + 已选学分 + GPA + 最近通知。 */
    public Map<String, Object> dashboard(SessionUser user) {
        Long studentId = studentId(user);
        return cache.get("student:" + studentId + ":dashboard", MAP_TYPE, () -> {
            Map<String, Object> row = studentMapper.dashboard(studentId);
            return AdminService.mapOf(
                    "summary", List.of(
                            AdminService.item("已选课程", valueOrZero(row, "selectedCourses")),
                            AdminService.item("已选学分", valueOrZero(row, "selectedCredits")),
                            AdminService.item("平均绩点", row.get("gpa") == null ? "-" : row.get("gpa"))
                    ),
                    "notices", commonMapper.listRecentNotices("student", 6)
            );
        });
    }

    /**
     * 可选课程列表：优先查选课学期的课程班，选课未开放时回退到当前学期。
     * 每行附带 enrollmentStatus（selected/dropped/null）和 passedBefore 标记。
     */
    public Map<String, Object> offerings(SessionUser user, String keyword) {
        Long studentId = studentId(user);
        return cache.get("student:" + studentId + ":offerings:" + cache.keyPart(keyword), MAP_TYPE, () -> {
            Map<String, Object> selectionSemester = commonMapper.selectionSemester();
            Map<String, Object> semester = selectionSemester == null ? commonMapper.currentSemester() : selectionSemester;
            if (semester == null) {
                throw new ApiException(500, "暂无可用学期");
            }
            Long semesterId = MapUtil.longValue(semester, "id");
            return AdminService.mapOf(
                    "semester", semester,
                    "selectionSemester", selectionSemester,
                    "selectionOpen", selectionSemester != null,
                    "rows", selectionSemester == null
                            ? List.of()
                            : withOfferingTimes(studentMapper.listCurrentOfferings(studentId, semesterId, keyword), "id")
            );
        });
    }

    /** 选课：调用存储过程 sp_select_course，所有校验在数据库层完成。 */
    public Map<String, Object> selectCourse(SessionUser user, Long offeringId) {
        studentMapper.callSelectCourse(studentId(user), offeringId, user.id());
        clearTeachingCaches();
        return AdminService.message("选课成功");
    }

    /** 退课：调用存储过程 sp_student_drop_course，传入选课记录 ID。 */
    public Map<String, Object> dropCourse(SessionUser user, Long enrollmentId) {
        studentMapper.callStudentDropCourse(studentId(user), enrollmentId, user.id());
        clearTeachingCaches();
        return AdminService.message("退课成功");
    }

    /** 课表：按学期查询已选课程，附带上课时间用于周视图渲染。 */
    public List<Map<String, Object>> schedule(SessionUser user, Long semesterId) {
        Long studentId = studentId(user);
        return cache.get("student:" + studentId + ":schedule:" + cache.keyPart(semesterId), LIST_TYPE,
                () -> withOfferingTimes(studentMapper.schedule(studentId, semesterId), "offeringId"));
    }

    /** 成绩总表：所有学期的已录入成绩（含最终分和绩点）。 */
    public List<Map<String, Object>> grades(SessionUser user) {
        Long studentId = studentId(user);
        return cache.get("student:" + studentId + ":grades", LIST_TYPE,
                () -> studentMapper.grades(studentId));
    }

    /** 成绩单：按学期筛选的成绩列表，前端用于 GPA 走势图。 */
    public Map<String, Object> transcript(SessionUser user, Long semesterId) {
        Long studentId = studentId(user);
        return cache.get("student:" + studentId + ":transcript:" + cache.keyPart(semesterId), MAP_TYPE,
                () -> AdminService.mapOf(
                        "rows", studentMapper.transcript(studentId, semesterId)
                ));
    }

    /** 从 SessionUser.profile 提取 studentId，不存在则拒绝访问。 */
    private Long studentId(SessionUser user) {
        if (!user.profile().containsKey("studentId")) {
            throw new ApiException(403, "当前账号不是学生");
        }
        return MapUtil.longValue(user.profile(), "studentId");
    }

    /** null 安全的数值提取，null 时返回 0。 */
    private Object valueOrZero(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? 0 : value;
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
