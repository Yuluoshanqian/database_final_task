CREATE DATABASE IF NOT EXISTS test
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE test;

SET FOREIGN_KEY_CHECKS = 0;
DROP PROCEDURE IF EXISTS sp_select_course;
DROP PROCEDURE IF EXISTS sp_student_drop_course;
DROP PROCEDURE IF EXISTS sp_admin_drop_course;
DROP PROCEDURE IF EXISTS sp_admin_drop_enrollment;
DROP PROCEDURE IF EXISTS sp_save_grade;
DROP VIEW IF EXISTS grade_results;
DROP VIEW IF EXISTS course_offering_stats;
DROP TABLE IF EXISTS transaction_log_entries;
DROP TABLE IF EXISTS transactions;
DROP TABLE IF EXISTS backup_records;
DROP TABLE IF EXISTS notices;
DROP TABLE IF EXISTS grades;
DROP TABLE IF EXISTS enrollments;
DROP TABLE IF EXISTS course_offering_times;
DROP TABLE IF EXISTS course_offerings;
DROP TABLE IF EXISTS classrooms;
DROP TABLE IF EXISTS courses;
DROP TABLE IF EXISTS semester_active_phases;
DROP TABLE IF EXISTS semesters;
DROP TABLE IF EXISTS teachers;
DROP TABLE IF EXISTS students;
DROP TABLE IF EXISTS majors;
DROP TABLE IF EXISTS departments;
DROP TABLE IF EXISTS users;
DROP TABLE IF EXISTS roles;
SET FOREIGN_KEY_CHECKS = 1;

CREATE TABLE roles (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  code VARCHAR(32) NOT NULL UNIQUE,
  name VARCHAR(64) NOT NULL
) ENGINE=InnoDB;

CREATE TABLE users (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(64) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  display_name VARCHAR(80) NOT NULL,
  email VARCHAR(120) NOT NULL UNIQUE,
  role_id BIGINT NOT NULL,
  status ENUM('enabled','disabled') NOT NULL DEFAULT 'enabled',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_users_role FOREIGN KEY (role_id) REFERENCES roles(id)
) ENGINE=InnoDB;

CREATE TABLE departments (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(80) NOT NULL UNIQUE,
  phone VARCHAR(32) NULL
) ENGINE=InnoDB;

CREATE TABLE majors (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  department_id BIGINT NOT NULL,
  name VARCHAR(80) NOT NULL,
  duration_years TINYINT NOT NULL DEFAULT 4,
  CONSTRAINT fk_majors_department FOREIGN KEY (department_id) REFERENCES departments(id),
  CONSTRAINT chk_majors_duration CHECK (duration_years BETWEEN 1 AND 8),
  UNIQUE KEY uk_major_department_name (department_id, name)
) ENGINE=InnoDB;

CREATE TABLE students (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL UNIQUE,
  student_no VARCHAR(32) NOT NULL UNIQUE,
  major_id BIGINT NOT NULL,
  admission_year SMALLINT NOT NULL,
  CONSTRAINT fk_students_user FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT fk_students_major FOREIGN KEY (major_id) REFERENCES majors(id),
  CONSTRAINT chk_students_admission_year CHECK (admission_year BETWEEN 1900 AND 2100)
) ENGINE=InnoDB;

CREATE TABLE teachers (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL UNIQUE,
  teacher_no VARCHAR(32) NOT NULL UNIQUE,
  department_id BIGINT NOT NULL,
  title VARCHAR(64) NOT NULL DEFAULT '讲师',
  CONSTRAINT fk_teachers_user FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT fk_teachers_department FOREIGN KEY (department_id) REFERENCES departments(id)
) ENGINE=InnoDB;

CREATE TABLE semesters (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(64) NOT NULL UNIQUE,
  start_date DATE NOT NULL,
  end_date DATE NOT NULL,
  max_credit DECIMAL(5,1) NOT NULL DEFAULT 30.0,
  CONSTRAINT chk_semesters_date_range CHECK (start_date <= end_date),
  CHECK (max_credit > 0)
) ENGINE=InnoDB;

