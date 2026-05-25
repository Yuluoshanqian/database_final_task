package com.student.management.mapper;

import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 认证相关的数据库查询。通过 JOIN 一次性查出用户+角色信息，减少数据库往返。
 */
@Mapper
public interface AuthMapper {
    @Select("""
            SELECT u.id, u.username, u.password_hash AS passwordHash, u.display_name AS displayName,
                   u.email,
                   u.status, r.code AS role, r.name AS roleName
              FROM users u
              JOIN roles r ON r.id = u.role_id
             WHERE u.username = #{username}
            """)
    Map<String, Object> findUserByUsername(@Param("username") String username);

    @Select("""
            SELECT s.id AS studentId, s.student_no AS studentNo,
                   s.admission_year AS admissionYear,
                   m.id AS majorId, m.name AS majorName, d.name AS departmentName
              FROM students s
              JOIN majors m ON m.id = s.major_id
              JOIN departments d ON d.id = m.department_id
             WHERE s.user_id = #{userId}
            """)
    Map<String, Object> findStudentProfile(@Param("userId") Long userId);

    @Select("""
            SELECT t.id AS teacherId, t.teacher_no AS teacherNo, t.title,
                   d.name AS departmentName
              FROM teachers t
              JOIN departments d ON d.id = t.department_id
             WHERE t.user_id = #{userId}
            """)
    Map<String, Object> findTeacherProfile(@Param("userId") Long userId);

    @Select("SELECT password_hash FROM users WHERE id = #{userId}")
    String passwordHashByUserId(@Param("userId") Long userId);

    @Update("UPDATE users SET password_hash = #{passwordHash} WHERE id = #{userId}")
    int updatePassword(@Param("userId") Long userId, @Param("passwordHash") String passwordHash);
}
