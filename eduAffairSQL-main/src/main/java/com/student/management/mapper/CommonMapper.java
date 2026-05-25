package com.student.management.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 公共查询映射器，被 Admin/Student/Teacher 等多个 Service 共用。
 *
 * CURRENT_SEMESTER_ID_SQL 是核心的"当前学期"判断逻辑：
 * 优先级为 进行中 → 即将开始（最近的）→ 已结束（最近的），
 * 该 SQL 片段被其他 Mapper 复用以保证"当前学期"定义一致。
 */
@Mapper
public interface CommonMapper {
    String CURRENT_SEMESTER_ID_SQL = """
            SELECT id
              FROM semesters
             ORDER BY
                   CASE
                     WHEN CURDATE() BETWEEN start_date AND end_date THEN 0
                     WHEN start_date > CURDATE() THEN 1
                     ELSE 2
                   END,
                   CASE WHEN start_date > CURDATE() THEN start_date END ASC,
                   CASE WHEN CURDATE() BETWEEN start_date AND end_date THEN start_date END DESC,
                   CASE WHEN CURDATE() > end_date THEN end_date END DESC,
                   id DESC
             LIMIT 1
            """;

    @Select("""
            SELECT id, title, content, audience, created_at AS createdAt
              FROM notices
             WHERE #{role} = 'admin' OR audience IN ('all', #{role})
             ORDER BY created_at DESC
             LIMIT #{limit}
            """)
    List<Map<String, Object>> listRecentNotices(@Param("role") String role, @Param("limit") int limit);

    @Select("""
            SELECT id, title, content, audience, created_at AS createdAt
              FROM notices
             WHERE #{role} = 'admin' OR audience IN ('all', #{role})
             ORDER BY created_at DESC
            """)
    List<Map<String, Object>> listNotices(@Param("role") String role);

    @Select("""
            WITH current_semester AS (
            """ + CURRENT_SEMESTER_ID_SQL + """
            )
            SELECT s.id, s.name, s.start_date AS startDate, s.end_date AS endDate,
                   s.max_credit AS maxCredit,
                   selection_phase.semester_id IS NOT NULL AS selectionOpen,
                   grading_phase.semester_id IS NOT NULL AS gradingOpen,
                   CASE
                     WHEN s.start_date > CURDATE() THEN 'not_started'
                     WHEN CURDATE() BETWEEN s.start_date AND s.end_date THEN 'active'
                     ELSE 'archived'
                   END AS status,
                   s.id = (SELECT id FROM current_semester) AS isCurrent
              FROM semesters s
              LEFT JOIN semester_active_phases selection_phase
                ON selection_phase.semester_id = s.id AND selection_phase.phase = 'selection'
              LEFT JOIN semester_active_phases grading_phase
                ON grading_phase.semester_id = s.id AND grading_phase.phase = 'grading'
             ORDER BY s.start_date DESC
            """)
    List<Map<String, Object>> semesters();

    @Select("""
            SELECT s.id, s.name, s.start_date AS startDate, s.end_date AS endDate,
                   s.max_credit AS maxCredit,
                   selection_phase.semester_id IS NOT NULL AS selectionOpen,
                   grading_phase.semester_id IS NOT NULL AS gradingOpen,
                   CASE
                     WHEN s.start_date > CURDATE() THEN 'not_started'
                     WHEN CURDATE() BETWEEN s.start_date AND s.end_date THEN 'active'
                     ELSE 'archived'
                   END AS status,
                   1 AS isCurrent
              FROM semesters s
              LEFT JOIN semester_active_phases selection_phase
                ON selection_phase.semester_id = s.id AND selection_phase.phase = 'selection'
              LEFT JOIN semester_active_phases grading_phase
                ON grading_phase.semester_id = s.id AND grading_phase.phase = 'grading'
             ORDER BY
                   CASE
                     WHEN CURDATE() BETWEEN s.start_date AND s.end_date THEN 0
                     WHEN s.start_date > CURDATE() THEN 1
                     ELSE 2
                   END,
                   CASE WHEN s.start_date > CURDATE() THEN s.start_date END ASC,
                   CASE WHEN CURDATE() BETWEEN s.start_date AND s.end_date THEN s.start_date END DESC,
                   CASE WHEN CURDATE() > s.end_date THEN s.end_date END DESC,
                   s.id DESC
             LIMIT 1
            """)
    Map<String, Object> currentSemester();

    @Select("""
            SELECT s.id, s.name, s.start_date AS startDate, s.end_date AS endDate,
                   s.max_credit AS maxCredit,
                   TRUE AS selectionOpen,
                   FALSE AS gradingOpen,
                   CASE
                     WHEN s.start_date > CURDATE() THEN 'not_started'
                     WHEN CURDATE() BETWEEN s.start_date AND s.end_date THEN 'active'
                     ELSE 'archived'
                   END AS status
              FROM semesters s
              JOIN semester_active_phases sap ON sap.semester_id = s.id
             WHERE sap.phase = 'selection'
             LIMIT 1
            """)
    Map<String, Object> selectionSemester();

    @Select("""
            SELECT s.id, s.name, s.start_date AS startDate, s.end_date AS endDate,
                   s.max_credit AS maxCredit,
                   FALSE AS selectionOpen,
                   TRUE AS gradingOpen,
                   CASE
                     WHEN s.start_date > CURDATE() THEN 'not_started'
                     WHEN CURDATE() BETWEEN s.start_date AND s.end_date THEN 'active'
                     ELSE 'archived'
                   END AS status
              FROM semesters s
              JOIN semester_active_phases sap ON sap.semester_id = s.id
             WHERE sap.phase = 'grading'
             LIMIT 1
            """)
    Map<String, Object> gradingSemester();

    @Select("""
            <script>
            SELECT cot.id, cot.offering_id AS offeringId, cot.day_of_week AS dayOfWeek,
                   cot.start_section AS startSection, cot.end_section AS endSection,
                   cot.start_week AS startWeek, cot.end_week AS endWeek, cot.week_type AS weekType,
                   cot.classroom_id AS classroomId, CONCAT(cr.building, cr.room_no) AS classroom
              FROM course_offering_times cot
              JOIN classrooms cr ON cr.id = cot.classroom_id
             WHERE cot.offering_id IN
             <foreach collection="offeringIds" item="offeringId" open="(" separator="," close=")">
               #{offeringId}
             </foreach>
             ORDER BY cot.offering_id, cot.day_of_week, cot.start_section, cot.start_week
            </script>
            """)
    List<Map<String, Object>> offeringTimes(@Param("offeringIds") List<Long> offeringIds);
}