CREATE TABLE semester_active_phases (
  phase ENUM('selection', 'grading') PRIMARY KEY,
  semester_id BIGINT NOT NULL UNIQUE,
  CONSTRAINT fk_semester_active_phases_semester
    FOREIGN KEY (semester_id) REFERENCES semesters(id)
    ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE courses (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  code VARCHAR(32) NOT NULL UNIQUE,
  name VARCHAR(100) NOT NULL,
  department_id BIGINT NOT NULL,
  credit DECIMAL(3,1) NOT NULL,
  status ENUM('enabled','disabled') NOT NULL DEFAULT 'enabled',
  CONSTRAINT fk_courses_department FOREIGN KEY (department_id) REFERENCES departments(id),
  CONSTRAINT chk_courses_credit CHECK (credit > 0)
) ENGINE=InnoDB;

CREATE TABLE classrooms (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  building VARCHAR(64) NOT NULL,
  room_no VARCHAR(32) NOT NULL,
  UNIQUE KEY uk_room (building, room_no)
) ENGINE=InnoDB;

CREATE TABLE course_offerings (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  course_id BIGINT NOT NULL,
  semester_id BIGINT NOT NULL,
  teacher_id BIGINT NOT NULL,
  capacity SMALLINT NOT NULL,
  exam_ratio DECIMAL(4,2) NOT NULL DEFAULT 0.60,
  status ENUM('selecting','closed','deleted') NOT NULL DEFAULT 'selecting',
  CONSTRAINT fk_offerings_course FOREIGN KEY (course_id) REFERENCES courses(id),
  CONSTRAINT fk_offerings_semester FOREIGN KEY (semester_id) REFERENCES semesters(id),
  CONSTRAINT fk_offerings_teacher FOREIGN KEY (teacher_id) REFERENCES teachers(id),
  CONSTRAINT chk_offerings_capacity CHECK (capacity > 0),
  CONSTRAINT chk_offerings_exam_ratio
  CHECK (
    exam_ratio >= 0
    AND exam_ratio <= 1
  )
) ENGINE=InnoDB;

CREATE TABLE course_offering_times (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  offering_id BIGINT NOT NULL,
  classroom_id BIGINT NOT NULL,
  day_of_week TINYINT NOT NULL COMMENT '1-7, Monday is 1',
  start_section TINYINT NOT NULL,
  end_section TINYINT NOT NULL,
  start_week TINYINT NOT NULL DEFAULT 1,
  end_week TINYINT NOT NULL DEFAULT 16,
  week_type ENUM('all','odd','even') NOT NULL DEFAULT 'all' COMMENT 'all/odd/even',
  CONSTRAINT fk_offering_times_offering FOREIGN KEY (offering_id) REFERENCES course_offerings(id) ON DELETE CASCADE,
  CONSTRAINT fk_offering_times_room FOREIGN KEY (classroom_id) REFERENCES classrooms(id),
  CHECK (day_of_week BETWEEN 1 AND 7),
  CHECK (start_section BETWEEN 1 AND 12),
  CHECK (end_section BETWEEN 1 AND 12),
  CHECK (start_section <= end_section),
  CHECK (start_week BETWEEN 1 AND 30),
  CHECK (end_week BETWEEN 1 AND 30),
  CHECK (start_week <= end_week),
  INDEX idx_offering_times_lookup (offering_id, day_of_week, start_section, end_section),
  INDEX idx_offering_times_room (classroom_id, day_of_week, start_section, end_section)
) ENGINE=InnoDB;

CREATE TABLE enrollments (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  student_id BIGINT NOT NULL,
  offering_id BIGINT NOT NULL,
  status ENUM('selected','dropped') NOT NULL DEFAULT 'selected',
  CONSTRAINT fk_enrollments_student FOREIGN KEY (student_id) REFERENCES students(id),
  CONSTRAINT fk_enrollments_offering FOREIGN KEY (offering_id) REFERENCES course_offerings(id),
  UNIQUE KEY uk_student_offering (student_id, offering_id),
  INDEX idx_enrollments_offering_status (offering_id, status),
  INDEX idx_enrollments_student_status (student_id, status)
) ENGINE=InnoDB;

CREATE TABLE grades (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  enrollment_id BIGINT NOT NULL UNIQUE,
  usual_score DECIMAL(5,2) NULL,
  exam_score DECIMAL(5,2) NULL,
  updated_by BIGINT NULL,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_grades_enrollment FOREIGN KEY (enrollment_id) REFERENCES enrollments(id),
  CONSTRAINT fk_grades_user FOREIGN KEY (updated_by) REFERENCES users(id),
  CONSTRAINT chk_grades_usual_score CHECK (usual_score IS NULL OR usual_score BETWEEN 0 AND 100),
  CONSTRAINT chk_grades_exam_score CHECK (exam_score IS NULL OR exam_score BETWEEN 0 AND 100)
) ENGINE=InnoDB;

CREATE TABLE notices (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  title VARCHAR(120) NOT NULL,
  content TEXT NOT NULL,
  audience ENUM('all','teacher','student') NOT NULL DEFAULT 'all',
  created_by BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_notices_user FOREIGN KEY (created_by) REFERENCES users(id)
) ENGINE=InnoDB;

CREATE TABLE transactions (
  transaction_id CHAR(36) PRIMARY KEY,
  business_type VARCHAR(64) NOT NULL,
  actor_user_id BIGINT NULL,
  started_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  ended_at DATETIME NULL,
  final_status ENUM('started','committed','rolled_back','failed') NOT NULL DEFAULT 'started',
  CONSTRAINT fk_transactions_actor FOREIGN KEY (actor_user_id) REFERENCES users(id),
  INDEX idx_transactions_started_at (started_at),
  INDEX idx_transactions_actor (actor_user_id)
) ENGINE=InnoDB;

CREATE TABLE transaction_log_entries (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  transaction_id CHAR(36) NOT NULL,
  operation ENUM('START','INSERT','UPDATE','DELETE','UPSERT','COMMIT','ROLLBACK') NOT NULL,
  table_name VARCHAR(64) NULL,
  record_id BIGINT NULL,
  status ENUM('started','success','failed','rolled_back') NOT NULL,
  message TEXT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_transaction_log_entries_transaction
    FOREIGN KEY (transaction_id) REFERENCES transactions(transaction_id),
  INDEX idx_transaction_log_entries_tx (transaction_id, id),
  INDEX idx_transaction_log_entries_created_at (created_at)
) ENGINE=InnoDB;

CREATE TABLE backup_records (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  database_name VARCHAR(64) NOT NULL,
  file_name VARCHAR(255) NOT NULL,
  backup_directory VARCHAR(512) NOT NULL,
  file_size_bytes BIGINT NULL,
  status ENUM('started','success','failed','deleted') NOT NULL DEFAULT 'started',
  trigger_type ENUM('scheduled','manual') NOT NULL DEFAULT 'scheduled',
  created_by BIGINT NULL,
  started_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  ended_at DATETIME NULL,
  message TEXT NULL,
  CONSTRAINT fk_backup_records_user FOREIGN KEY (created_by) REFERENCES users(id),
  CONSTRAINT chk_backup_records_file_size CHECK (file_size_bytes IS NULL OR file_size_bytes >= 0),
  UNIQUE KEY uk_backup_records_database_file (database_name, file_name),
  INDEX idx_backup_records_started_at (started_at),
  INDEX idx_backup_records_status (status)
) ENGINE=InnoDB;

DELIMITER $$

CREATE TRIGGER trg_semesters_no_overlap_before_insert
BEFORE INSERT ON semesters
FOR EACH ROW
BEGIN
  IF NEW.start_date > NEW.end_date THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'semester start_date must not be after end_date';
  END IF;
  IF EXISTS (
    SELECT 1
      FROM semesters s
     WHERE s.start_date <= NEW.end_date
       AND s.end_date >= NEW.start_date
     LIMIT 1
  ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'semester date range overlaps existing semester';
  END IF;
END$$

CREATE TRIGGER trg_semesters_no_overlap_before_update
BEFORE UPDATE ON semesters
FOR EACH ROW
BEGIN
  IF NEW.start_date > NEW.end_date THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'semester start_date must not be after end_date';
  END IF;
  IF EXISTS (
    SELECT 1
      FROM semesters s
     WHERE s.id <> NEW.id
       AND s.start_date <= NEW.end_date
       AND s.end_date >= NEW.start_date
     LIMIT 1
  ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'semester date range overlaps existing semester';
  END IF;
END$$

CREATE TRIGGER trg_enrollments_capacity_before_insert
BEFORE INSERT ON enrollments
FOR EACH ROW
BEGIN
  DECLARE v_capacity SMALLINT;
  DECLARE v_selected INT DEFAULT 0;
  IF NEW.status = 'selected' THEN
    SELECT capacity
      INTO v_capacity
      FROM course_offerings
     WHERE id = NEW.offering_id
     FOR UPDATE;
    SELECT COUNT(*)
      INTO v_selected
      FROM enrollments
     WHERE offering_id = NEW.offering_id
       AND status = 'selected';
    IF v_selected >= v_capacity THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'course offering capacity exceeded';
    END IF;
  END IF;
END$$

CREATE TRIGGER trg_enrollments_capacity_before_update
BEFORE UPDATE ON enrollments
FOR EACH ROW
BEGIN
  DECLARE v_capacity SMALLINT;
  DECLARE v_selected INT DEFAULT 0;
  IF NEW.status = 'selected' AND (OLD.status <> 'selected' OR OLD.offering_id <> NEW.offering_id) THEN
    SELECT capacity
      INTO v_capacity
      FROM course_offerings
     WHERE id = NEW.offering_id
     FOR UPDATE;
    SELECT COUNT(*)
      INTO v_selected
      FROM enrollments
     WHERE offering_id = NEW.offering_id
       AND status = 'selected'
       AND id <> OLD.id;
    IF v_selected >= v_capacity THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'course offering capacity exceeded';
    END IF;
  END IF;
END$$

CREATE TRIGGER trg_course_offerings_capacity_before_update
BEFORE UPDATE ON course_offerings
FOR EACH ROW
BEGIN
  DECLARE v_selected INT DEFAULT 0;
  SELECT COUNT(*)
    INTO v_selected
    FROM enrollments
   WHERE offering_id = OLD.id
     AND status = 'selected';
  IF NEW.capacity < v_selected THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'course offering capacity is lower than selected enrollments';
  END IF;
  IF EXISTS (
    SELECT 1
     FROM course_offering_times check_time
      JOIN course_offering_times existing_time ON existing_time.offering_id <> OLD.id
      JOIN course_offerings existing_offering ON existing_offering.id = existing_time.offering_id
     WHERE check_time.offering_id = OLD.id
       AND NEW.status <> 'deleted'
       AND existing_offering.status <> 'deleted'
       AND existing_offering.semester_id = NEW.semester_id
       AND (existing_offering.teacher_id = NEW.teacher_id
            OR existing_time.classroom_id = check_time.classroom_id)
       AND existing_time.day_of_week = check_time.day_of_week
       AND NOT (existing_time.end_section < check_time.start_section
                OR existing_time.start_section > check_time.end_section)
       AND NOT (existing_time.end_week < check_time.start_week
                OR existing_time.start_week > check_time.end_week)
       AND (existing_time.week_type = 'all'
            OR check_time.week_type = 'all'
            OR existing_time.week_type = check_time.week_type)
     LIMIT 1
  ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'teacher or classroom schedule conflict';
  END IF;
  IF EXISTS (
    SELECT 1
      FROM enrollments current_enrollment
      JOIN course_offering_times check_time ON check_time.offering_id = OLD.id
      JOIN enrollments other_enrollment
        ON other_enrollment.student_id = current_enrollment.student_id
       AND other_enrollment.status = 'selected'
       AND other_enrollment.offering_id <> current_enrollment.offering_id
      JOIN course_offerings other_offering ON other_offering.id = other_enrollment.offering_id
      JOIN course_offering_times other_time ON other_time.offering_id = other_offering.id
     WHERE current_enrollment.offering_id = OLD.id
       AND current_enrollment.status = 'selected'
       AND NEW.status <> 'deleted'
       AND other_offering.status <> 'deleted'
       AND other_offering.semester_id = NEW.semester_id
       AND other_time.day_of_week = check_time.day_of_week
       AND NOT (other_time.end_section < check_time.start_section
                OR other_time.start_section > check_time.end_section)
       AND NOT (other_time.end_week < check_time.start_week
                OR other_time.start_week > check_time.end_week)
       AND (other_time.week_type = 'all'
            OR check_time.week_type = 'all'
            OR other_time.week_type = check_time.week_type)
     LIMIT 1
  ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'student schedule conflict after offering time change';
  END IF;
END$$

CREATE TRIGGER trg_offering_times_schedule_after_insert
AFTER INSERT ON course_offering_times
FOR EACH ROW
BEGIN
  IF EXISTS (
    SELECT 1
      FROM course_offering_times existing_time
      JOIN course_offerings existing_offering ON existing_offering.id = existing_time.offering_id
      JOIN course_offerings current_offering ON current_offering.id = NEW.offering_id
     WHERE existing_time.id <> NEW.id
       AND existing_offering.status <> 'deleted'
       AND current_offering.status <> 'deleted'
       AND (existing_time.offering_id = NEW.offering_id
            OR (existing_offering.semester_id = current_offering.semester_id
                AND (existing_offering.teacher_id = current_offering.teacher_id
                     OR existing_time.classroom_id = NEW.classroom_id)))
       AND existing_time.day_of_week = NEW.day_of_week
       AND NOT (existing_time.end_section < NEW.start_section
                OR existing_time.start_section > NEW.end_section)
       AND NOT (existing_time.end_week < NEW.start_week
                OR existing_time.start_week > NEW.end_week)
       AND (existing_time.week_type = 'all'
            OR NEW.week_type = 'all'
            OR existing_time.week_type = NEW.week_type)
     LIMIT 1
  ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'course offering time conflict';
  END IF;
  IF EXISTS (
    SELECT 1
      FROM enrollments current_enrollment
      JOIN course_offerings current_offering ON current_offering.id = NEW.offering_id
      JOIN enrollments other_enrollment
        ON other_enrollment.student_id = current_enrollment.student_id
       AND other_enrollment.status = 'selected'
       AND other_enrollment.offering_id <> current_enrollment.offering_id
      JOIN course_offerings other_offering ON other_offering.id = other_enrollment.offering_id
      JOIN course_offering_times other_time ON other_time.offering_id = other_offering.id
     WHERE current_enrollment.offering_id = NEW.offering_id
       AND current_enrollment.status = 'selected'
       AND current_offering.status <> 'deleted'
       AND other_offering.status <> 'deleted'
       AND other_offering.semester_id = current_offering.semester_id
       AND other_time.day_of_week = NEW.day_of_week
       AND NOT (other_time.end_section < NEW.start_section
                OR other_time.start_section > NEW.end_section)
       AND NOT (other_time.end_week < NEW.start_week
                OR other_time.start_week > NEW.end_week)
       AND (other_time.week_type = 'all'
            OR NEW.week_type = 'all'
            OR other_time.week_type = NEW.week_type)
     LIMIT 1
  ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'student schedule conflict after offering time change';
  END IF;
END$$

CREATE TRIGGER trg_offering_times_schedule_after_update
AFTER UPDATE ON course_offering_times
FOR EACH ROW
BEGIN
  IF EXISTS (
    SELECT 1
      FROM course_offering_times existing_time
      JOIN course_offerings existing_offering ON existing_offering.id = existing_time.offering_id
      JOIN course_offerings current_offering ON current_offering.id = NEW.offering_id
     WHERE existing_time.id <> NEW.id
       AND existing_offering.status <> 'deleted'
       AND current_offering.status <> 'deleted'
       AND (existing_time.offering_id = NEW.offering_id
            OR (existing_offering.semester_id = current_offering.semester_id
                AND (existing_offering.teacher_id = current_offering.teacher_id
                     OR existing_time.classroom_id = NEW.classroom_id)))
       AND existing_time.day_of_week = NEW.day_of_week
       AND NOT (existing_time.end_section < NEW.start_section
                OR existing_time.start_section > NEW.end_section)
       AND NOT (existing_time.end_week < NEW.start_week
                OR existing_time.start_week > NEW.end_week)
       AND (existing_time.week_type = 'all'
            OR NEW.week_type = 'all'
            OR existing_time.week_type = NEW.week_type)
     LIMIT 1
  ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'course offering time conflict';
  END IF;
  IF EXISTS (
    SELECT 1
      FROM enrollments current_enrollment
      JOIN course_offerings current_offering ON current_offering.id = NEW.offering_id
      JOIN enrollments other_enrollment
        ON other_enrollment.student_id = current_enrollment.student_id
       AND other_enrollment.status = 'selected'
       AND other_enrollment.offering_id <> current_enrollment.offering_id
      JOIN course_offerings other_offering ON other_offering.id = other_enrollment.offering_id
      JOIN course_offering_times other_time ON other_time.offering_id = other_offering.id
     WHERE current_enrollment.offering_id = NEW.offering_id
       AND current_enrollment.status = 'selected'
       AND current_offering.status <> 'deleted'
       AND other_offering.status <> 'deleted'
       AND other_offering.semester_id = current_offering.semester_id
       AND other_time.day_of_week = NEW.day_of_week
       AND NOT (other_time.end_section < NEW.start_section
                OR other_time.start_section > NEW.end_section)
       AND NOT (other_time.end_week < NEW.start_week
                OR other_time.start_week > NEW.end_week)
       AND (other_time.week_type = 'all'
            OR NEW.week_type = 'all'
            OR other_time.week_type = NEW.week_type)
     LIMIT 1
  ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'student schedule conflict after offering time change';
  END IF;
END$$

CREATE VIEW course_offering_stats AS
SELECT co.id, co.course_id, co.semester_id, co.teacher_id,
       co.capacity, co.exam_ratio, co.status,
       COALESCE(selection_counts.selected_count, 0) AS selected_count
  FROM course_offerings co
  LEFT JOIN (
        SELECT offering_id, COUNT(*) AS selected_count
          FROM enrollments
         WHERE status = 'selected'
         GROUP BY offering_id
       ) selection_counts ON selection_counts.offering_id = co.id$$

CREATE VIEW grade_results AS
SELECT scored.id, scored.enrollment_id, scored.usual_score, scored.exam_score,
       scored.final_score,
       CASE
         WHEN scored.final_score IS NULL THEN NULL
         WHEN scored.final_score >= 90 THEN 4.0
         WHEN scored.final_score >= 85 THEN 3.7
         WHEN scored.final_score >= 82 THEN 3.3
         WHEN scored.final_score >= 78 THEN 3.0
         WHEN scored.final_score >= 75 THEN 2.7
         WHEN scored.final_score >= 72 THEN 2.3
         WHEN scored.final_score >= 68 THEN 2.0
         WHEN scored.final_score >= 66 THEN 1.7
         WHEN scored.final_score >= 64 THEN 1.5
         WHEN scored.final_score >= 60 THEN 1.0
         ELSE 0.0
       END AS grade_point,
       scored.updated_by, scored.updated_at
  FROM (
        SELECT g.id, g.enrollment_id, g.usual_score, g.exam_score,
               CASE
                 WHEN g.usual_score IS NOT NULL AND g.exam_score IS NOT NULL
                 THEN ROUND(g.usual_score * (1 - co.exam_ratio) + g.exam_score * co.exam_ratio, 0)
                 ELSE NULL
               END AS final_score,
               g.updated_by, g.updated_at
          FROM grades g
          JOIN enrollments e ON e.id = g.enrollment_id
          JOIN course_offerings co ON co.id = e.offering_id
       ) scored$$

-- Business lock order is fixed to reduce deadlocks:
-- students -> semester_active_phases -> course_offerings -> enrollments -> grades.
CREATE PROCEDURE sp_select_course(
  IN p_student_id BIGINT,
  IN p_offering_id BIGINT,
  IN p_actor_user_id BIGINT
)
BEGIN
  DECLARE v_tx_id CHAR(36);
  DECLARE v_error_message VARCHAR(255) DEFAULT 'transaction failed';
  DECLARE v_not_found BOOLEAN DEFAULT FALSE;
  DECLARE v_locked_student_id BIGINT;
  DECLARE v_enrollment_id BIGINT;
  DECLARE v_capacity SMALLINT;
  DECLARE v_selected INT DEFAULT 0;
  DECLARE v_status VARCHAR(16);
  DECLARE v_semester BIGINT;
  DECLARE v_current_semester BIGINT;
  DECLARE v_course BIGINT;
  DECLARE v_credit DECIMAL(3,1);
  DECLARE v_max_credit DECIMAL(5,1);
  DECLARE v_current_credit DECIMAL(5,1);
  DECLARE v_duplicate INT DEFAULT 0;
  DECLARE v_passed_before INT DEFAULT 0;
  DECLARE v_conflict INT DEFAULT 0;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    GET DIAGNOSTICS CONDITION 1 v_error_message = MESSAGE_TEXT;
    ROLLBACK;
    START TRANSACTION;
    UPDATE transactions
       SET ended_at = CURRENT_TIMESTAMP,
           final_status = 'rolled_back'
     WHERE transaction_id = v_tx_id;
    INSERT INTO transaction_log_entries(transaction_id, operation, table_name, record_id, status, message)
    VALUES(v_tx_id, 'ROLLBACK', NULL, NULL, 'rolled_back', v_error_message);
    COMMIT;
    RESIGNAL;
  END;
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_not_found = TRUE;

  SET v_tx_id = UUID();
  SET SESSION TRANSACTION ISOLATION LEVEL READ COMMITTED;

  START TRANSACTION;
  INSERT INTO transactions(transaction_id, business_type, actor_user_id, started_at, final_status)
  VALUES(v_tx_id, 'select_course', p_actor_user_id, CURRENT_TIMESTAMP, 'started');
  INSERT INTO transaction_log_entries(transaction_id, operation, status, message)
  VALUES(v_tx_id, 'START', 'started', CONCAT('student=', p_student_id, ', offering=', p_offering_id));
  COMMIT;

  START TRANSACTION;

  SET v_not_found = FALSE;
  SELECT id
    INTO v_locked_student_id
    FROM students
   WHERE id = p_student_id
   FOR UPDATE;
  IF v_not_found THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'student not found';
  END IF;

  SET v_not_found = FALSE;
  SELECT semester_id
    INTO v_current_semester
    FROM semester_active_phases
   WHERE phase = 'selection'
   FOR UPDATE;
  IF v_not_found THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'selection phase is not open';
  END IF;

  SET v_not_found = FALSE;
  SELECT co.capacity, co.status, co.semester_id, co.course_id, c.credit, s.max_credit
    INTO v_capacity, v_status, v_semester, v_course, v_credit, v_max_credit
    FROM course_offerings co
    JOIN courses c ON c.id = co.course_id
    JOIN semesters s ON s.id = co.semester_id
   WHERE co.id = p_offering_id
   FOR UPDATE;
  IF v_not_found THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'course offering not found';
  END IF;

  SELECT COALESCE(SUM(c.credit), 0)
    INTO v_current_credit
    FROM enrollments e
    JOIN course_offerings co ON co.id = e.offering_id
    JOIN courses c ON c.id = co.course_id
   WHERE e.student_id = p_student_id
     AND e.status = 'selected'
     AND co.semester_id = v_semester;

  SELECT COUNT(*)
    INTO v_duplicate
    FROM enrollments e
    JOIN course_offerings co ON co.id = e.offering_id
   WHERE e.student_id = p_student_id
     AND e.status = 'selected'
     AND co.semester_id = v_semester
     AND co.course_id = v_course;

  SELECT COUNT(*)
    INTO v_passed_before
    FROM enrollments e
    JOIN course_offerings co ON co.id = e.offering_id
    JOIN grades g ON g.enrollment_id = e.id
   WHERE e.student_id = p_student_id
     AND e.status = 'selected'
     AND co.course_id = v_course
     AND co.semester_id <> v_semester
     AND g.usual_score IS NOT NULL
     AND g.exam_score IS NOT NULL
     AND ROUND(g.usual_score * (1 - co.exam_ratio) + g.exam_score * co.exam_ratio, 0) >= 60;

  SELECT COUNT(*)
    INTO v_conflict
    FROM enrollments e
    JOIN course_offerings co ON co.id = e.offering_id
    JOIN course_offering_times selected_time ON selected_time.offering_id = p_offering_id
    JOIN course_offering_times existing_time ON existing_time.offering_id = co.id
   WHERE e.student_id = p_student_id
     AND e.status = 'selected'
     AND co.semester_id = v_semester
     AND existing_time.day_of_week = selected_time.day_of_week
     AND NOT (existing_time.end_section < selected_time.start_section
              OR existing_time.start_section > selected_time.end_section)
     AND NOT (existing_time.end_week < selected_time.start_week
              OR existing_time.start_week > selected_time.end_week)
     AND (existing_time.week_type = 'all'
          OR selected_time.week_type = 'all'
          OR existing_time.week_type = selected_time.week_type);

  SELECT COUNT(*)
    INTO v_selected
    FROM enrollments
   WHERE offering_id = p_offering_id
     AND status = 'selected';

  IF v_semester <> v_current_semester THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'only the open selection semester can be selected';
  ELSEIF v_status <> 'selecting' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'course offering is not selecting';
  ELSEIF v_selected >= v_capacity THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'course offering capacity exceeded';
  ELSEIF v_duplicate > 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'same course already selected';
  ELSEIF v_passed_before > 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'passed course cannot be selected again';
  ELSEIF v_conflict > 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'course time conflict';
  ELSEIF v_current_credit + v_credit > v_max_credit THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'semester credit limit exceeded';
  END IF;

  SELECT COUNT(*)
    INTO v_selected
    FROM enrollments
   WHERE offering_id = p_offering_id
     AND status = 'selected';
  IF v_selected >= v_capacity THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'course offering capacity exceeded';
  END IF;

  INSERT INTO enrollments(student_id, offering_id, status)
  VALUES(p_student_id, p_offering_id, 'selected')
  ON DUPLICATE KEY UPDATE status = 'selected',
                          id = LAST_INSERT_ID(id);
  SET v_enrollment_id = LAST_INSERT_ID();

  INSERT INTO transaction_log_entries(transaction_id, operation, table_name, record_id, status, message)
  VALUES(v_tx_id, 'UPSERT', 'enrollments', v_enrollment_id, 'success', 'selected');

  UPDATE transactions
     SET ended_at = CURRENT_TIMESTAMP,
         final_status = 'committed'
   WHERE transaction_id = v_tx_id;
  INSERT INTO transaction_log_entries(transaction_id, operation, status, message)
  VALUES(v_tx_id, 'COMMIT', 'success', 'committed');
  COMMIT;
END$$

CREATE PROCEDURE sp_student_drop_course(
  IN p_student_id BIGINT,
  IN p_enrollment_id BIGINT,
  IN p_actor_user_id BIGINT
)
BEGIN
  DECLARE v_tx_id CHAR(36);
  DECLARE v_error_message VARCHAR(255) DEFAULT 'transaction failed';
  DECLARE v_not_found BOOLEAN DEFAULT FALSE;
  DECLARE v_locked_student_id BIGINT;
  DECLARE v_offering_id BIGINT;
  DECLARE v_semester BIGINT;
  DECLARE v_current_semester BIGINT;
  DECLARE v_status VARCHAR(16);
  DECLARE v_grade_id BIGINT;
  DECLARE v_grade_count INT DEFAULT 0;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    GET DIAGNOSTICS CONDITION 1 v_error_message = MESSAGE_TEXT;
    ROLLBACK;
    START TRANSACTION;
    UPDATE transactions
       SET ended_at = CURRENT_TIMESTAMP,
           final_status = 'rolled_back'
     WHERE transaction_id = v_tx_id;
    INSERT INTO transaction_log_entries(transaction_id, operation, table_name, record_id, status, message)
    VALUES(v_tx_id, 'ROLLBACK', NULL, NULL, 'rolled_back', v_error_message);
    COMMIT;
    RESIGNAL;
  END;
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_not_found = TRUE;

  SET v_tx_id = UUID();
  SET SESSION TRANSACTION ISOLATION LEVEL READ COMMITTED;

  START TRANSACTION;
  INSERT INTO transactions(transaction_id, business_type, actor_user_id, started_at, final_status)
  VALUES(v_tx_id, 'student_drop_course', p_actor_user_id, CURRENT_TIMESTAMP, 'started');
  INSERT INTO transaction_log_entries(transaction_id, operation, status, message)
  VALUES(v_tx_id, 'START', 'started', CONCAT('student=', p_student_id, ', enrollment=', p_enrollment_id));
  COMMIT;

  START TRANSACTION;

  SET v_not_found = FALSE;
  SELECT offering_id
    INTO v_offering_id
    FROM enrollments
   WHERE id = p_enrollment_id
     AND student_id = p_student_id;
  IF v_not_found THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'selected enrollment not found';
  END IF;

  SET v_not_found = FALSE;
  SELECT id
    INTO v_locked_student_id
    FROM students
   WHERE id = p_student_id
   FOR UPDATE;
  IF v_not_found THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'student not found';
  END IF;

  SET v_not_found = FALSE;
  SELECT semester_id
    INTO v_current_semester
    FROM semester_active_phases
   WHERE phase = 'selection'
   FOR UPDATE;
  IF v_not_found THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'selection phase is not open';
  END IF;

  SET v_not_found = FALSE;
  SELECT semester_id
    INTO v_semester
    FROM course_offerings
   WHERE id = v_offering_id
   FOR UPDATE;
  IF v_not_found THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'course offering not found';
  END IF;

  SET v_not_found = FALSE;
  SELECT status
    INTO v_status
    FROM enrollments
   WHERE id = p_enrollment_id
     AND student_id = p_student_id
     AND offering_id = v_offering_id
   FOR UPDATE;
  IF v_not_found OR v_status <> 'selected' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'selected enrollment not found';
  END IF;

  SET v_not_found = FALSE;
  SELECT id
    INTO v_grade_id
    FROM grades
   WHERE enrollment_id = p_enrollment_id
   FOR UPDATE;
  IF v_not_found THEN
    SET v_not_found = FALSE;
  ELSE
    SELECT COUNT(*)
      INTO v_grade_count
      FROM grades
     WHERE id = v_grade_id
       AND usual_score IS NOT NULL
       AND exam_score IS NOT NULL;
  END IF;

  IF v_semester <> v_current_semester THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'only the open selection semester can be dropped';
  ELSEIF v_grade_count > 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'graded enrollment cannot be dropped';
  END IF;

  UPDATE enrollments
     SET status = 'dropped'
   WHERE id = p_enrollment_id
     AND student_id = p_student_id
     AND status = 'selected';
  IF ROW_COUNT() = 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'selected enrollment not found';
  END IF;

  INSERT INTO transaction_log_entries(transaction_id, operation, table_name, record_id, status, message)
  VALUES(v_tx_id, 'UPDATE', 'enrollments', p_enrollment_id, 'success', 'dropped');

  UPDATE transactions
     SET ended_at = CURRENT_TIMESTAMP,
         final_status = 'committed'
   WHERE transaction_id = v_tx_id;
  INSERT INTO transaction_log_entries(transaction_id, operation, status, message)
  VALUES(v_tx_id, 'COMMIT', 'success', 'committed');
  COMMIT;
END$$

CREATE PROCEDURE sp_admin_drop_course(
  IN p_student_id BIGINT,
  IN p_offering_id BIGINT,
  IN p_actor_user_id BIGINT
)
BEGIN
  DECLARE v_tx_id CHAR(36);
  DECLARE v_error_message VARCHAR(255) DEFAULT 'transaction failed';
  DECLARE v_not_found BOOLEAN DEFAULT FALSE;
  DECLARE v_locked_student_id BIGINT;
  DECLARE v_locked_offering_id BIGINT;
  DECLARE v_enrollment_id BIGINT;
  DECLARE v_status VARCHAR(16);
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    GET DIAGNOSTICS CONDITION 1 v_error_message = MESSAGE_TEXT;
    ROLLBACK;
    START TRANSACTION;
    UPDATE transactions
       SET ended_at = CURRENT_TIMESTAMP,
           final_status = 'rolled_back'
     WHERE transaction_id = v_tx_id;
    INSERT INTO transaction_log_entries(transaction_id, operation, table_name, record_id, status, message)
    VALUES(v_tx_id, 'ROLLBACK', NULL, NULL, 'rolled_back', v_error_message);
    COMMIT;
    RESIGNAL;
  END;
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_not_found = TRUE;

  SET v_tx_id = UUID();
  SET SESSION TRANSACTION ISOLATION LEVEL READ COMMITTED;

  START TRANSACTION;
  INSERT INTO transactions(transaction_id, business_type, actor_user_id, started_at, final_status)
  VALUES(v_tx_id, 'admin_drop_course', p_actor_user_id, CURRENT_TIMESTAMP, 'started');
  INSERT INTO transaction_log_entries(transaction_id, operation, status, message)
  VALUES(v_tx_id, 'START', 'started', CONCAT('student=', p_student_id, ', offering=', p_offering_id));
  COMMIT;

  START TRANSACTION;

  SET v_not_found = FALSE;
  SELECT id
    INTO v_enrollment_id
    FROM enrollments
   WHERE student_id = p_student_id
     AND offering_id = p_offering_id
     AND status = 'selected';
  IF v_not_found THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'selected enrollment not found';
  END IF;

  SET v_not_found = FALSE;
  SELECT id
    INTO v_locked_student_id
    FROM students
   WHERE id = p_student_id
   FOR UPDATE;
  IF v_not_found THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'student not found';
  END IF;

  SET v_not_found = FALSE;
  SELECT id
    INTO v_locked_offering_id
    FROM course_offerings
   WHERE id = p_offering_id
   FOR UPDATE;
  IF v_not_found THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'course offering not found';
  END IF;

  SET v_not_found = FALSE;
  SELECT status
    INTO v_status
    FROM enrollments
   WHERE id = v_enrollment_id
     AND student_id = p_student_id
     AND offering_id = p_offering_id
   FOR UPDATE;
  IF v_not_found OR v_status <> 'selected' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'selected enrollment not found';
  END IF;

  UPDATE enrollments
     SET status = 'dropped'
   WHERE id = v_enrollment_id
     AND status = 'selected';
  IF ROW_COUNT() = 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'selected enrollment not found';
  END IF;

  INSERT INTO transaction_log_entries(transaction_id, operation, table_name, record_id, status, message)
  VALUES(v_tx_id, 'UPDATE', 'enrollments', v_enrollment_id, 'success', 'dropped');

  UPDATE transactions
     SET ended_at = CURRENT_TIMESTAMP,
         final_status = 'committed'
   WHERE transaction_id = v_tx_id;
  INSERT INTO transaction_log_entries(transaction_id, operation, status, message)
  VALUES(v_tx_id, 'COMMIT', 'success', 'committed');
  COMMIT;
END$$

CREATE PROCEDURE sp_admin_drop_enrollment(
  IN p_enrollment_id BIGINT,
  IN p_actor_user_id BIGINT
)
BEGIN
  DECLARE v_tx_id CHAR(36);
  DECLARE v_error_message VARCHAR(255) DEFAULT 'transaction failed';
  DECLARE v_not_found BOOLEAN DEFAULT FALSE;
  DECLARE v_student_id BIGINT;
  DECLARE v_offering_id BIGINT;
  DECLARE v_status VARCHAR(16);
  DECLARE v_locked_student_id BIGINT;
  DECLARE v_locked_offering_id BIGINT;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    GET DIAGNOSTICS CONDITION 1 v_error_message = MESSAGE_TEXT;
    ROLLBACK;
    START TRANSACTION;
    UPDATE transactions
       SET ended_at = CURRENT_TIMESTAMP,
           final_status = 'rolled_back'
     WHERE transaction_id = v_tx_id;
    INSERT INTO transaction_log_entries(transaction_id, operation, table_name, record_id, status, message)
    VALUES(v_tx_id, 'ROLLBACK', NULL, NULL, 'rolled_back', v_error_message);
    COMMIT;
    RESIGNAL;
  END;
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_not_found = TRUE;

  SET v_tx_id = UUID();
  SET SESSION TRANSACTION ISOLATION LEVEL READ COMMITTED;

  START TRANSACTION;
  INSERT INTO transactions(transaction_id, business_type, actor_user_id, started_at, final_status)
  VALUES(v_tx_id, 'admin_drop_enrollment', p_actor_user_id, CURRENT_TIMESTAMP, 'started');
  INSERT INTO transaction_log_entries(transaction_id, operation, status, message)
  VALUES(v_tx_id, 'START', 'started', CONCAT('enrollment=', p_enrollment_id));
  COMMIT;

  START TRANSACTION;

  SET v_not_found = FALSE;
  SELECT student_id, offering_id
    INTO v_student_id, v_offering_id
    FROM enrollments
   WHERE id = p_enrollment_id;
  IF v_not_found THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'selected enrollment not found';
  END IF;

  SET v_not_found = FALSE;
  SELECT id
    INTO v_locked_student_id
    FROM students
   WHERE id = v_student_id
   FOR UPDATE;
  IF v_not_found THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'student not found';
  END IF;

  SET v_not_found = FALSE;
  SELECT id
    INTO v_locked_offering_id
    FROM course_offerings
   WHERE id = v_offering_id
   FOR UPDATE;
  IF v_not_found THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'course offering not found';
  END IF;

  SET v_not_found = FALSE;
  SELECT status
    INTO v_status
    FROM enrollments
   WHERE id = p_enrollment_id
     AND student_id = v_student_id
     AND offering_id = v_offering_id
   FOR UPDATE;
  IF v_not_found OR v_status <> 'selected' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'selected enrollment not found';
  END IF;

  UPDATE enrollments
     SET status = 'dropped'
   WHERE id = p_enrollment_id
     AND status = 'selected';
  IF ROW_COUNT() = 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'selected enrollment not found';
  END IF;

  INSERT INTO transaction_log_entries(transaction_id, operation, table_name, record_id, status, message)
  VALUES(v_tx_id, 'UPDATE', 'enrollments', p_enrollment_id, 'success', 'dropped');

  UPDATE transactions
     SET ended_at = CURRENT_TIMESTAMP,
         final_status = 'committed'
   WHERE transaction_id = v_tx_id;
  INSERT INTO transaction_log_entries(transaction_id, operation, status, message)
  VALUES(v_tx_id, 'COMMIT', 'success', 'committed');
  COMMIT;
END$$

CREATE PROCEDURE sp_save_grade(
  IN p_teacher_id BIGINT,
  IN p_enrollment_id BIGINT,
  IN p_usual_score DECIMAL(5,2),
  IN p_exam_score DECIMAL(5,2),
  IN p_actor_user_id BIGINT
)
BEGIN
  DECLARE v_tx_id CHAR(36);
  DECLARE v_error_message VARCHAR(255) DEFAULT 'transaction failed';
  DECLARE v_not_found BOOLEAN DEFAULT FALSE;
  DECLARE v_student_id BIGINT;
  DECLARE v_offering_id BIGINT;
  DECLARE v_semester BIGINT;
  DECLARE v_grading_semester BIGINT;
  DECLARE v_owner_teacher_id BIGINT;
  DECLARE v_enrollment_status VARCHAR(16);
  DECLARE v_locked_student_id BIGINT;
  DECLARE v_grade_id BIGINT;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    GET DIAGNOSTICS CONDITION 1 v_error_message = MESSAGE_TEXT;
    ROLLBACK;
    START TRANSACTION;
    UPDATE transactions
       SET ended_at = CURRENT_TIMESTAMP,
           final_status = 'rolled_back'
     WHERE transaction_id = v_tx_id;
    INSERT INTO transaction_log_entries(transaction_id, operation, table_name, record_id, status, message)
    VALUES(v_tx_id, 'ROLLBACK', NULL, NULL, 'rolled_back', v_error_message);
    COMMIT;
    RESIGNAL;
  END;
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_not_found = TRUE;

  SET v_tx_id = UUID();
  SET SESSION TRANSACTION ISOLATION LEVEL READ COMMITTED;

  START TRANSACTION;
  INSERT INTO transactions(transaction_id, business_type, actor_user_id, started_at, final_status)
  VALUES(v_tx_id, 'save_grade', p_actor_user_id, CURRENT_TIMESTAMP, 'started');
  INSERT INTO transaction_log_entries(transaction_id, operation, status, message)
  VALUES(v_tx_id, 'START', 'started', CONCAT('teacher=', p_teacher_id, ', enrollment=', p_enrollment_id));
  COMMIT;

  START TRANSACTION;

  IF p_usual_score IS NULL OR p_exam_score IS NULL
     OR p_usual_score < 0 OR p_usual_score > 100
     OR p_exam_score < 0 OR p_exam_score > 100 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'grade score must be between 0 and 100';
  END IF;

  SET v_not_found = FALSE;
  SELECT student_id, offering_id
    INTO v_student_id, v_offering_id
    FROM enrollments
   WHERE id = p_enrollment_id;
  IF v_not_found THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'selected enrollment not found';
  END IF;

  SET v_not_found = FALSE;
  SELECT id
    INTO v_locked_student_id
    FROM students
   WHERE id = v_student_id
   FOR UPDATE;
  IF v_not_found THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'student not found';
  END IF;

  SET v_not_found = FALSE;
  SELECT semester_id
    INTO v_grading_semester
    FROM semester_active_phases
   WHERE phase = 'grading'
   FOR UPDATE;
  IF v_not_found THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'grading phase is not open';
  END IF;

  SET v_not_found = FALSE;
  SELECT semester_id, teacher_id
    INTO v_semester, v_owner_teacher_id
    FROM course_offerings
   WHERE id = v_offering_id
   FOR UPDATE;
  IF v_not_found THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'course offering not found';
  END IF;

  SET v_not_found = FALSE;
  SELECT status
    INTO v_enrollment_status
    FROM enrollments
   WHERE id = p_enrollment_id
     AND student_id = v_student_id
     AND offering_id = v_offering_id
   FOR UPDATE;
  IF v_not_found OR v_enrollment_status <> 'selected' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'selected enrollment not found';
  END IF;

  SET v_not_found = FALSE;
  SELECT id
    INTO v_grade_id
    FROM grades
   WHERE enrollment_id = p_enrollment_id
   FOR UPDATE;
  IF v_not_found THEN
    SET v_not_found = FALSE;
  END IF;

  IF v_owner_teacher_id <> p_teacher_id THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'teacher is not course owner';
  ELSEIF v_semester <> v_grading_semester THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'grading is not open for this semester';
  END IF;

  INSERT INTO grades(enrollment_id, usual_score, exam_score, updated_by)
  VALUES(p_enrollment_id, p_usual_score, p_exam_score, p_actor_user_id)
  ON DUPLICATE KEY UPDATE usual_score = VALUES(usual_score),
                          exam_score = VALUES(exam_score),
                          updated_by = VALUES(updated_by),
                          id = LAST_INSERT_ID(id);
  SET v_grade_id = LAST_INSERT_ID();

  INSERT INTO transaction_log_entries(transaction_id, operation, table_name, record_id, status, message)
  VALUES(v_tx_id, 'UPSERT', 'grades', v_grade_id, 'success', CONCAT('enrollment=', p_enrollment_id));

  UPDATE transactions
     SET ended_at = CURRENT_TIMESTAMP,
         final_status = 'committed'
   WHERE transaction_id = v_tx_id;
  INSERT INTO transaction_log_entries(transaction_id, operation, status, message)
  VALUES(v_tx_id, 'COMMIT', 'success', 'committed');
  COMMIT;
END$$

DELIMITER ;

DELIMITER $$
CREATE TRIGGER trg_notices_admin_insert
BEFORE INSERT ON notices
FOR EACH ROW
BEGIN
  IF NOT EXISTS (
    SELECT 1
      FROM users u
      JOIN roles r ON r.id = u.role_id
     WHERE u.id = NEW.created_by
       AND r.code = 'admin'
  ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Only administrators can publish notices';
  END IF;
END$$

CREATE TRIGGER trg_notices_admin_update
BEFORE UPDATE ON notices
FOR EACH ROW
BEGIN
  IF NOT EXISTS (
    SELECT 1
      FROM users u
      JOIN roles r ON r.id = u.role_id
     WHERE u.id = NEW.created_by
       AND r.code = 'admin'
  ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Only administrators can publish notices';
  END IF;
END$$
DELIMITER ;
