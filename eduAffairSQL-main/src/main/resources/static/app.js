const app = document.querySelector('#app');
const modalRoot = document.querySelector('#modalRoot');
const toastRoot = document.querySelector('#toast');

const dayNames = ['', '周一', '周二', '周三', '周四', '周五', '周六', '周日'];
const weekTypeText = { all: '全部', odd: '单周', even: '双周' };
const statusText = {
  not_started: '未开始',
  active: '进行中',
  planning: '筹备中',
  selecting: '选课中',
  closed: '已结课',
  archived: '已归档',
  open: '开放',
  selected: '已选',
  dropped: '已退',
  disabled: '弃用',
  enabled: '启用',
  started: '已开始',
  success: '成功',
  failed: '失败',
  rolled_back: '已回滚',
  committed: '已提交',
  deleted: '已删除'
};
const operationText = {
  START: '开始',
  INSERT: '新增',
  UPDATE: '更新',
  DELETE: '删除',
  UPSERT: '写入',
  COMMIT: '提交',
  ROLLBACK: '回滚'
};
const businessTypeText = {
  select_course: '学生选课',
  student_drop_course: '学生退课',
  admin_drop_course: '管理员退课',
  admin_drop_enrollment: '管理员退课',
  save_grade: '录入成绩',
  create_user: '新增用户',
  delete_user: '停用用户',
  change_password: '修改密码',
  create_teacher: '新增教师',
  update_teacher: '修改教师',
  disable_teacher: '停用教师',
  enable_teacher: '启用教师',
  create_student: '新增学生',
  update_student: '修改学生',
  disable_student: '停用学生',
  enable_student: '启用学生',
  create_course: '新增课程',
  enable_course: '启用课程',
  disable_course: '停用课程',
  create_offering: '新增课程班',
  update_offering: '修改课程班',
  delete_offering: '删除课程班',
  create_semester: '新增学期',
  update_semester: '修改学期',
  start_selection: '开放选课',
  stop_selection: '关闭选课',
  start_grading: '开放登分',
  stop_grading: '关闭登分',
  create_notice: '发布通知',
  update_notice: '修改通知',
  delete_notice: '删除通知'
};
const tableText = {
  users: '用户',
  teachers: '教师',
  students: '学生',
  courses: '课程',
  course_offerings: '课程班',
  course_offering_times: '排课时间',
  enrollments: '选课记录',
  grades: '成绩',
  semesters: '学期',
  semester_active_phases: '学期阶段',
  notices: '通知'
};
const roleText = { admin: '管理员', teacher: '教师', student: '学生' };
const audienceText = { all: '全部', teacher: '教师', student: '学生' };

const navs = {
  admin: [
    ['dashboard', '首页'],
    ['selectionControl', '学期与选课'],
    ['courseCatalog', '课程管理'],
    ['courseManage', '课程班管理'],
    ['studentManage', '学生管理'],
    ['teacherManage', '教师管理'],
    ['noticeManage', '通知发布'],
    ['auditLogs', '事务日志'],
    ['backupManage', '逻辑备份'],
    ['profile', '个人信息']
  ],
  teacher: [
    ['dashboard', '首页'],
    ['courses', '任课课程'],
    ['teachingSchedule', '排课安排'],
    ['gradeEntry', '成绩登录'],
    ['noticeManage', '通知公告'],
    ['profile', '个人信息']
  ],
  student: [
    ['dashboard', '首页'],
    ['courseSelect', '在线选课'],
    ['selectedCourses', '我的课表'],
    ['grades', '成绩总表'],
    ['noticeManage', '通知公告'],
    ['profile', '个人信息']
  ]
};

const routeTitles = {
  dashboard: '首页',
  selectionControl: '学期与选课',
  courseCatalog: '课程管理',
  courseManage: '课程班管理',
  studentManage: '学生管理',
  teacherManage: '教师管理',
  noticeManage: '通知发布',
  auditLogs: '事务日志',
  backupManage: '逻辑备份',
  profile: '个人信息',
  courses: '任课课程',
  teachingSchedule: '排课安排',
  gradeEntry: '成绩登录',
  courseSelect: '在线选课',
  selectedCourses: '我的课表',
  grades: '成绩总表'
};

const studentSelectedCreditRoutes = new Set(['courseSelect', 'selectedCourses', 'grades', 'noticeManage', 'profile']);

const state = {
  token: localStorage.getItem('course-ui-token') || '',
  user: readJson('course-ui-user'),
  route: localStorage.getItem('course-ui-route') || 'dashboard',
  catalog: null,
  adminCatalog: null,
  landing: null,
  routeData: {},
  modal: '',
  selected: {
    adminRosterOfferingId: null,
    gradeOfferingId: null,
    transcriptSemesterId: 'all',
    offeringSemesterId: null,
    teacherOfferingSemesterId: null,
    studentEnrollmentSemesterId: null,
    scheduleSemesterId: null
  },
  filters: {
    teacherKeyword: '',
    studentKeyword: '',
    offeringKeyword: '',
    adminCourseKeyword: '',
    courseKeyword: '',
    studentNo: ''
  },
  offeringPage: 1,
  offeringPageSize: 10,
  adminCoursePage: 1,
  adminCoursePageSize: 10,
  studentPage: 1,
  studentPageSize: 10,
  teacherPage: 1,
  teacherPageSize: 10,
  noticePage: 1,
  noticePageSize: 5,
  logPage: 1,
  logPageSize: 10,
  backupPage: 1,
  backupPageSize: 10
};

function readJson(key) {
  try {
    return JSON.parse(localStorage.getItem(key) || 'null');
  } catch {
    return null;
  }
}

function saveSession(login) {
  state.token = login.token;
  state.user = login.user;
  localStorage.setItem('course-ui-token', login.token);
  localStorage.setItem('course-ui-user', JSON.stringify(login.user));
}

function clearSession() {
  state.token = '';
  state.user = null;
  state.catalog = null;
  state.adminCatalog = null;
  state.routeData = {};
  localStorage.removeItem('course-ui-token');
  localStorage.removeItem('course-ui-user');
}

async function api(path, options = {}) {
  const headers = { ...(options.headers || {}) };
  if (!(options.body instanceof FormData)) {
    headers['Content-Type'] = 'application/json';
  }
  if (state.token) {
    headers.Authorization = `Bearer ${state.token}`;
  }
  const response = await fetch(path, { ...options, headers });
  let payload = null;
  try {
    payload = await response.json();
  } catch {
    payload = null;
  }
  if (!response.ok || (payload && payload.success === false)) {
    if (response.status === 401) {
      clearSession();
      renderLogin();
    }
    throw new Error(payload?.message || '请求失败');
  }
  return payload?.data ?? payload;
}

function escapeHtml(value) {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;');
}

function text(value, fallback = '') {
  const normalized = value === null || value === undefined || value === '' ? fallback : value;
  return escapeHtml(normalized);
}

function number(value, fallback = '0') {
  if (value === null || value === undefined || value === '') return fallback;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? String(parsed) : escapeHtml(value);
}

function creditNumber(value) {
  const parsed = Number(value || 0);
  if (!Number.isFinite(parsed)) return '0';
  return String(Math.round(parsed * 10) / 10);
}

function finalScoreText(value, fallback = '\u6682\u65e0') {
  if (value === null || value === undefined || value === '') return fallback;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? String(Math.round(parsed)) : text(value);
}

function bool(value) {
  return value === true || value === 1 || value === '1';
}

function dateRange(start, end) {
  if (!start && !end) return '未设置';
  return `${start || '-'} 至 ${end || '-'}`;
}

function offeringTimes(row) {
  if (Array.isArray(row?.times)) return row.times;
  if (typeof row?.times === 'string' && row.times.trim()) {
    try {
      const parsed = JSON.parse(row.times);
      if (Array.isArray(parsed)) return parsed;
    } catch {
      return [];
    }
  }
  if (row?.dayOfWeek) {
    return [{
      dayOfWeek: row.dayOfWeek,
      classroomId: row.classroomId,
      classroom: row.classroom,
      startSection: row.startSection,
      endSection: row.endSection,
      startWeek: row.startWeek || 1,
      endWeek: row.endWeek || 16,
      weekType: row.weekType || 'all'
    }];
  }
  return [];
}

function courseTime(row) {
  const times = offeringTimes(row);
  if (!times.length) return '时间待定';
  return times.map(timeText).join('；');
}

function timeText(time) {
  const weekRange = `${text(time.startWeek || 1)}-${text(time.endWeek || 16)}周`;
  const type = weekTypeText[time.weekType] || time.weekType || '全部';
  const room = time.classroom ? ` · ${text(time.classroom)}` : '';
  return `${text(dayNames[time.dayOfWeek])} ${text(time.startSection)}-${text(time.endSection)}节 · ${weekRange} · ${text(type)}${room}`;
}

function weekTypeOptions(selected = 'all') {
  return Object.entries(weekTypeText).map(([value, label]) => (
    `<option value="${value}" ${selected === value ? 'selected' : ''}>${label}</option>`
  )).join('');
}

function dayOptions(selected = 1) {
  return [1, 2, 3, 4, 5, 6, 7].map((day) => (
    `<option value="${day}" ${Number(selected) === day ? 'selected' : ''}>${dayNames[day]}</option>`
  )).join('');
}

function offeringTimesEditor(times = []) {
  const rows = times.length ? times : [{ dayOfWeek: 1, startSection: 1, endSection: 2, startWeek: 1, endWeek: 16, weekType: 'all' }];
  return `
    <div class="field full offering-times-field">
      <span>上课时间段</span>
      <div class="offering-times" data-offering-times>
        ${rows.map((time) => offeringTimeFields(time)).join('')}
      </div>
      <button class="btn" type="button" data-action="add-offering-time">添加时间段</button>
    </div>
  `;
}

function offeringTimeFields(time = {}) {
  const catalog = state.adminCatalog || {};
  return `
    <div class="time-segment" data-time-segment>
      <label><span>教室</span><select name="timeClassroomId" required>${options(catalog.classrooms || [], 'id', (item) => `${item.building}${item.roomNo}`, time.classroomId)}</select></label>
      <label><span>星期</span><select name="timeDayOfWeek" required>${dayOptions(time.dayOfWeek || 1)}</select></label>
      <label><span>开始节</span><input name="timeStartSection" type="number" min="1" max="12" value="${text(time.startSection || 1)}" required></label>
      <label><span>结束节</span><input name="timeEndSection" type="number" min="1" max="12" value="${text(time.endSection || 2)}" required></label>
      <label><span>起始周</span><input name="timeStartWeek" type="number" min="1" max="30" value="${text(time.startWeek || 1)}" required></label>
      <label><span>结束周</span><input name="timeEndWeek" type="number" min="1" max="30" value="${text(time.endWeek || 16)}" required></label>
      <label><span>单双周</span><select name="timeWeekType" required>${weekTypeOptions(time.weekType || 'all')}</select></label>
      <button class="btn btn-danger" type="button" data-action="remove-offering-time">删除</button>
    </div>
  `;
}

function badge(status) {
  const cls = status === 'selecting' || status === 'active' || status === 'open' || status === 'enabled' || status === 'selected' || status === 'success' || status === 'committed'
    ? 'status-success'
    : status === 'planning' || status === 'not_started' || status === 'started'
      ? 'status-warn'
      : status === 'archived' || status === 'deleted'
        ? 'status-muted'
        : status === 'closed' || status === 'disabled' || status === 'failed' || status === 'rolled_back'
          ? 'status-danger'
          : '';
  return `<span class="status-tag ${cls}">${text(statusText[status] || status)}</span>`;
}

function courseBadge(status) {
  const cls = status === 'enabled' ? 'status-success' : 'status-danger';
  const label = status === 'disabled' ? '弃用' : status === 'enabled' ? '启用' : status;
  return `<span class="status-tag ${cls}">${text(label)}</span>`;
}

function audienceBadge(audience) {
  const cls = audience === 'all'
    ? 'status-success'
    : audience === 'teacher'
      ? 'status-warn'
      : '';
  return `<span class="status-tag ${cls}">${text(audienceText[audience] || audience || '全部')}</span>`;
}

function toast(message, type = 'success') {
  const node = document.createElement('div');
  node.className = `toast ${type}`;
  node.textContent = message;
  toastRoot.appendChild(node);
  window.setTimeout(() => node.remove(), 3200);
}

function formObject(form) {
  const data = Object.fromEntries(new FormData(form).entries());
  Object.keys(data).forEach((key) => {
    if (data[key] === '') data[key] = null;
  });
  return data;
}

function asNumberFields(data, fields) {
  fields.forEach((field) => {
    if (data[field] !== null && data[field] !== undefined) data[field] = Number(data[field]);
  });
  return data;
}

function collectOfferingPayload(form) {
  const raw = formObject(form);
  const data = asNumberFields(raw, ['courseId', 'semesterId', 'teacherId', 'capacity']);
  data.examRatio = raw.examRatio != null ? Number(raw.examRatio) / 100 : null;
  ['timeClassroomId', 'timeDayOfWeek', 'timeStartSection', 'timeEndSection', 'timeStartWeek', 'timeEndWeek', 'timeWeekType'].forEach((field) => {
    delete data[field];
  });
  data.times = Array.from(form.querySelectorAll('[data-time-segment]')).map((segment) => ({
    classroomId: Number(segment.querySelector('[name="timeClassroomId"]').value),
    dayOfWeek: Number(segment.querySelector('[name="timeDayOfWeek"]').value),
    startSection: Number(segment.querySelector('[name="timeStartSection"]').value),
    endSection: Number(segment.querySelector('[name="timeEndSection"]').value),
    startWeek: Number(segment.querySelector('[name="timeStartWeek"]').value),
    endWeek: Number(segment.querySelector('[name="timeEndWeek"]').value),
    weekType: segment.querySelector('[name="timeWeekType"]').value
  }));
  return data;
}

function validateSemesterPayload(data, excludedId = null) {
  if (!data.startDate || !data.endDate) return true;
  if (data.startDate > data.endDate) {
    toast('学期开始日期不能晚于结束日期', 'error');
    return false;
  }
  const semesters = state.adminCatalog?.semesters || [];
  const overlap = semesters.some((term) => {
    if (excludedId && String(term.id) === String(excludedId)) return false;
    return term.startDate <= data.endDate && term.endDate >= data.startDate;
  });
  if (overlap) {
    toast('学期日期范围不能与已有学期重叠', 'error');
    return false;
  }
  return true;
}

async function loadLanding() {
  state.landing = await api('/api/public/landing');
}

async function loadCatalog(force = false) {
  if (!state.token) return;
  if (!state.catalog || force) {
    state.catalog = await api('/api/catalog');
  }
  if (state.user?.role === 'admin' && (!state.adminCatalog || force)) {
    state.adminCatalog = await api('/api/admin/catalog');
  }
}

async function loadRoute(force = false) {
  if (!state.user) return;
  const role = state.user.role;
  const route = state.route;
  await loadCatalog(force);

  if (route === 'dashboard') {
    state.routeData.dashboard = await api('/api/dashboard');
    if (role === 'teacher') {
      state.routeData.teacherCourses = await api('/api/teacher/courses');
      state.routeData.teacherGradeCourses = await api('/api/teacher/grade-courses');
    } else if (role === 'student') {
      state.routeData.schedule = await api('/api/student/schedule');
      state.routeData.transcript = await api('/api/student/transcript');
    }
    return;
  }

  if (role === 'admin') {
    if (route === 'teacherManage') {
      state.routeData.teachers = await api(`/api/admin/teachers?keyword=${encodeURIComponent(state.filters.teacherKeyword || '')}`);
    } else if (route === 'studentManage') {
      state.routeData.students = await api(`/api/admin/students?keyword=${encodeURIComponent(state.filters.studentKeyword || '')}`);
    } else if (route === 'courseCatalog') {
      state.routeData.adminCourses = await api(`/api/admin/courses?keyword=${encodeURIComponent(state.filters.adminCourseKeyword || '')}`);
    } else if (route === 'courseManage') {
      const semesterId = ensureAdminSemesterSelection('offeringSemesterId');
      const params = new URLSearchParams({ keyword: state.filters.offeringKeyword || '' });
      if (semesterId) params.set('semesterId', semesterId);
      state.routeData.offerings = await api(`/api/admin/offerings?${params.toString()}`);
    } else if (route === 'auditLogs') {
      state.routeData.auditLogs = await api(`/api/admin/logs?page=${encodeURIComponent(state.logPage)}&pageSize=${encodeURIComponent(state.logPageSize)}`);
    } else if (route === 'backupManage') {
      state.routeData.backups = await api(`/api/admin/backups?page=${encodeURIComponent(state.backupPage)}&pageSize=${encodeURIComponent(state.backupPageSize)}`);
    } else if (route === 'studentTeachingSearch') {
    return;
  }
  return;
}

  if (role === 'teacher') {
    if (route === 'courses') {
      state.routeData.teacherCourses = await api('/api/teacher/courses');
    }
    if (route === 'teachingSchedule') {
      state.routeData.teacherSchedule = await api('/api/teacher/schedule');
    }
    if (route === 'gradeEntry') {
      state.routeData.teacherGradeCourses = await api('/api/teacher/grade-courses');
      const gradable = state.routeData.teacherGradeCourses || [];
      if (!gradable.length) {
        state.selected.gradeOfferingId = null;
        state.routeData.gradeRoster = [];
        return;
      }
      if (!gradable.some((item) => String(item.id) === String(state.selected.gradeOfferingId))) {
        state.selected.gradeOfferingId = String(gradable[0].id);
      }
      if (state.selected.gradeOfferingId) {
        state.routeData.gradeRoster = await api(`/api/teacher/grade-roster?offeringId=${encodeURIComponent(state.selected.gradeOfferingId)}`);
      } else {
        state.routeData.gradeRoster = [];
      }
    }
    return;
  }

  if (role === 'student') {
    if (studentSelectedCreditRoutes.has(route)) {
      const semesterId = route === 'selectedCourses' ? ensureCatalogSemesterSelection('scheduleSemesterId') : '';
      const query = semesterId ? `?semesterId=${encodeURIComponent(semesterId)}` : '';
      state.routeData.schedule = await api(`/api/student/schedule${query}`);
    }
    if (route === 'courseSelect') {
      state.routeData.studentOfferings = await api(`/api/student/offerings?keyword=${encodeURIComponent(state.filters.courseKeyword || '')}`);
    } else if (route === 'grades') {
      state.routeData.transcript = await api('/api/student/transcript');
    }
  }
}

async function boot() {
  try {
    if (!state.token || !state.user) {
      await loadLanding();
      renderLogin();
      return;
    }
    ensureRoute();
    await loadRoute(true);
    renderShell();
  } catch (error) {
    toast(error.message, 'error');
    if (!state.user) renderLogin();
  }
}

function ensureRoute() {
  const allowed = navs[state.user?.role] || [];
  if (!allowed.some(([id]) => id === state.route)) {
    state.route = 'dashboard';
    localStorage.setItem('course-ui-route', state.route);
  }
}

function renderLogin() {
  app.innerHTML = loginView();
  modalRoot.innerHTML = '';
}

function topbarTerm() {
  const term = state.catalog?.currentSemester;
  return `
    <div class="global-search term-window">
      <span class="small muted">学期日期</span>
      <strong>${text(dateRange(term?.startDate, term?.endDate))}</strong>
    </div>
  `;
}

function termStrip() {
  const term = state.catalog?.currentSemester;
  const selectionSemester = state.catalog?.selectionSemester;
  const gradingSemester = state.catalog?.gradingSemester;
  const role = state.user?.role;
  const creditLabel = state.route === 'selectedCourses' ? '所选学期已选学分' : '本学期已选学分';
  const selectedCredits = state.user?.role === 'student' && studentSelectedCreditRoutes.has(state.route)
    ? `<div class="term-item"><span>${creditLabel}</span><strong>${creditNumber(selectedTermCredits())} 学分</strong></div>`
    : '';
  const selectionStatus = role !== 'teacher'
    ? `<div class="term-item"><span>选课状态</span><strong>${selectionSemester ? `${text(selectionSemester.name)}选课中` : '未开放选课'}</strong></div>`
    : '';
  const gradingStatus = role !== 'student'
    ? `<div class="term-item"><span>登分状态</span><strong>${gradingSemester ? `${text(gradingSemester.name)}登分中` : '未开放登分'}</strong></div>`
    : '';
  return `
    <section class="term-strip">
      <div class="term-item"><span>当前学期</span><strong>${text(term?.name)}</strong></div>
      <div class="term-item"><span>学期日期</span><strong>${text(dateRange(term?.startDate, term?.endDate))}</strong></div>
      ${selectionStatus}
      ${gradingStatus}
      ${selectedCredits}
    </section>
  `;
}

function selectedTermCredits() {
  if (state.route === 'courseSelect') {
    const seen = new Set();
    return (state.routeData.studentOfferings?.rows || []).reduce((sum, row) => {
      if (row.enrollmentStatus !== 'selected') return sum;
      const key = row.enrollmentId || row.id;
      if (seen.has(key)) return sum;
      seen.add(key);
      const credit = Number(row.credit || 0);
      return Number.isFinite(credit) ? sum + credit : sum;
    }, 0);
  }
  const seen = new Set();
  return (state.routeData.schedule || []).reduce((sum, row) => {
    const key = row.enrollmentId || `${row.courseCode}-${row.courseName}`;
    if (seen.has(key)) return sum;
    seen.add(key);
    const credit = Number(row.credit || 0);
    return Number.isFinite(credit) ? sum + credit : sum;
  }, 0);
}

function renderRoute() {
  if (state.route === 'dashboard') return renderDashboard();
  if (state.route === 'profile') return renderProfile();
  if (state.user.role === 'admin') return renderAdminRoute();
  if (state.user.role === 'teacher') return renderTeacherRoute();
  if (state.user.role === 'student') return renderStudentRoute();
  return empty('没有可用页面');
}

function gradeDistribution(distribution = {}) {
  const rows = [
    ['优秀', distribution.excellent],
    ['良好', distribution.good],
    ['及格', distribution.passed],
    ['不及格', distribution.failed]
  ];
  const total = rows.reduce((sum, [, value]) => sum + Number(value || 0), 0) || 1;
  return `
    <div class="bar-chart">
      ${rows.map(([label, value]) => {
        const percent = Math.round((Number(value || 0) / total) * 100);
        return `
          <div class="bar-row">
            <span>${label}</span>
            <div class="bar-track"><div class="bar-fill" style="width:${percent}%"></div></div>
            <b>${number(value)}</b>
          </div>
        `;
      }).join('')}
    </div>
  `;
}

function renderProfile() {
  const user = state.user || {};
  const profile = user.profile || {};
  const rows = [
    ['账号', user.username],
    ['姓名', user.displayName],
    ['角色', roleText[user.role] || user.roleName],
    ['邮箱', user.email]
  ];
  if (user.role === 'teacher') {
    rows.push(['工号', profile.teacherNo], ['院系', profile.departmentName], ['职称', profile.title]);
  } else if (user.role === 'student') {
    rows.push(['学号', profile.studentNo], ['院系', profile.departmentName], ['专业', profile.majorName], ['入学年份', profile.admissionYear]);
  } else {
    rows.push(['账号类型', '系统管理员']);
  }
  return `
    <div class="profile-layout">
      <section class="panel profile-card">
        <div class="profile-head">
          <div class="profile-avatar">${text((user.displayName || user.username || 'U').slice(0, 1))}</div>
          <div>
            <span class="eyebrow">个人信息</span>
            <h2>${display(user.displayName)}</h2>
            <p>${text(roleText[user.role] || user.roleName || '')}</p>
          </div>
        </div>
        <div class="profile-info-grid">
          ${rows.map(([label, value]) => `
            <div class="profile-info-item">
              <span>${text(label)}</span>
              <strong>${display(value)}</strong>
            </div>
          `).join('')}
        </div>
      </section>
      <section class="panel password-card">
        <div class="panel-header"><div><h2>修改密码</h2><p>修改后需要重新登录。</p></div></div>
        <form class="form-grid password-form" data-form="change-password">
          <label class="field full"><span>原密码</span><input name="oldPassword" type="password" autocomplete="current-password" required></label>
          <label class="field full"><span>新密码</span><input name="newPassword" type="password" autocomplete="new-password" required></label>
          <label class="field full"><span>确认新密码</span><input name="confirmPassword" type="password" autocomplete="new-password" required></label>
          <div class="form-actions"><button class="btn btn-primary" type="submit">保存密码</button></div>
        </form>
      </section>
    </div>
  `;
}

function renderAdminRoute() {
  if (state.route === 'selectionControl') return renderSemesterAdmin();
  if (state.route === 'courseCatalog') return renderCourseCatalog();
  if (state.route === 'courseManage') return renderCourseManage();
  if (state.route === 'studentManage') return renderStudents();
  if (state.route === 'teacherManage') return renderTeachers();
  if (state.route === 'noticeManage') return renderNoticeManage('admin');
  if (state.route === 'auditLogs') return renderAuditLogs();
  if (state.route === 'backupManage') return renderBackupManage();
  return empty('页面不存在');
}

function renderSemesterAdmin() {
  const catalog = state.adminCatalog || {};
  const semesters = catalog.semesters || [];
  return `
    <div class="page-grid">
      <section class="panel">
        <div class="panel-header">
          <div><h2>学期管理</h2></div>
          <div class="panel-actions">
            <button class="btn btn-primary" data-action="open-create-semester-modal">新建学期</button>
          </div>
        </div>
        <div class="table-wrap">
          <table>
            <thead><tr><th class="col-term">学期</th><th class="col-date">日期</th><th class="col-credit">最大学分</th><th class="col-status">状态</th><th class="col-semester-action">编辑学期</th><th class="col-phase-action">开始/结束选课</th><th class="col-phase-action">开始/结束登分</th></tr></thead>
            <tbody>
              ${semesters.map((term) => `
                <tr>
                  <td>${text(term.name)}</td>
                  <td>${text(dateRange(term.startDate, term.endDate))}</td>
                  <td>${creditNumber(term.maxCredit)} 学分</td>
                  <td>${badge(term.status)}</td>
                  <td>
                    <button class="btn btn-sm" data-action="open-edit-semester-modal" data-id="${term.id}">编辑</button>
                  </td>
                  <td>${semesterSelectionAction(term)}</td>
                  <td>${semesterGradingAction(term)}</td>
                </tr>
              `).join('')}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  `;
}

function semesterSelectionAction(term) {
  const selectionOpen = bool(term.selectionOpen);
  const gradingOpen = bool(term.gradingOpen);
  const archived = term.status === 'archived';
  if (selectionOpen) {
    return `<button class="btn btn-sm btn-danger" data-action="semester-selection-stop" data-id="${term.id}">结束选课</button>`;
  }
  const disabled = gradingOpen || archived;
  const title = gradingOpen ? '该学期正在登分' : archived ? '已归档学期不能开始选课' : '';
  return `<button class="btn btn-sm btn-primary" data-action="semester-selection-start" data-id="${term.id}" ${disabled ? `disabled title="${title}"` : ''}>开始选课</button>`;
}

function semesterGradingAction(term) {
  const selectionOpen = bool(term.selectionOpen);
  const gradingOpen = bool(term.gradingOpen);
  const notStarted = term.status === 'not_started';
  if (gradingOpen) {
    return `<button class="btn btn-sm btn-danger" data-action="semester-grading-stop" data-id="${term.id}">结束登分</button>`;
  }
  const disabled = selectionOpen || notStarted;
  const title = selectionOpen ? '该学期正在选课' : notStarted ? '未开始学期不能开始登分' : '';
  return `<button class="btn btn-sm btn-primary" data-action="semester-grading-start" data-id="${term.id}" ${disabled ? `disabled title="${title}"` : ''}>开始登分</button>`;
}

function renderCreateSemesterModal() {
  return `
    <div class="modal-backdrop">
      <div class="modal">
        <div class="modal-header">
          <h2>新建学期</h2>
          <button class="btn btn-ghost" data-action="close-modal">&times;</button>
        </div>
        <div class="modal-body">
          <form class="form-grid" data-form="create-semester">
            ${semesterFields({}, false)}
            <div class="form-actions">
              <button class="btn" type="button" data-action="close-modal">取消</button>
              <button class="btn btn-primary" type="submit">新建学期</button>
            </div>
          </form>
        </div>
      </div>
    </div>
  `;
}

function renderEditSemesterModal(semesterId) {
  const catalog = state.adminCatalog || {};
  const current = (catalog.semesters || []).find((term) => String(term.id) === String(semesterId)) || catalog.currentSemester;
  if (!current) {
    return `
      <div class="modal-backdrop">
        <div class="modal">
          <div class="modal-header">
            <h2>编辑学期</h2>
            <button class="btn btn-ghost" data-action="close-modal">&times;</button>
          </div>
          <div class="modal-body">${empty('暂无当前学期')}</div>
        </div>
      </div>
    `;
  }
  return `
    <div class="modal-backdrop">
      <div class="modal">
        <div class="modal-header">
          <h2>编辑学期</h2>
          <button class="btn btn-ghost" data-action="close-modal">&times;</button>
        </div>
        <div class="modal-body">
          <form class="form-grid" data-form="update-semester" data-semester-id="${current.id}">
            ${semesterFields(current, false)}
            <div class="form-actions">
              <button class="btn" type="button" data-action="close-modal">取消</button>
              <button class="btn btn-primary" type="submit">保存学期</button>
            </div>
          </form>
        </div>
      </div>
    </div>
  `;
}

function semesterFields(term, disabled) {
  const attr = disabled ? 'disabled' : '';
  return `
    <label class="field"><span>学期名称</span><input name="name" value="${text(term.name || '')}" required ${attr}></label>
    <label class="field"><span>学期开始</span><input name="startDate" type="date" value="${text(term.startDate || '')}" required ${attr}></label>
    <label class="field"><span>学期结束</span><input name="endDate" type="date" value="${text(term.endDate || '')}" required ${attr}></label>
    <label class="field"><span>最大学分</span><input name="maxCredit" type="number" min="1" step="0.5" value="${text(term.maxCredit ?? 30)}" required ${attr}></label>
  `;
}

function renderCourseCatalog() {
  const courses = state.routeData.adminCourses || [];
  const totalPages = Math.max(1, Math.ceil(courses.length / state.adminCoursePageSize));
  if (state.adminCoursePage > totalPages) state.adminCoursePage = totalPages;
  const start = (state.adminCoursePage - 1) * state.adminCoursePageSize;
  const pageItems = courses.slice(start, start + state.adminCoursePageSize);
  return `
    <section class="panel">
      <div class="panel-header">
        <div><h2>课程管理</h2><p>维护课程基础信息；历史学期课程班不随课程弃用而变更。</p></div>
        <div class="panel-actions" style="gap:8px">
          <form data-form="course-catalog-search" style="display:flex;gap:8px"><input name="keyword" value="${text(state.filters.adminCourseKeyword)}" placeholder="课程号/课程名/院系"><button class="btn">查询</button></form>
          <button class="btn btn-primary" data-action="open-course-modal">添加课程</button>
        </div>
      </div>
      ${courseCatalogTable(pageItems)}
      ${renderPagination(totalPages, state.adminCoursePage, 'course-catalog-page')}
    </section>
  `;
}

function courseCatalogTable(rows) {
  if (!rows.length) return empty('暂无课程');
  return `
    <div class="table-wrap">
      <table>
        <thead><tr><th class="col-code">课程号</th><th class="col-course">课程名</th><th class="col-dept">开课院系</th><th class="col-credit">学分</th><th class="col-status">课程状态</th><th class="col-actions">操作</th></tr></thead>
        <tbody>${rows.map((row) => `
          <tr>
            <td><b>${text(row.code)}</b></td>
            <td>${text(row.name)}</td>
            <td>${text(row.departmentName)}</td>
            <td>${number(row.credit)}</td>
            <td>${courseBadge(row.status)}</td>
            <td><div class="row-actions">${row.status === 'enabled'
              ? `<button class="btn btn-danger" data-action="course-disable" data-id="${row.id}">弃用</button>`
              : `<button class="btn btn-primary" data-action="course-enable" data-id="${row.id}">启用</button>`}</div></td>
          </tr>
        `).join('')}</tbody>
      </table>
    </div>
  `;
}

function userStatusAction(type, row) {
  const id = type === 'student' ? row.studentId : row.teacherId;
  if (row.status === 'enabled') {
    return `<button class="btn btn-danger" data-action="${type}-disable" data-id="${id}">弃用</button>`;
  }
  return `<button class="btn btn-primary" data-action="${type}-enable" data-id="${id}">启用</button>`;
}

function ensureAdminSemesterSelection(key) {
  const semesters = state.adminCatalog?.semesters || [];
  if (!semesters.length) {
    state.selected[key] = null;
    return '';
  }
  const currentValue = state.selected[key];
  const exists = semesters.some((semester) => String(semester.id) === String(currentValue));
  if (!exists) {
    const currentSemester = state.adminCatalog?.currentSemester;
    state.selected[key] = currentSemester?.id || semesters[0].id;
  }
  return String(state.selected[key] || '');
}

function selectedAdminSemester(key) {
  const selectedId = ensureAdminSemesterSelection(key);
  return (state.adminCatalog?.semesters || []).find((semester) => String(semester.id) === selectedId);
}

function semesterSelectOptions(selectedId) {
  const semesters = state.adminCatalog?.semesters || [];
  return semesters.map((semester) => (
    `<option value="${text(semester.id)}" ${String(semester.id) === String(selectedId) ? 'selected' : ''}>${text(semester.name)}</option>`
  )).join('');
}

function ensureCatalogSemesterSelection(key) {
  const semesters = state.catalog?.semesters || [];
  if (!semesters.length) {
    state.selected[key] = null;
    return '';
  }
  const currentValue = state.selected[key];
  const exists = semesters.some((semester) => String(semester.id) === String(currentValue));
  if (!exists) {
    const currentSemester = state.catalog?.currentSemester;
    state.selected[key] = currentSemester?.id || semesters[0].id;
  }
  return String(state.selected[key] || '');
}

function selectedCatalogSemester(key) {
  const selectedId = ensureCatalogSemesterSelection(key);
  return (state.catalog?.semesters || []).find((semester) => String(semester.id) === selectedId);
}

function catalogSemesterSelectOptions(selectedId) {
  const semesters = state.catalog?.semesters || [];
  return semesters.map((semester) => (
    `<option value="${text(semester.id)}" ${String(semester.id) === String(selectedId) ? 'selected' : ''}>${text(semester.name)}</option>`
  )).join('');
}

function renderCourseManage() {
  const offerings = state.routeData.offerings || [];
  const selectedSemester = selectedAdminSemester('offeringSemesterId');
  const selectedSemesterId = selectedSemester?.id || '';
  const totalPages = Math.max(1, Math.ceil(offerings.length / state.offeringPageSize));
  if (state.offeringPage > totalPages) state.offeringPage = totalPages;
  const start = (state.offeringPage - 1) * state.offeringPageSize;
  const pageItems = offerings.slice(start, start + state.offeringPageSize);
  return `
    <section class="panel">
      <div class="panel-header">
        <div>
          <div class="panel-title-row">
            <h2>课程班管理</h2>
            <select class="semester-select" data-action="offering-semester-select" ${selectedSemesterId ? '' : 'disabled'}>
              ${semesterSelectOptions(selectedSemesterId)}
            </select>
          </div>
          <p>按所选学期维护课程班；添加课程班也会写入该学期。</p>
        </div>
        <div class="panel-actions" style="gap:8px">
          <form data-form="offering-search" style="display:flex;gap:8px"><input name="keyword" value="${text(state.filters.offeringKeyword)}" placeholder="课程/教师/教室"><button class="btn">查询</button></form>
          <button class="btn btn-primary" data-action="open-offering-modal" ${selectedSemesterId ? '' : 'disabled'}>添加课程班</button>
        </div>
      </div>
      ${offeringTable(pageItems, true)}
      ${renderPagination(totalPages, state.offeringPage, 'offering-page')}
    </section>
  `;
}

function offeringTable(rows, adminActions = false) {
  if (!rows.length) return empty('暂无课程班');
  return `
    <div class="table-wrap">
      <table>
        <thead><tr><th class="col-course">课程</th><th class="col-name">教师</th><th class="col-time">时间</th><th class="col-capacity">容量</th><th class="col-status">状态</th>${adminActions ? '<th class="col-actions">操作</th>' : ''}</tr></thead>
        <tbody>${rows.map((row) => `
          <tr>
            <td><b>${text(row.courseCode)}</b> ${text(row.courseName)}</td>
            <td>${text(row.teacherName)}</td>
            <td>${courseTime(row)}</td>
            <td>${number(row.selectedCount)}/${number(row.capacity)}</td>
            <td>${badge(row.status)}</td>
            ${adminActions ? `<td><div class="row-actions"><button class="btn" data-action="edit-offering" data-id="${row.id}">修改</button><button class="btn" data-action="course-roster" data-id="${row.id}">选课名单</button><button class="btn btn-danger" data-action="offering-delete" data-id="${row.id}">删除</button></div></td>` : ''}
          </tr>
        `).join('')}</tbody>
      </table>
    </div>
  `;
}

function renderPagination(totalPages, currentPage, actionName) {
  if (totalPages <= 1) return '';
  const pages = [];
  for (let i = 1; i <= totalPages; i++) {
    pages.push(i);
  }
  return `
    <div class="pagination">
      <button class="btn btn-sm" data-action="${actionName}" data-page="${currentPage - 1}" ${currentPage <= 1 ? 'disabled' : ''}>上一页</button>
      ${pages.map((p) => `
        <button class="btn btn-sm ${p === currentPage ? 'btn-primary' : ''}" data-action="${actionName}" data-page="${p}">${p}</button>
      `).join('')}
      <button class="btn btn-sm" data-action="${actionName}" data-page="${currentPage + 1}" ${currentPage >= totalPages ? 'disabled' : ''}>下一页</button>
    </div>
  `;
}

function pageSummary(data) {
  return `第 ${number(data.page || 1)} / ${number(data.totalPages || 1)} 页，共 ${number(data.total || 0)} 条`;
}

function shortId(value) {
  const raw = String(value || '');
  if (!raw) return '-';
  return raw.length > 12 ? `${text(raw.slice(0, 8))}...` : text(raw);
}

function formatBytes(value) {
  const bytes = Number(value || 0);
  if (!Number.isFinite(bytes) || bytes <= 0) return '-';
  const units = ['B', 'KB', 'MB', 'GB'];
  let size = bytes;
  let index = 0;
  while (size >= 1024 && index < units.length - 1) {
    size /= 1024;
    index += 1;
  }
  return `${size >= 10 || index === 0 ? size.toFixed(0) : size.toFixed(1)} ${units[index]}`;
}

function renderAuditLogs() {
  const data = state.routeData.auditLogs || { rows: [], page: 1, totalPages: 1, total: 0 };
  state.logPage = Number(data.page || 1);
  const rows = data.rows || [];
  return `
    <section class="panel">
      <div class="panel-header">
        <div><h2>事务日志</h2><p>${pageSummary(data)}，每页最多 10 条。</p></div>
        <button class="btn" data-action="reload-audit-logs">刷新</button>
      </div>
      ${auditLogTable(rows)}
      ${renderPagination(Number(data.totalPages || 1), state.logPage, 'log-page')}
    </section>
  `;
}

function auditLogTable(rows) {
  if (!rows.length) return empty('暂无事务日志');
  return `
    <div class="table-wrap">
      <table class="audit-log-table">
        <thead><tr><th class="col-name">操作人</th><th class="col-date">时间</th><th class="col-dept">对象</th><th class="col-course">动作</th><th class="col-status">结果</th></tr></thead>
        <tbody>${rows.map((row) => `
          <tr>
            <td>${text(row.actorName || row.actorUserId || '-')}</td>
            <td>${text(row.createdAt)}</td>
            <td>${text(auditObject(row))}</td>
            <td>${text(businessTypeText[row.businessType] || operationText[row.operation] || row.businessType || row.operation || '-')}</td>
            <td title="${text(row.message || '')}">${badge(row.status)}</td>
          </tr>
        `).join('')}</tbody>
      </table>
    </div>
  `;
}

function auditObject(row) {
  const tableName = row.tableName || '';
  if (!tableName && row.targetMessage) {
    return row.targetMessage;
  }
  const label = tableText[tableName] || tableName || '业务事务';
  return row.recordId ? `${label} #${row.recordId}` : label;
}

function renderBackupManage() {
  const data = state.routeData.backups || { rows: [], page: 1, totalPages: 1, total: 0 };
  state.backupPage = Number(data.page || 1);
  const rows = data.rows || [];
  return `
    <section class="panel">
      <div class="panel-header">
        <div><h2>逻辑备份</h2><p>${pageSummary(data)}；系统每天 02:00 调用备份脚本，并保留最近 10 份备份文件。</p></div>
        <div class="panel-actions">
          <button class="btn" data-action="reload-backups">刷新</button>
          <button class="btn btn-primary" data-action="run-backup">立即备份</button>
        </div>
      </div>
      ${backupTable(rows)}
      ${renderPagination(Number(data.totalPages || 1), state.backupPage, 'backup-page')}
    </section>
  `;
}

function backupTable(rows) {
  if (!rows.length) return empty('暂无备份记录');
  return `
    <div class="table-wrap">
      <table class="backup-table">
        <thead><tr><th class="col-date">开始时间</th><th class="col-date">结束时间</th><th class="col-code">数据库</th><th class="col-course">备份文件</th><th class="col-capacity">大小</th><th class="col-status">状态</th><th class="col-name">触发方式</th><th class="col-message">消息</th></tr></thead>
        <tbody>${rows.map((row) => `
          <tr>
            <td>${text(row.startedAt)}</td>
            <td>${text(row.endedAt || '-')}</td>
            <td>${text(row.databaseName)}</td>
            <td title="${text(`${row.backupDirectory || ''}/${row.fileName || ''}`)}">${text(row.fileName)}</td>
            <td>${formatBytes(row.fileSizeBytes)}</td>
            <td>${badge(row.status)}</td>
            <td>${text(row.triggerType === 'manual' ? '手动' : '定时')}</td>
            <td title="${text(row.message || '')}">${text(row.message || '-')}</td>
          </tr>
        `).join('')}</tbody>
      </table>
    </div>
  `;
}

function renderCourseModal() {
  const catalog = state.adminCatalog || {};
  return `
    <div class="modal-backdrop">
      <div class="modal">
        <div class="modal-header">
          <h2>添加课程</h2>
          <button class="btn btn-ghost" data-action="close-course-modal">&times;</button>
        </div>
        <div class="modal-body">
          <form class="form-grid" data-form="create-course-modal">
            <label class="field"><span>课程号</span><input name="code" required></label>
            <label class="field"><span>课程名</span><input name="name" required></label>
            <label class="field"><span>开课院系</span><select name="departmentId" required>${options(catalog.departments, 'id', (item) => item.name)}</select></label>
            <label class="field"><span>学分</span><input name="credit" type="number" min="0.5" max="20" step="0.5" required></label>
            <div class="form-actions"><button class="btn btn-primary" type="submit">添加课程</button></div>
          </form>
        </div>
      </div>
    </div>
  `;
}

function renderOfferingModal() {
  const catalog = state.adminCatalog || {};
  const selectedSemester = selectedAdminSemester('offeringSemesterId');
  const availableCourses = (catalog.courses || []).filter((course) => course.status !== 'disabled');
  return `
    <div class="modal-backdrop">
      <div class="modal">
        <div class="modal-header">
          <h2>添加课程班</h2>
          <button class="btn btn-ghost" data-action="close-offering-modal">&times;</button>
        </div>
        <div class="modal-body">
          <form class="form-grid" data-form="create-offering-modal">
            <label class="field"><span>课程</span><select name="courseId" required>${options(availableCourses, 'id', (item) => `${item.code} ${item.name}`)}</select></label>
            <input type="hidden" name="semesterId" value="${text(selectedSemester?.id || '')}">
            <label class="field"><span>学期</span><input value="${text(selectedSemester?.name || '暂无可用学期')}" disabled></label>
            <label class="field"><span>教师</span><select name="teacherId" required>${options(catalog.teachers, 'id', (item) => `${item.teacherNo} ${item.name}`)}</select></label>
            ${offeringTimesEditor()}
            <label class="field"><span>容量</span><input name="capacity" type="number" min="1" required></label>
            <label class="field"><span>期末占比(%)</span><input name="examRatio" type="number" min="0" max="100" step="1" placeholder="60"></label>
            <div class="form-actions"><button class="btn btn-primary" type="submit" ${selectedSemester && availableCourses.length ? '' : 'disabled'}>添加课程班</button></div>
          </form>
        </div>
      </div>
    </div>
  `;
}

function renderEditOfferingModal(offeringId) {
  const catalog = state.adminCatalog || {};
  const offerings = state.routeData.offerings || [];
  const offering = offerings.find((o) => String(o.id) === String(offeringId));
  if (!offering) return '';
  const examPct = offering.examRatio != null ? Math.round(Number(offering.examRatio) * 100) : '';
  const minCapacity = Math.max(1, Number(offering.selectedCount || 0));
  return `
    <div class="modal-backdrop">
      <div class="modal">
        <div class="modal-header">
          <h2>修改课程班</h2>
          <button class="btn btn-ghost" data-action="close-offering-modal">&times;</button>
        </div>
        <div class="modal-body">
          <form class="form-grid" data-form="edit-offering-modal" data-offering-id="${offeringId}">
            <input type="hidden" name="courseId" value="${text(offering.courseId)}">
            <input type="hidden" name="semesterId" value="${text(offering.semesterId)}">
            <input type="hidden" name="status" value="${text(offering.status || 'selecting')}">
            <label class="field"><span>课程</span><input value="${text(offering.courseCode)} ${text(offering.courseName)}" disabled></label>
            <label class="field"><span>学期</span><input value="${text(offering.semesterName || '')}" disabled></label>
            <label class="field"><span>教师</span><select name="teacherId" required>${options(catalog.teachers, 'id', (item) => `${item.teacherNo} ${item.name}`, offering.teacherId)}</select></label>
            ${offeringTimesEditor(offeringTimes(offering))}
            <label class="field"><span>容量</span><input name="capacity" type="number" min="${minCapacity}" value="${text(offering.capacity)}" required></label>
            <label class="field"><span>期末占比(%)</span><input name="examRatio" type="number" min="0" max="100" step="1" value="${examPct}" placeholder="60"></label>
            <div class="form-actions"><button class="btn btn-primary" type="submit">保存修改</button></div>
          </form>
        </div>
      </div>
    </div>
  `;
}

function renderStudentModal() {
  const catalog = state.adminCatalog || {};
  return `
    <div class="modal-backdrop">
      <div class="modal">
        <div class="modal-header">
          <h2>添加学生</h2>
          <button class="btn btn-ghost" data-action="close-modal">&times;</button>
        </div>
        <div class="modal-body">
          <form class="form-grid" data-form="create-student-modal">
            <label class="field"><span>学号</span><input name="studentNo" required></label>
            <label class="field"><span>姓名</span><input name="name" required></label>
            <label class="field"><span>邮箱</span><input name="email" type="email" required></label>
            <label class="field"><span>专业</span><select name="majorId" required>${options(catalog.majors, 'id', (item) => `${item.departmentName} · ${item.name}`)}</select></label>
            <label class="field"><span>入学年份</span><input name="admissionYear" type="number" min="2000" max="2099" required></label>
            <div class="form-actions"><button class="btn btn-primary" type="submit">添加学生</button></div>
          </form>
        </div>
      </div>
    </div>
  `;
}

function renderEditStudentModal(studentId) {
  const catalog = state.adminCatalog || {};
  const students = state.routeData.students || [];
  const student = students.find((s) => String(s.studentId) === String(studentId));
  if (!student) return '';
  return `
    <div class="modal-backdrop">
      <div class="modal">
        <div class="modal-header">
          <h2>修改学生</h2>
          <button class="btn btn-ghost" data-action="close-modal">&times;</button>
        </div>
        <div class="modal-body">
          <form class="form-grid" data-form="edit-student-modal" data-student-id="${studentId}">
            <input type="hidden" name="studentNo" value="${text(student.studentNo)}">
            <label class="field"><span>学号</span><input value="${text(student.studentNo)}" disabled></label>
            <label class="field"><span>姓名</span><input name="name" value="${text(student.name)}" required></label>
            <label class="field"><span>邮箱</span><input name="email" type="email" value="${text(student.email)}" required></label>
            <label class="field"><span>专业</span><select name="majorId" required>${options(catalog.majors, 'id', (item) => `${item.departmentName} · ${item.name}`, student.majorId)}</select></label>
            <label class="field"><span>入学年份</span><input name="admissionYear" type="number" min="2000" max="2099" value="${text(student.admissionYear)}" required></label>
            <div class="form-actions"><button class="btn btn-primary" type="submit">保存修改</button></div>
          </form>
        </div>
      </div>
    </div>
  `;
}

function renderTeacherModal() {
  const catalog = state.adminCatalog || {};
  return `
    <div class="modal-backdrop">
      <div class="modal">
        <div class="modal-header">
          <h2>添加教师</h2>
          <button class="btn btn-ghost" data-action="close-modal">&times;</button>
        </div>
        <div class="modal-body">
          <form class="form-grid" data-form="create-teacher-modal">
            <label class="field"><span>工号</span><input name="teacherNo" required></label>
            <label class="field"><span>姓名</span><input name="name" required></label>
            <label class="field"><span>邮箱</span><input name="email" type="email" required></label>
            <label class="field"><span>院系</span><select name="departmentId" required>${options(catalog.departments, 'id', (item) => item.name)}</select></label>
            <label class="field"><span>职称</span><select name="title"><option value="教授">教授</option><option value="副教授">副教授</option><option value="讲师">讲师</option><option value="助教">助教</option></select></label>
            <div class="form-actions"><button class="btn btn-primary" type="submit">添加教师</button></div>
          </form>
        </div>
      </div>
    </div>
  `;
}

function renderEditTeacherModal(teacherId) {
  const catalog = state.adminCatalog || {};
  const teachers = state.routeData.teachers || [];
  const teacher = teachers.find((t) => String(t.teacherId) === String(teacherId));
  if (!teacher) return '';
  return `
    <div class="modal-backdrop">
      <div class="modal">
        <div class="modal-header">
          <h2>修改教师</h2>
          <button class="btn btn-ghost" data-action="close-modal">&times;</button>
        </div>
        <div class="modal-body">
          <form class="form-grid" data-form="edit-teacher-modal" data-teacher-id="${teacherId}">
            <input type="hidden" name="teacherNo" value="${text(teacher.teacherNo)}">
            <label class="field"><span>工号</span><input value="${text(teacher.teacherNo)}" disabled></label>
            <label class="field"><span>姓名</span><input name="name" value="${text(teacher.name)}" required></label>
            <label class="field"><span>邮箱</span><input name="email" type="email" value="${text(teacher.email)}" required></label>
            <label class="field"><span>院系</span><select name="departmentId" required>${options(catalog.departments, 'id', (item) => item.name, teacher.departmentId)}</select></label>
            <label class="field"><span>职称</span><select name="title">${['教授','副教授','讲师','助教'].map((t) => `<option value="${t}" ${teacher.title === t ? 'selected' : ''}>${t}</option>`).join('')}</select></label>
            <div class="form-actions"><button class="btn btn-primary" type="submit">保存修改</button></div>
          </form>
        </div>
      </div>
    </div>
  `;
}

function renderCourseRosterModal(offeringId) {
  return state.routeData.rosterData ? `
    <div class="modal-backdrop">
      <div class="modal roster-modal">
        <div class="modal-header">
          <h2>选课名单</h2>
          <button class="btn btn-ghost" data-action="close-modal">&times;</button>
        </div>
        <div class="modal-body">
          <div class="table-wrap"><table>
            <thead><tr><th class="col-id">学号</th><th class="col-name">姓名</th><th class="col-major">专业</th><th class="col-email">邮箱</th><th class="col-actions">操作</th></tr></thead>
            <tbody>${state.routeData.rosterData.map((row) => `
              <tr>
                <td>${text(row.studentNo)}</td>
                <td>${text(row.studentName)}</td>
                <td>${text(row.majorName)}</td>
                <td>${text(row.email)}</td>
                <td><button class="btn btn-danger" data-action="admin-roster-drop" data-id="${row.enrollmentId}" data-offering-id="${offeringId}">删除</button></td>
              </tr>
            `).join('')}</tbody>
          </table></div>
        </div>
      </div>
    </div>
  ` : '';
}

function renderTeacherOfferingsModal(teacherId) {
  const data = state.routeData.teacherCourseData || [];
  const selectedSemester = selectedAdminSemester('teacherOfferingSemesterId');
  const selectedSemesterId = selectedSemester?.id || '';
  return `
    <div class="modal-backdrop">
      <div class="modal">
        <div class="modal-header">
          <div class="modal-title-row">
            <h2>授课详情</h2>
            <select class="semester-select" data-action="teacher-offering-semester-select" data-teacher-id="${teacherId}" ${selectedSemesterId ? '' : 'disabled'}>
              ${semesterSelectOptions(selectedSemesterId)}
            </select>
          </div>
          <button class="btn btn-ghost" data-action="close-modal">&times;</button>
        </div>
        <div class="modal-body">
          ${data.length ? `
            <div class="table-wrap"><table>
              <thead><tr><th class="col-course">课程</th><th class="col-time">时间</th><th class="col-capacity">容量</th><th class="col-ratio">期末占比</th></tr></thead>
              <tbody>${data.map((row) => `
                <tr>
                  <td><b>${text(row.courseCode)}</b> ${text(row.courseName)}</td>
                  <td>${courseTime(row)}</td>
                  <td>${number(row.selectedCount)}/${number(row.capacity)}</td>
                  <td>${number(Math.round(Number(row.examRatio) * 100))}%</td>
                </tr>
              `).join('')}</tbody>
            </table></div>
          ` : empty('该教师该学期暂无授课')}
        </div>
      </div>
    </div>
  `;
}

function renderStudentEnrollmentsModal(studentId) {
  const data = state.routeData.studentCourseData || [];
  const selectedSemester = selectedAdminSemester('studentEnrollmentSemesterId');
  const selectedSemesterId = selectedSemester?.id || '';
  return `
    <div class="modal-backdrop">
      <div class="modal">
        <div class="modal-header">
          <div class="modal-title-row">
            <h2>选课详情</h2>
            <select class="semester-select" data-action="student-enrollment-semester-select" data-student-id="${studentId}" ${selectedSemesterId ? '' : 'disabled'}>
              ${semesterSelectOptions(selectedSemesterId)}
            </select>
          </div>
          <button class="btn btn-ghost" data-action="close-modal">&times;</button>
        </div>
        <div class="modal-body">
          ${data.length ? `
            <div class="table-wrap"><table>
              <thead><tr><th class="col-course">课程</th><th class="col-name">教师</th><th class="col-time">时间</th><th class="col-capacity">容量</th><th class="col-ratio">期末占比</th></tr></thead>
              <tbody>${data.map((row) => `
                <tr>
                  <td><b>${text(row.courseCode)}</b> ${text(row.courseName)}</td>
                  <td>${text(row.teacherName)}</td>
                  <td>${courseTime(row)}</td>
                  <td>${number(row.selectedCount)}/${number(row.capacity)}</td>
                  <td>${number(Math.round(Number(row.examRatio) * 100))}%</td>
                </tr>
              `).join('')}</tbody>
            </table></div>
          ` : empty('该学生该学期暂无选课')}
        </div>
      </div>
    </div>
  `;
}

function renderEnrollmentReport() {
  const rows = state.routeData.enrollmentReport || [];
  const roster = state.routeData.adminRoster || [];
  return `
    <div class="grid-2">
      <section class="panel">
        <div class="panel-header"><div><h2>选课统计</h2><p>点击课程班查看真实选课名单。</p></div></div>
        ${rows.length ? `
          <div class="table-wrap"><table>
            <thead><tr><th class="col-course">课程</th><th class="col-name">教师</th><th class="col-capacity">人数</th><th class="col-progress">满班率</th><th class="col-actions">名单</th></tr></thead>
            <tbody>${rows.map((row) => `
              <tr>
                <td>${text(row.courseCode)} ${text(row.courseName)}</td>
                <td>${text(row.teacherName)}</td>
                <td>${number(row.selectedCount)}/${number(row.capacity)}</td>
                <td><div class="progress"><span style="width:${Math.min(100, Number(row.fillRate || 0))}%"></span></div></td>
                <td><button class="btn" data-action="admin-roster" data-id="${row.offeringId}">查看</button></td>
              </tr>
            `).join('')}</tbody>
          </table></div>
        ` : empty('暂无统计')}
      </section>
      <section class="panel">
        <div class="panel-header"><div><h2>课程名单</h2><p>来自选课记录和成绩表。</p></div></div>
        ${rosterTable(roster)}
      </section>
    </div>
  `;
}

function renderStudentTeaching() {
  const data = state.routeData.studentTeaching;
  return `
    <div class="page-grid">
      <section class="panel">
        <div class="panel-header"><div><h2>学生修课查询</h2><p>查询学生所有真实选课记录及已归档学期成绩。</p></div></div>
        <form class="filter-grid compact" data-form="student-teaching-search">
          <label class="field"><span>学号或账号</span><input name="studentNo" value="${text(state.filters.studentNo)}" required></label>
          <button class="btn btn-primary" type="submit">查询</button>
        </form>
      </section>
      ${data ? `
        <section class="grid-2">
          <div class="panel"><div class="panel-header"><div><h2>修课记录</h2></div></div>${studentEnrollmentTable(data.enrollments || [])}</div>
          <div class="panel"><div class="panel-header"><div><h2>历史成绩</h2></div></div>${gradeTable(data.transcript || [])}</div>
        </section>
      ` : ''}
    </div>
  `;
}

function studentEnrollmentTable(rows) {
  if (!rows.length) return empty('暂无修课记录');
  return `
    <div class="table-wrap"><table>
      <thead><tr><th class="col-term">学期</th><th class="col-course">课程</th><th class="col-name">教师</th><th class="col-time">时间</th><th class="col-status">状态</th></tr></thead>
      <tbody>${rows.map((row) => `
        <tr><td>${text(row.semesterName)}</td><td>${text(row.courseCode)} ${text(row.courseName)}</td><td>${text(row.teacherName)}</td><td>${courseTime(row)}</td><td>${badge(row.status)}</td></tr>
      `).join('')}</tbody>
    </table></div>
  `;
}

function renderStudents() {
  const rows = state.routeData.students || [];
  const totalPages = Math.max(1, Math.ceil(rows.length / state.studentPageSize));
  if (state.studentPage > totalPages) state.studentPage = totalPages;
  const start = (state.studentPage - 1) * state.studentPageSize;
  const pageItems = rows.slice(start, start + state.studentPageSize);
  return `
    <section class="panel">
      <div class="panel-header">
        <div><h2>学生管理</h2></div>
        <div class="panel-actions" style="gap:8px">
          <form data-form="student-search" style="display:flex;gap:8px"><input name="keyword" value="${text(state.filters.studentKeyword)}" placeholder="学号/姓名/学院/专业/邮箱"><button class="btn">查询</button></form>
          <button class="btn btn-primary" data-action="open-student-modal">添加学生</button>
        </div>
      </div>
      ${pageItems.length ? `
        <div class="table-wrap"><table>
          <thead><tr><th class="col-id">学号</th><th class="col-name">姓名</th><th class="col-dept">学院</th><th class="col-major">专业</th><th class="col-email">学生邮箱</th><th class="col-year">入学年份</th><th class="col-status">状态</th><th class="col-actions">操作</th></tr></thead>
          <tbody>${pageItems.map((row) => `<tr><td>${text(row.studentNo)}</td><td>${text(row.name)}</td><td>${text(row.departmentName || '-')}</td><td>${text(row.majorName || '-')}</td><td>${text(row.email || '-')}</td><td>${text(row.admissionYear)}</td><td>${badge(row.status)}</td><td><div class="row-actions"><button class="btn" data-action="edit-student" data-id="${row.studentId}">修改</button><button class="btn" data-action="student-enrollments" data-id="${row.studentId}">选课详情</button>${userStatusAction('student', row)}</div></td></tr>`).join('')}</tbody>
        </table></div>
      ` : empty('暂无学生')}
      ${renderPagination(totalPages, state.studentPage, 'student-page')}
    </section>
  `;
}

function renderTeachers() {
  const rows = state.routeData.teachers || [];
  const totalPages = Math.max(1, Math.ceil(rows.length / state.teacherPageSize));
  if (state.teacherPage > totalPages) state.teacherPage = totalPages;
  const start = (state.teacherPage - 1) * state.teacherPageSize;
  const pageItems = rows.slice(start, start + state.teacherPageSize);
  return `
    <section class="panel">
      <div class="panel-header">
        <div><h2>教师管理</h2></div>
        <div class="panel-actions" style="gap:8px">
          <form data-form="teacher-search" style="display:flex;gap:8px"><input name="keyword" value="${text(state.filters.teacherKeyword)}" placeholder="工号/姓名/院系/邮箱"><button class="btn">查询</button></form>
          <button class="btn btn-primary" data-action="open-teacher-modal">添加教师</button>
        </div>
      </div>
      ${pageItems.length ? `
        <div class="table-wrap"><table>
          <thead><tr><th class="col-id">工号</th><th class="col-name">姓名</th><th class="col-dept">院系</th><th class="col-title">职称</th><th class="col-email">教师邮箱</th><th class="col-status">状态</th><th class="col-actions">操作</th></tr></thead>
          <tbody>${pageItems.map((row) => `<tr><td>${text(row.teacherNo)}</td><td>${text(row.name)}</td><td>${text(row.departmentName)}</td><td>${text(row.title)}</td><td>${text(row.email || '-')}</td><td>${badge(row.status)}</td><td><div class="row-actions"><button class="btn" data-action="edit-teacher" data-id="${row.teacherId}">修改</button><button class="btn" data-action="teacher-offerings" data-id="${row.teacherId}">授课详情</button>${userStatusAction('teacher', row)}</div></td></tr>`).join('')}</tbody>
        </table></div>
      ` : empty('暂无教师')}
      ${renderPagination(totalPages, state.teacherPage, 'teacher-page')}
    </section>
  `;
}

function renderTeacherCourses() {
  const courses = state.routeData.teacherCourses || [];
  return `
    <section class="panel">
      <div class="panel-header"><div><h2>任课课程</h2></div></div>
      ${courses.length ? `
        <div class="table-wrap"><table>
          <thead><tr><th class="col-term">学期</th><th class="col-course">课程</th><th class="col-time">时间</th><th class="col-capacity">人数</th><th class="col-status">状态</th><th class="col-actions">操作</th></tr></thead>
          <tbody>${courses.map((row) => `
            <tr>
              <td>${text(row.semesterName)}</td>
              <td><b>${text(row.courseCode)}</b> ${text(row.courseName)}</td>
              <td>${courseTime(row)}</td>
              <td>${number(row.selectedCount)}/${number(row.capacity)}</td>
              <td>${badge(row.status)}</td>
              <td><button class="btn" data-action="teacher-roster" data-id="${row.id}">查看名单</button></td>
            </tr>
          `).join('')}</tbody>
        </table></div>
      ` : empty('暂无课程')}
    </section>
  `;
}

function renderTeacherSchedule() {
  const rows = state.routeData.teacherSchedule || [];
  const term = state.catalog?.currentSemester || {};
  return `
    <section class="panel">
      <div class="panel-header">
        <div><h2>排课安排</h2><p>${term.name ? `显示 ${text(term.name)} 的排课表。` : '显示当前学期排课表。'}</p></div>
      </div>
      ${scheduleGrid(rows, { showTeacher: false })}
    </section>
  `;
}

function renderTeacherRosterModal(offeringId) {
  const courses = state.routeData.teacherCourses || [];
  const course = courses.find((c) => String(c.id) === String(offeringId));
  const data = state.routeData.teacherRosterData || [];
  const title = course ? `${text(course.courseCode)} ${text(course.courseName)}` : '学生名单';
  return `
    <div class="modal-backdrop">
      <div class="modal">
        <div class="modal-header">
          <h2>${title}</h2>
          <button class="btn btn-ghost" data-action="close-modal">&times;</button>
        </div>
        <div class="modal-body">
          ${data.length ? `
            <div class="table-wrap"><table>
              <thead><tr><th class="col-id">学号</th><th class="col-name">姓名</th><th class="col-major">专业</th><th class="col-email">邮箱</th></tr></thead>
              <tbody>${data.map((row) => `
                <tr><td>${text(row.studentNo)}</td><td>${text(row.studentName)}</td><td>${text(row.majorName)}</td><td>${text(row.email)}</td></tr>
              `).join('')}</tbody>
            </table></div>
          ` : empty('暂无学生名单')}
        </div>
      </div>
    </div>
  `;
}

function renderStudentRoute() {
  if (state.route === 'courseSelect') return renderCourseSelect();
  if (state.route === 'selectedCourses') return renderSchedule();
  if (state.route === 'grades') return renderGrades();
  if (state.route === 'noticeManage') return renderNoticeManage();
  return empty('页面不存在');
}

function renderCourseSelect() {
  const data = state.routeData.studentOfferings || {};
  const rows = data.rows || [];
  const semester = data.semester || {};
  return `
    <div class="course-select-layout">
      <section class="panel">
        <div class="panel-header">
          <div><h2>在线选课</h2><p>管理员开放选课后才可选课；关闭后不能继续选课或退课。</p></div>
          <form class="panel-actions" data-form="course-search"><input name="keyword" value="${text(state.filters.courseKeyword)}" placeholder="课程/教师/教室"><button class="btn">查询</button></form>
        </div>
        ${rows.length ? `<div class="course-grid">${rows.map(courseCard).join('')}</div>` : empty(data.selectionOpen ? '暂无可显示课程' : '未开放选课')}
      </section>
      <aside class="panel preview-card">
        <div class="panel-header"><div><h2>选课窗口</h2></div></div>
        <div class="list-stack">
          <div class="list-item"><div><strong>${text(semester.name)}</strong><p>${text(dateRange(semester.startDate, semester.endDate))}</p></div>${badge(semester.status)}</div>
          <div class="list-item"><div><strong>当前是否可选</strong><p>${data.selectionOpen ? `${text(semester.name)}选课中，可以选课` : '未开放选课，不可选课'}</p></div></div>
          <div class="list-item"><div><strong>重修规则</strong><p>历史已通过课程不可重复选择，挂科课程可重新选择。</p></div></div>
          <div class="list-item"><div><strong>退课限制</strong><p>已被教师登分的课程不能退课。</p></div></div>
        </div>
      </aside>
    </div>
  `;
}

function courseCard(row) {
  const selected = row.enrollmentStatus === 'selected';
  const passedBefore = bool(row.passedBefore);
  const full = Number(row.selectedCount || 0) >= Number(row.capacity || 0);
  const selectionOpen = Boolean(state.routeData.studentOfferings?.selectionOpen);
  const disabled = !selectionOpen || row.status !== 'selecting' || passedBefore || full;
  const reason = selected ? '已选' : passedBefore ? '已通过' : full ? '已满' : !selectionOpen ? '未开放选课' : row.status !== 'selecting' ? '已结课' : '可选';
  return `
    <article class="course-card">
      <div>
        <h3>${text(row.courseCode)} ${text(row.courseName)}</h3>
        <div class="course-meta">
          <span>${number(row.credit)} 学分</span>
          <span>${text(row.teacherName)}</span>
          <span>${courseTime(row)}</span>
          <span>${number(row.selectedCount)}/${number(row.capacity)}</span>
          <span>${reason}</span>
        </div>
      </div>
      <div class="row-actions">
        ${selected
          ? `<button class="btn btn-danger" data-action="student-drop" data-id="${row.enrollmentId}" ${selectionOpen ? '' : 'disabled'}>退课</button>`
          : `<button class="btn btn-primary" data-action="student-select" data-id="${row.id}" ${disabled ? 'disabled' : ''}>选课</button>`}
      </div>
    </article>
  `;
}

function renderSchedule() {
  const rows = state.routeData.schedule || [];
  const selectedSemester = selectedCatalogSemester('scheduleSemesterId');
  const selectedSemesterId = selectedSemester?.id || '';
  return `
    <section class="panel">
      <div class="panel-header">
        <div>
          <div class="panel-title-row">
            <h2>我的课表</h2>
            <select class="semester-select" data-action="schedule-semester-select" ${selectedSemesterId ? '' : 'disabled'}>
              ${catalogSemesterSelectOptions(selectedSemesterId)}
            </select>
          </div>
          <p>按所选学期查看已选课程课表。</p>
        </div>
      </div>
      ${scheduleGrid(rows)}
    </section>
  `;
}

function scheduleGrid(rows, options = {}) {
  const slots = [[1,2], [3,4], [5,6], [7,8], [9,10], [11,12]];
  const entries = rows.flatMap((row) => offeringTimes(row).map((time) => ({ ...row, ...time })));
  const cells = ['<div class="schedule-head">节次</div>', ...dayNames.slice(1).map((day) => `<div class="schedule-head">${day}</div>`)];
  slots.forEach(([start, end]) => {
    cells.push(`<div class="schedule-slot">${start}-${end}节</div>`);
    for (let day = 1; day <= 7; day += 1) {
      const courses = entries.filter((row) => Number(row.dayOfWeek) === day && Number(row.startSection) <= start && Number(row.endSection) >= end);
      cells.push(courses.length ? `<div class="schedule-course">${courses.map((course) => {
        const teacher = options.showTeacher === false ? '' : ` · ${text(course.teacherName)}`;
        return `<strong>${text(course.courseName)}</strong>${text(course.startWeek || 1)}-${text(course.endWeek || 16)}周 · ${text(weekTypeText[course.weekType] || '全部')}${teacher}<br>${text(course.classroom)}`;
      }).join('<hr>')}</div>` : '<div></div>');
    }
  });
  return `<div class="schedule"><div class="schedule-grid">${cells.join('')}</div></div>`;
}

function renderGrades() {
  const allRows = state.routeData.transcript?.rows || [];
  const semesters = uniqueSemesters(allRows);
  const selected = state.selected.transcriptSemesterId;
  const rows = selected === 'all' ? allRows : allRows.filter((row) => String(row.semesterId) === String(selected));
  return `
    <div class="page-grid">
      <section class="panel">
        <div class="panel-header"><div><h2>成绩总表</h2><p>按学期分类查看所有已录入最终成绩的课程。</p></div></div>
        <div class="tabs">
          <button class="tab ${selected === 'all' ? 'active' : ''}" data-action="transcript-semester" data-id="all">全部学期</button>
          ${semesters.map((term) => `<button class="tab ${String(selected) === String(term.id) ? 'active' : ''}" data-action="transcript-semester" data-id="${term.id}">${text(term.name)}</button>`).join('')}
        </div>
        ${gradeTable(rows)}
      </section>
      <section class="panel">
        <div class="panel-header"><div><h2>平均绩点走势</h2><p>按学期学分加权计算。</p></div></div>
        ${gpaTrend(allRows)}
      </section>
    </div>
  `;
}

function uniqueSemesters(rows) {
  const map = new Map();
  rows.forEach((row) => {
    if (!map.has(row.semesterId)) map.set(row.semesterId, { id: row.semesterId, name: row.semesterName });
  });
  return Array.from(map.values());
}

function gradeTable(rows) {
  if (!rows.length) return empty('暂无成绩');
  return `
    <div class="table-wrap"><table>
      <thead><tr><th class="col-term">学期</th><th class="col-course">课程</th><th class="col-credit">学分</th><th class="col-score">平时分</th><th class="col-score">考试分</th><th class="col-score">最终分</th><th class="col-gpa">绩点</th></tr></thead>
      <tbody>${rows.map((row) => `<tr><td>${text(row.semesterName)}</td><td>${text(row.courseCode)} ${text(row.courseName)}</td><td>${number(row.credit)}</td><td>${text(row.usualScore)}</td><td>${text(row.examScore)}</td><td>${finalScoreText(row.finalScore)}</td><td>${text(row.gradePoint)}</td></tr>`).join('')}</tbody>
    </table></div>
  `;
}

function gpaTrend(rows) {
  if (!rows.length) return empty('暂无绩点数据');
  const map = new Map();
  rows.forEach((row, index) => {
    const key = row.semesterId;
    const credit = Number(row.credit || 0);
    const gp = Number(row.gradePoint || 0);
    if (!map.has(key)) map.set(key, { name: row.semesterName, credits: 0, points: 0, order: Number(row.semesterId || 0) || -index });
    const item = map.get(key);
    item.credits += credit;
    item.points += credit * gp;
  });
  const trend = Array.from(map.values()).sort((a, b) => b.order - a.order).map((item) => ({
    name: item.name,
    gpa: Math.round((item.credits ? item.points / item.credits : 0) * 100) / 100
  }));
  const width = 760;
  const height = 240;
  const left = 62;
  const right = 54;
  const top = 24;
  const bottom = 50;
  const plotWidth = width - left - right;
  const plotHeight = height - top - bottom;
  const effectiveWidth = trend.length <= 1 ? 0 : Math.min(plotWidth, 170 * (trend.length - 1));
  const startX = left + (plotWidth - effectiveWidth) / 2;
  const points = trend.map((item, index) => {
    const x = trend.length === 1 ? left + plotWidth / 2 : startX + (effectiveWidth / (trend.length - 1)) * index;
    const y = top + (1 - Math.max(0, Math.min(4, item.gpa)) / 4) * plotHeight;
    return { ...item, x: Math.round(x * 10) / 10, y: Math.round(y * 10) / 10 };
  });
  const pointString = points.map((item) => `${item.x},${item.y}`).join(' ');
  const areaString = points.length ? `${points[0].x},${top + plotHeight} ${pointString} ${points[points.length - 1].x},${top + plotHeight}` : '';
  const ticks = [4, 3, 2, 1, 0];
  return `
    <div class="gpa-line-chart">
      <svg viewBox="0 0 ${width} ${height}" role="img" aria-label="平均绩点折线图" preserveAspectRatio="xMidYMid meet">
        ${ticks.map((tick) => {
          const y = top + (1 - tick / 4) * plotHeight;
          return `
            <g class="gpa-grid-line">
              <line x1="${left}" y1="${y}" x2="${left + plotWidth}" y2="${y}"></line>
              <text x="${left - 12}" y="${y + 4}">${tick.toFixed(1)}</text>
            </g>
          `;
        }).join('')}
        <line class="gpa-axis" x1="${left}" y1="${top}" x2="${left}" y2="${top + plotHeight}"></line>
        <line class="gpa-axis" x1="${left}" y1="${top + plotHeight}" x2="${left + plotWidth}" y2="${top + plotHeight}"></line>
        ${points.length > 1 ? `<polygon class="gpa-area" points="${areaString}"></polygon>` : ''}
        <polyline class="gpa-line" points="${pointString}"></polyline>
        ${points.map((item, index) => `
          <g class="gpa-point">
            <line class="gpa-point-drop" x1="${item.x}" y1="${item.y}" x2="${item.x}" y2="${top + plotHeight}"></line>
            <circle cx="${item.x}" cy="${item.y}" r="5"></circle>
            <text x="${item.x}" y="${item.y - 10}" text-anchor="middle">${item.gpa.toFixed(2)}</text>
            <text class="gpa-x-index" x="${item.x}" y="${top + plotHeight + 28}" text-anchor="middle">${index + 1}</text>
          </g>
        `).join('')}
      </svg>
      <div class="gpa-line-legend">
        ${points.map((item, index) => `
          <div><i>${index + 1}</i><span>${text(item.name)}</span><strong>${item.gpa.toFixed(2)}</strong></div>
        `).join('')}
      </div>
    </div>
  `;
}

function rosterTable(rows) {
  if (!rows.length) return empty('暂无名单');
  return `
    <div class="table-wrap"><table>
      <thead><tr><th class="col-id">学号</th><th class="col-name">姓名</th><th class="col-major">专业</th><th class="col-email">邮箱</th><th class="col-score">平时分</th><th class="col-score">考试分</th><th class="col-score">最终分</th><th class="col-gpa">绩点</th></tr></thead>
      <tbody>${rows.map((row) => `<tr><td>${text(row.studentNo)}</td><td>${text(row.studentName)}</td><td>${text(row.majorName)}</td><td>${text(row.email)}</td><td>${text(row.usualScore)}</td><td>${text(row.examScore)}</td><td>${finalScoreText(row.finalScore)}</td><td>${text(row.gradePoint)}</td></tr>`).join('')}</tbody>
    </table></div>
  `;
}

function options(rows = [], valueKey, label, selectedValue) {
  return rows.map((row) => {
    const val = text(row[valueKey]);
    const sel = selectedValue !== undefined && String(selectedValue) === String(row[valueKey]) ? 'selected' : '';
    return `<option value="${val}" ${sel}>${text(label(row))}</option>`;
  }).join('');
}

function empty(message) {
  return `<div class="empty-state">${text(message)}</div>`;
}

async function refresh(message) {
  await loadRoute(true);
  renderShell();
  if (message) toast(message);
}

document.addEventListener('click', async (event) => {
  const target = event.target.closest('[data-action]');
  if (!target) return;
  const action = target.dataset.action;
  try {
    // 点击遮罩层（灰色背景）关闭弹窗
    if (event.target.classList.contains('modal-backdrop')) {
      state.modal = '';
      renderShell();
      return;
    }
    if (action === 'login-required') {
      toast(target.dataset.message || '请登录后查看', 'warn');
      return;
    }
    if (action === 'navigate') {
      state.route = target.dataset.route;
      localStorage.setItem('course-ui-route', state.route);
      await loadRoute();
      renderShell();
    } else if (action === 'logout') {
      if (state.token) await api('/api/auth/logout', { method: 'POST' }).catch(() => null);
      clearSession();
      await loadLanding();
      renderLogin();
    } else if (action === 'notice') {
      state.route = 'noticeManage';
      localStorage.setItem('course-ui-route', state.route);
      await loadRoute();
      renderShell();
    } else if (action === 'open-notice-modal') {
      state.modal = renderNoticeEditorModal();
      renderShell();
    } else if (action === 'edit-notice') {
      state.modal = renderNoticeEditorModal(target.dataset.id);
      renderShell();
    } else if (action === 'delete-notice') {
      if (!confirm('确定要删除这条通知吗？')) return;
      await api(`/api/admin/notices/${target.dataset.id}`, { method: 'DELETE' });
      await refresh('通知已删除');
    } else if (action === 'course-disable') {
      if (!confirm('确定要弃用该课程吗？弃用后不能再新建该课程的课程班，历史课程班不受影响。')) return;
      await api(`/api/admin/courses/${target.dataset.id}/disable`, { method: 'POST' });
      await refresh('课程已弃用');
    } else if (action === 'course-enable') {
      await api(`/api/admin/courses/${target.dataset.id}/enable`, { method: 'POST' });
      await refresh('课程已启用');
    } else if (action === 'offering-delete') {
      if (!confirm('确定要删除该课程班吗？无成绩且学期未结束时，系统会标记课程班为已删除；已有选课会统一退选。')) return;
      await api(`/api/admin/offerings/${target.dataset.id}`, { method: 'DELETE' });
      await refresh('课程班已删除');
    } else if (action === 'course-roster') {
      state.routeData.rosterData = await api(`/api/admin/offerings/${target.dataset.id}/roster`);
      state.modal = renderCourseRosterModal(target.dataset.id);
      renderShell();
    } else if (action === 'admin-roster-drop') {
      const offeringId = target.dataset.offeringId;
      if (!confirm('确定要将该学生从该课程班删除吗？该操作会退掉该学生的这门课。')) return;
      await api('/api/admin/teaching/drop', {
        method: 'POST',
        body: JSON.stringify({ enrollmentId: Number(target.dataset.id) })
      });
      await loadRoute(true);
      state.routeData.rosterData = await api(`/api/admin/offerings/${offeringId}/roster`);
      state.modal = renderCourseRosterModal(offeringId);
      renderShell();
      toast('学生已退课');
    } else if (action === 'teacher-offerings') {
      const semesterId = ensureAdminSemesterSelection('teacherOfferingSemesterId');
      const query = semesterId ? `?semesterId=${encodeURIComponent(semesterId)}` : '';
      state.routeData.teacherCourseData = await api(`/api/admin/teachers/${target.dataset.id}/offerings${query}`);
      state.modal = renderTeacherOfferingsModal(target.dataset.id);
      renderShell();
    } else if (action === 'student-enrollments') {
      const semesterId = ensureAdminSemesterSelection('studentEnrollmentSemesterId');
      const query = semesterId ? `?semesterId=${encodeURIComponent(semesterId)}` : '';
      state.routeData.studentCourseData = await api(`/api/admin/students/${target.dataset.id}/enrollments${query}`);
      state.modal = renderStudentEnrollmentsModal(target.dataset.id);
      renderShell();
    } else if (action === 'open-create-semester-modal') {
      state.modal = renderCreateSemesterModal();
      renderShell();
    } else if (action === 'open-edit-semester-modal') {
      state.modal = renderEditSemesterModal(target.dataset.id);
      renderShell();
    } else if (action === 'semester-selection-start') {
      await api(`/api/admin/semesters/${target.dataset.id}/selection/start`, { method: 'POST' });
      await refresh('选课已开放');
    } else if (action === 'semester-selection-stop') {
      if (!confirm('确定要结束该学期选课吗？关闭后学生不能继续选课或退课。')) return;
      await api(`/api/admin/semesters/${target.dataset.id}/selection/stop`, { method: 'POST' });
      await refresh('选课已关闭');
    } else if (action === 'semester-grading-start') {
      await api(`/api/admin/semesters/${target.dataset.id}/grading/start`, { method: 'POST' });
      await refresh('登分已开放');
    } else if (action === 'semester-grading-stop') {
      if (!confirm('确定要结束该学期登分吗？关闭后教师不能继续保存成绩。')) return;
      await api(`/api/admin/semesters/${target.dataset.id}/grading/stop`, { method: 'POST' });
      await refresh('登分已关闭');
    } else if (action === 'open-course-modal') {
      state.modal = renderCourseModal();
      renderShell();
    } else if (action === 'close-course-modal') {
      state.modal = '';
      renderShell();
    } else if (action === 'open-offering-modal') {
      state.modal = renderOfferingModal();
      renderShell();
    } else if (action === 'close-offering-modal') {
      state.modal = '';
      renderShell();
    } else if (action === 'edit-offering') {
      state.modal = renderEditOfferingModal(target.dataset.id);
      renderShell();
    } else if (action === 'add-offering-time') {
      const list = target.closest('form')?.querySelector('[data-offering-times]');
      if (list) list.insertAdjacentHTML('beforeend', offeringTimeFields());
    } else if (action === 'remove-offering-time') {
      const list = target.closest('[data-offering-times]');
      if (list && list.querySelectorAll('[data-time-segment]').length > 1) {
        target.closest('[data-time-segment]')?.remove();
      } else {
        toast('至少保留一个上课时间段', 'error');
      }
    } else if (action === 'offering-page') {
      state.offeringPage = Number(target.dataset.page);
      renderShell();
    } else if (action === 'course-catalog-page') {
      state.adminCoursePage = Number(target.dataset.page);
      renderShell();
    } else if (action === 'log-page') {
      state.logPage = Number(target.dataset.page);
      await loadRoute(true);
      renderShell();
    } else if (action === 'backup-page') {
      state.backupPage = Number(target.dataset.page);
      await loadRoute(true);
      renderShell();
    } else if (action === 'reload-audit-logs') {
      await refresh();
    } else if (action === 'reload-backups') {
      await refresh();
    } else if (action === 'run-backup') {
      if (!confirm('确定要立即执行一次数据库逻辑备份吗？')) return;
      await api('/api/admin/backups/run', { method: 'POST' });
      state.backupPage = 1;
      await refresh('数据库备份已完成');
    } else if (action === 'student-page') {
      state.studentPage = Number(target.dataset.page);
      renderShell();
    } else if (action === 'teacher-page') {
      state.teacherPage = Number(target.dataset.page);
      renderShell();
    } else if (action === 'notice-page') {
      state.noticePage = Number(target.dataset.page);
      renderShell();
    } else if (action === 'open-student-modal') {
      state.modal = renderStudentModal();
      renderShell();
    } else if (action === 'open-teacher-modal') {
      state.modal = renderTeacherModal();
      renderShell();
    } else if (action === 'close-modal') {
      state.modal = '';
      renderShell();
    } else if (action === 'edit-student') {
      state.modal = renderEditStudentModal(target.dataset.id);
      renderShell();
    } else if (action === 'edit-teacher') {
      state.modal = renderEditTeacherModal(target.dataset.id);
      renderShell();
    } else if (action === 'student-disable') {
      if (!confirm('确定要弃用该学生吗？弃用后该学生不能登录，历史选课和成绩不受影响。')) return;
      await api(`/api/admin/students/${target.dataset.id}/disable`, { method: 'POST' });
      await refresh('学生已弃用');
    } else if (action === 'student-enable') {
      await api(`/api/admin/students/${target.dataset.id}/enable`, { method: 'POST' });
      await refresh('学生已启用');
    } else if (action === 'teacher-disable') {
      if (!confirm('确定要弃用该教师吗？弃用后该教师不能登录，历史授课安排不受影响。')) return;
      await api(`/api/admin/teachers/${target.dataset.id}/disable`, { method: 'POST' });
      await refresh('教师已弃用');
    } else if (action === 'teacher-enable') {
      await api(`/api/admin/teachers/${target.dataset.id}/enable`, { method: 'POST' });
      await refresh('教师已启用');
    } else if (action === 'admin-roster') {
      state.selected.adminRosterOfferingId = target.dataset.id;
      await loadRoute(true);
      renderShell();
    } else if (action === 'student-select') {
      await api('/api/student/select', { method: 'POST', body: JSON.stringify({ offeringId: Number(target.dataset.id) }) });
      await refresh('选课成功');
    } else if (action === 'student-drop') {
      try {
        await api('/api/student/drop', { method: 'POST', body: JSON.stringify({ enrollmentId: Number(target.dataset.id) }) });
        await refresh('退课成功');
      } catch (error) {
        if (error.message && error.message.includes('已被教师登分')) {
          alert('该课程已被教师登分，不能退课。如需退课请联系管理员。');
        }
        toast(error.message, 'error');
      }
    } else if (action === 'transcript-semester') {
      state.selected.transcriptSemesterId = target.dataset.id;
      renderShell();
    } else if (action === 'grade-course-select-btn') {
      state.selected.gradeOfferingId = target.dataset.id;
      state.routeData.gradeRoster = await api(`/api/teacher/grade-roster?offeringId=${encodeURIComponent(state.selected.gradeOfferingId)}`);
      renderShell();
    } else if (action === 'save-grade') {
      await saveGrade(target.dataset.id);
    } else if (action === 'teacher-roster') {
      state.routeData.teacherRosterData = await api(`/api/teacher/roster?offeringId=${encodeURIComponent(target.dataset.id)}`);
      state.modal = renderTeacherRosterModal(target.dataset.id);
      renderShell();
    } else if (action === 'dashboard-go') {
      state.route = target.dataset.route;
      localStorage.setItem('course-ui-route', state.route);
      await loadRoute();
      renderShell();
    }
  } catch (error) {
    toast(error.message, 'error');
  }
});

document.addEventListener('change', async (event) => {
  const target = event.target.closest('[data-action]');
  if (!target) return;
  const action = target.dataset.action;
  try {
    if (action === 'grade-course-select') {
      state.selected.gradeOfferingId = target.value;
      state.routeData.gradeRoster = await api(`/api/teacher/grade-roster?offeringId=${encodeURIComponent(state.selected.gradeOfferingId)}`);
      renderShell();
    } else if (action === 'offering-semester-select') {
      state.selected.offeringSemesterId = target.value;
      state.offeringPage = 1;
      await refresh();
    } else if (action === 'teacher-offering-semester-select') {
      state.selected.teacherOfferingSemesterId = target.value;
      const query = target.value ? `?semesterId=${encodeURIComponent(target.value)}` : '';
      state.routeData.teacherCourseData = await api(`/api/admin/teachers/${target.dataset.teacherId}/offerings${query}`);
      state.modal = renderTeacherOfferingsModal(target.dataset.teacherId);
      renderShell();
    } else if (action === 'student-enrollment-semester-select') {
      state.selected.studentEnrollmentSemesterId = target.value;
      const query = target.value ? `?semesterId=${encodeURIComponent(target.value)}` : '';
      state.routeData.studentCourseData = await api(`/api/admin/students/${target.dataset.studentId}/enrollments${query}`);
      state.modal = renderStudentEnrollmentsModal(target.dataset.studentId);
      renderShell();
    } else if (action === 'schedule-semester-select') {
      state.selected.scheduleSemesterId = target.value;
      const query = target.value ? `?semesterId=${encodeURIComponent(target.value)}` : '';
      state.routeData.schedule = await api(`/api/student/schedule${query}`);
      renderShell();
    }
  } catch (error) {
    toast(error.message, 'error');
  }
});

document.addEventListener('submit', async (event) => {
  const form = event.target.closest('[data-form]');
  if (!form) return;
  event.preventDefault();
  const name = form.dataset.form;
  try {
    if (name === 'login') {
      const data = formObject(form);
      const login = await api('/api/auth/login', { method: 'POST', body: JSON.stringify(data) });
      saveSession(login);
      state.route = 'dashboard';
      await loadRoute(true);
      renderShell();
      toast('登录成功');
    } else if (name === 'change-password') {
      const data = formObject(form);
      if (data.newPassword !== data.confirmPassword) {
        toast('两次输入的新密码不一致', 'error');
        return;
      }
      await api('/api/auth/password', {
        method: 'POST',
        body: JSON.stringify({ oldPassword: data.oldPassword, newPassword: data.newPassword })
      });
      clearSession();
      await loadLanding();
      renderLogin();
      toast('密码已修改，请重新登录');
    } else if (name === 'create-semester') {
      const data = asNumberFields(formObject(form), ['maxCredit']);
      if (!validateSemesterPayload(data)) return;
      await api('/api/admin/semesters', { method: 'POST', body: JSON.stringify(data) });
      state.modal = '';
      await refresh('学期已新建');
    } else if (name === 'update-semester') {
      const data = asNumberFields(formObject(form), ['maxCredit']);
      if (!validateSemesterPayload(data, form.dataset.semesterId)) return;
      await api(`/api/admin/semesters/${form.dataset.semesterId}`, { method: 'PUT', body: JSON.stringify(data) });
      state.modal = '';
      await refresh('学期信息已保存');
    } else if (name === 'create-course-modal') {
      await api('/api/admin/courses', { method: 'POST', body: JSON.stringify(asNumberFields(formObject(form), ['departmentId', 'credit'])) });
      state.modal = '';
      await refresh('课程已添加');
    } else if (name === 'create-offering-modal') {
      const data = collectOfferingPayload(form);
      await api('/api/admin/offerings', { method: 'POST', body: JSON.stringify(data) });
      state.modal = '';
      await refresh('课程班已添加');
    } else if (name === 'edit-offering-modal') {
      const offeringId = form.dataset.offeringId;
      const data = collectOfferingPayload(form);
      await api(`/api/admin/offerings/${offeringId}`, { method: 'PUT', body: JSON.stringify(data) });
      state.modal = '';
      await refresh('课程班已更新');
    } else if (name === 'offering-search') {
      state.filters.offeringKeyword = formObject(form).keyword || '';
      state.offeringPage = 1;
      await refresh();
    } else if (name === 'course-catalog-search') {
      state.filters.adminCourseKeyword = formObject(form).keyword || '';
      state.adminCoursePage = 1;
      await refresh();
    } else if (name === 'teacher-search') {
      state.filters.teacherKeyword = formObject(form).keyword || '';
      state.teacherPage = 1;
      await refresh();
    } else if (name === 'student-search') {
      state.filters.studentKeyword = formObject(form).keyword || '';
      state.studentPage = 1;
      await refresh();
    } else if (name === 'create-student-modal') {
      await api('/api/admin/students', { method: 'POST', body: JSON.stringify(asNumberFields(formObject(form), ['majorId', 'admissionYear'])) });
      state.modal = '';
      await refresh('学生已添加');
    } else if (name === 'edit-student-modal') {
      const studentId = form.dataset.studentId;
      await api(`/api/admin/students/${studentId}`, { method: 'PUT', body: JSON.stringify(asNumberFields(formObject(form), ['majorId', 'admissionYear'])) });
      state.modal = '';
      await refresh('学生信息已更新');
    } else if (name === 'create-teacher-modal') {
      await api('/api/admin/teachers', { method: 'POST', body: JSON.stringify(asNumberFields(formObject(form), ['departmentId'])) });
      state.modal = '';
      await refresh('教师已添加');
    } else if (name === 'edit-teacher-modal') {
      const teacherId = form.dataset.teacherId;
      await api(`/api/admin/teachers/${teacherId}`, { method: 'PUT', body: JSON.stringify(asNumberFields(formObject(form), ['departmentId'])) });
      state.modal = '';
      await refresh('教师信息已更新');
    } else if (name === 'student-teaching-search') {
      state.filters.studentNo = formObject(form).studentNo || '';
      await refresh();
    } else if (name === 'course-search') {
      state.filters.courseKeyword = formObject(form).keyword || '';
      await refresh();
    } else if (name === 'admin-notice') {
      await api('/api/admin/notices', { method: 'POST', body: JSON.stringify(formObject(form)) });
      form.reset();
      await loadCatalog(true);
      await refresh('通知已发布');
    } else if (name === 'admin-notice-modal') {
      const noticeId = form.dataset.noticeId;
      const payload = formObject(form);
      if (noticeId) {
        await api(`/api/admin/notices/${noticeId}`, { method: 'PUT', body: JSON.stringify(payload) });
      } else {
        await api('/api/admin/notices', { method: 'POST', body: JSON.stringify(payload) });
      }
      state.modal = '';
      await refresh(noticeId ? '通知已更新' : '通知已发布');
    }
  } catch (error) {
    const messageNode = form.querySelector('[data-login-message]');
    if (messageNode) messageNode.textContent = error.message;
    toast(error.message, 'error');
  }
});

async function saveGrade(enrollmentId) {
  const row = document.querySelector(`tr[data-enrollment-id="${CSS.escape(String(enrollmentId))}"]`);
  const usualScore = row?.querySelector('input[name="usualScore"]')?.value;
  const examScore = row?.querySelector('input[name="examScore"]')?.value;
  if (usualScore === '' || examScore === '') {
    toast('请同时录入平时分和考试分', 'warn');
    return;
  }
  await api('/api/teacher/grades', {
    method: 'POST',
    body: JSON.stringify({ enrollmentId: Number(enrollmentId), usualScore: Number(usualScore), examScore: Number(examScore) })
  });
  await refresh('成绩已保存');
}

function friendlyStatus(status) {
  return ({
    not_started: '未开始',
    active: '进行中',
    planning: '筹备中',
    selecting: '选课中',
    closed: '已结束',
    archived: '已归档',
    open: '开放',
    selected: '已选',
    dropped: '已退选',
    enabled: '启用',
    disabled: '停用'
  })[status] || status || '暂无';
}

function display(value, fallback = '暂无') {
  return text(value === null || value === undefined || value === '' ? fallback : value);
}

function loginView() {
  const current = state.landing?.currentSemester || {};
  const notices = state.landing?.notices || [];
  const noticePreview = notices.slice(0, 3);
  return `
    <div class="login-view login-product">
      <section class="login-visual">
        <div class="login-brandline">
          <div class="school-mark">教</div>
          <strong>教务管理平台</strong>
        </div>
        <div class="login-title">
          <h1>教务管理平台</h1>
          <p>选课、成绩、课表与培养全过程管理，帮助学生与教师高效完成日常教务事务。</p>
          <span class="title-rule"></span>
        </div>
        <div class="login-illustration" aria-hidden="true">
          <div class="campus-building">
            <span></span><span></span><span></span><span></span>
          </div>
          <div class="screen-card">
            <div class="screen-top"></div>
            <div class="screen-grid">
              <span></span><span></span><span></span><span></span>
              <span></span><span></span><span></span><span></span>
            </div>
          </div>
          <div class="cap-shape"></div>
          <div class="book-stack"></div>
          <div class="orbit-icon orbit-calendar">□</div>
          <div class="orbit-icon orbit-chart">▥</div>
        </div>
        <div class="campus-panel login-cards">
          <button class="campus-card current-term-card" type="button" data-action="login-required">
            <span class="card-kicker">当前学期</span>
            <strong>${display(current.name)}</strong>
            <div class="mini-stat"><span>状态</span><b>${text(friendlyStatus(current.status))}</b></div>
            <div class="mini-stat"><span>日期</span><b>${text(dateRange(current.startDate, current.endDate) || '暂无')}</b></div>
          </button>
          ${[
            ['在线选课', '查看可选课程与选课进度'],
            ['成绩查询', '查看成绩、绩点与学分'],
            ['课表管理', '统一查看课程安排与考试时间']
          ].map(([title, desc]) => `
            <button class="campus-card feature-card" type="button" data-action="login-required">
              <span class="feature-icon">↗</span>
              <strong>${title}</strong>
              <p>${desc}</p>
            </button>
          `).join('')}
        </div>
        <section class="campus-table login-announcements">
          <div class="announce-title"><strong>通知公告</strong></div>
          <div class="announce-list">
            ${noticePreview.length ? noticePreview.map((notice) => `
              <button class="login-notice" type="button" data-action="login-required">
                <span class="login-notice-dot"></span>
                <div><b>${text(notice.title)}</b><p>${text(notice.content)}</p><small>${text((notice.createdAt || '').slice(5, 10))}</small></div>
              </button>
            `).join('') : '<div class="empty-state">暂无通知</div>'}
          </div>
          <button class="announce-more" type="button" data-action="login-required" data-message="请登录后查看">更多公告 ›</button>
        </section>
      </section>
      <aside class="login-panel-wrap">
        <form class="login-panel" data-form="login">
          <h2>登录</h2>
          <p class="login-help">请输入学校统一账号和密码。</p>
          <label class="field"><span>账号</span><input name="username" autocomplete="username" required></label>
          <label class="field"><span>密码</span><input name="password" type="password" autocomplete="current-password" required></label>
          <button class="btn btn-primary" type="submit">登录系统</button>
          <p class="message" data-login-message></p>
        </form>
      </aside>
    </div>
  `;
}

function renderShell() {
  ensureRoute();
  const nav = navs[state.user.role] || [];
  const title = currentRouteTitle();
  app.innerHTML = `
    <div class="app-shell">
      <aside class="sidebar">
        <div class="brand">
          <div class="brand-mark">教</div>
          <div><span>Academic Portal</span><strong>教务管理平台</strong></div>
        </div>
        <nav class="nav-list">
          <div class="nav-section">${text(roleText[state.user.role])}</div>
          ${nav.map(([id, label]) => `
            <button class="nav-button ${state.route === id ? 'active' : ''}" data-action="navigate" data-route="${id}">
              <span class="nav-dot"></span><strong>${label}</strong>
            </button>
          `).join('')}
        </nav>
        <div class="sidebar-footer">
          <div class="sidebar-user">
            <div class="avatar">${text((state.user.displayName || state.user.username || 'U').slice(0, 1))}</div>
            <div><strong>${text(state.user.displayName || state.user.username)}</strong><span>${text(roleText[state.user.role])}</span></div>
          </div>
          <button class="btn btn-logout" data-action="logout">退出登录</button>
        </div>
      </aside>
      <main class="main">
        <header class="topbar">
          <div>
            <div class="breadcrumb"><span>${text(roleText[state.user.role])}</span><span>/</span><span>${title}</span></div>
            <h1 class="page-title">${title}</h1>
          </div>
          <div class="topbar-actions">
            ${topbarTerm()}
            <button class="btn notice-button" data-action="notice">通知<span>${number((state.catalog?.notices || []).length)}</span></button>
          </div>
        </header>
        ${state.route === 'dashboard' ? '' : termStrip()}
        ${renderRoute()}
      </main>
    </div>
  `;
  modalRoot.innerHTML = state.modal || '';
}

function currentRouteTitle() {
  if (state.route === 'noticeManage') {
    return state.user?.role === 'admin' ? '通知发布' : '通知公告';
  }
  return routeTitles[state.route] || '首页';
}

function metrics(items, className = '') {
  const rows = items.length ? items : [
    { label: '当前学期', value: state.catalog?.currentSemester?.name || '暂无' },
    { label: '选课状态', value: friendlyStatus(state.catalog?.currentSemester?.status) }
  ];
  return `
    <section class="metric-grid ${text(className)}">
      ${rows.map((item) => `
        <div class="metric-card">
          <span>${text(item.label)}</span>
          <strong>${display(item.value)}</strong>
        </div>
      `).join('')}
    </section>
  `;
}

function clampPercent(value) {
  const parsed = Number(value);
  if (!Number.isFinite(parsed)) return 0;
  return Math.max(0, Math.min(100, parsed));
}

function percentLabel(value) {
  const percent = clampPercent(value);
  return `${percent % 1 === 0 ? percent.toFixed(0) : percent.toFixed(1)}%`;
}

function loadLevel(value) {
  const percent = clampPercent(value);
  if (percent >= 85) return 'danger';
  if (percent >= 70) return 'warn';
  return 'ok';
}

function systemStatusPanel(status = {}) {
  const cpu = status.cpu || {};
  const memory = status.memory || {};
  const disk = status.disk || {};
  const network = status.network || {};
  return `
    <section class="panel system-status-card">
      <div class="panel-header">
        <div><h2>系统运行状态</h2><p>实时概览服务器资源占用与网络连通情况。</p></div>
      </div>
      <div class="system-status-grid">
        ${cpuGauge(cpu)}
        ${usageMeter('内存使用率', memory, 'memory')}
        ${diskMeter(disk)}
        ${networkMeter(network)}
      </div>
    </section>
  `;
}

function cpuGauge(metric = {}) {
  const percent = clampPercent(metric.value);
  return `
    <article class="system-metric cpu-metric ${loadLevel(percent)}">
      <div class="status-ring" style="--value:${percent}%"><strong>${percentLabel(percent)}</strong></div>
      <div><span>CPU 使用率</span><p>${text(metric.detail || '处理器负载')}</p></div>
    </article>
  `;
}

function usageMeter(label, metric = {}, kind = '') {
  const percent = clampPercent(metric.value);
  return `
    <article class="system-metric usage-metric ${kind} ${loadLevel(percent)}">
      <div class="metric-line">
        <span>${text(label)}</span>
        <strong>${percentLabel(percent)}</strong>
      </div>
      <div class="wide-meter"><i style="width:${percent}%"></i></div>
      <p>${text(metric.detail || '暂无容量数据')}</p>
    </article>
  `;
}

function diskMeter(metric = {}) {
  const percent = clampPercent(metric.value);
  const activeBlocks = Math.ceil(percent / 100 * 12);
  return `
    <article class="system-metric disk-metric ${loadLevel(percent)}">
      <div class="metric-line">
        <span>磁盘使用率</span>
        <strong>${percentLabel(percent)}</strong>
      </div>
      <div class="disk-blocks">
        ${Array.from({ length: 12 }, (_, index) => `<i class="${index < activeBlocks ? 'active' : ''}"></i>`).join('')}
      </div>
      <p>${text(metric.detail || '暂无磁盘数据')}</p>
    </article>
  `;
}

function networkMeter(metric = {}) {
  const active = Number(metric.activeAdapters || 0);
  const total = Number(metric.availableAdapters || 0);
  const status = metric.status || (active > 0 ? 'online' : 'offline');
  const activeBars = status === 'online' ? Math.max(2, Math.min(4, active || 4)) : status === 'unknown' ? 1 : 0;
  return `
    <article class="system-metric network-metric ${status}">
      <div class="network-signal" aria-hidden="true">
        ${[1, 2, 3, 4].map((bar) => `<i class="${bar <= activeBars ? 'active' : ''}"></i>`).join('')}
      </div>
      <div>
        <span>网络流量 / 状态</span>
        <strong>${status === 'online' ? '在线' : status === 'offline' ? '离线' : '未知'}</strong>
        <p>${number(active)}/${number(total)} 个网卡可用</p>
      </div>
    </article>
  `;
}

function renderDashboard() {
  if (state.user?.role === 'admin') return renderAdminDashboard();
  if (state.user?.role === 'teacher') return renderTeacherDashboard();
  if (state.user?.role === 'student') return renderStudentDashboard();
  return empty('暂无内容');
}

function renderAdminDashboard() {
  const data = state.routeData.dashboard || {};
  const term = state.catalog?.currentSemester || {};
  return `
    <div class="page-grid role-home admin-home">
      <section class="role-hero admin-hero">
        <div>
          <span class="eyebrow">管理员首页</span>
          <h2>今日教务运行</h2>
          <p>集中查看当前学期、选课状态、系统运行状态与公告发布。</p>
          <div class="hero-actions">
            <button class="btn btn-primary" data-action="dashboard-go" data-route="selectionControl">管理学期</button>
            <button class="btn" data-action="dashboard-go" data-route="courseManage">安排课程</button>
            <button class="btn" data-action="open-notice-modal">发布通知</button>
          </div>
        </div>
        <div class="hero-term">
          <span>当前学期</span>
          <strong>${display(term.name)}</strong>
          <b>${text(friendlyStatus(term.status))}</b>
          <small>${text(dateRange(term.startDate, term.endDate) || '暂无选课日期')}</small>
        </div>
      </section>
      <div class="admin-dashboard-grid">
        <div class="admin-primary-column">
          ${metrics(data.summary || [], 'admin-metrics')}
          ${systemStatusPanel(data.systemStatus || {})}
        </div>
        <section class="panel admin-notice-panel">
          <div class="panel-header"><div><h2>通知公告</h2><p>面向不同角色发布校内通知。</p></div><button class="btn" data-action="notice">查看全部</button></div>
          ${noticeList(data.notices || state.catalog?.notices || [], { limit: 5 })}
        </section>
      </div>
    </div>
  `;
}

function currentTeacherCourses(courses) {
  const currentSemesterId = state.catalog?.currentSemester?.id;
  if (currentSemesterId) {
    return courses.filter((course) => String(course.semesterId) === String(currentSemesterId));
  }
  return courses.filter((course) => course.semesterStatus === 'active');
}

function completedGradeCourseCount(courses) {
  return courses.filter((course) => Number(course.gradedCount || 0) >= Number(course.studentCount || 0)).length;
}

function renderTeacherDashboard() {
  const data = state.routeData.dashboard || {};
  const courses = state.routeData.teacherCourses || [];
  const gradeCourses = state.routeData.teacherGradeCourses || [];
  const currentCourses = currentTeacherCourses(courses);
  const completedGradable = completedGradeCourseCount(gradeCourses);
  return `
    <div class="page-grid role-home teacher-home">
      <section class="role-hero teacher-hero">
        <div>
          <span class="eyebrow">教师首页</span>
          <h2>${display(state.user?.displayName || state.user?.username)}，查看授课与登分任务</h2>
          <p>聚焦本学期授课安排和已开放学期成绩登记。</p>
          <div class="hero-actions">
            <button class="btn btn-primary" data-action="dashboard-go" data-route="gradeEntry">登记成绩</button>
            <button class="btn" data-action="dashboard-go" data-route="courses">我的课程</button>
            <button class="btn" data-action="dashboard-go" data-route="teachingSchedule">排课安排</button>
          </div>
        </div>
        <div class="teacher-counts">
          <div><span>本学期课程</span><strong>${number(currentCourses.length)}</strong></div>
          <div><span>可登分课程</span><strong>${number(completedGradable)}/${number(gradeCourses.length)}</strong></div>
        </div>
      </section>
      <div class="grid-2">
        <section class="panel">
          <div class="panel-header"><div><h2>本学期授课</h2><p>显示当前学期的授课课程班。</p></div><button class="btn" data-action="dashboard-go" data-route="teachingSchedule">课表</button></div>
          <div class="list-stack">
            ${currentCourses.map((course) => `
              <div class="list-item">
                <div><strong>${text(course.courseCode)} ${text(course.courseName)}</strong><p>${courseTime(course)} · ${number(course.selectedCount)}/${number(course.capacity)} 人</p></div>
                <button class="btn" data-action="teacher-roster" data-id="${course.id}">名单</button>
              </div>
            `).join('') || empty('暂无本学期授课课程')}
          </div>
        </section>
        <section class="panel">
          <div class="panel-header"><div><h2>通知公告</h2><p>查看与教学相关的通知。</p></div><button class="btn" data-action="notice">查看</button></div>
          ${noticeList(data.notices || state.catalog?.notices || [])}
        </section>
      </div>
    </div>
  `;
}

function renderStudentDashboard() {
  const data = state.routeData.dashboard || {};
  const scheduleRows = state.routeData.schedule || [];
  const transcriptRows = state.routeData.transcript?.rows || [];
  const term = state.catalog?.currentSemester || {};
  return `
    <div class="page-grid role-home student-home">
      <section class="role-hero student-hero">
        <div>
          <span class="eyebrow">学生首页</span>
          <h2>${display(state.user?.displayName || state.user?.username)}，安排本学期学习</h2>
          <p>查看选课窗口、课表安排和历史成绩趋势。</p>
          <div class="hero-actions">
            <button class="btn btn-primary" data-action="dashboard-go" data-route="courseSelect">去选课</button>
            <button class="btn" data-action="dashboard-go" data-route="selectedCourses">看课表</button>
            <button class="btn" data-action="dashboard-go" data-route="grades">查成绩</button>
          </div>
        </div>
        <div class="hero-term">
          <span>当前学期</span>
          <strong>${display(term.name, '暂无当前学期')}</strong>
        </div>
      </section>
      ${metrics(data.summary || [])}
      <div class="grid-2">
        <section class="panel">
          <div class="panel-header"><div><h2>近期课表</h2><p>显示当前已选课程。</p></div></div>
          <div class="list-stack">
            ${scheduleRows.slice(0, 5).map((course) => `
              <div class="list-item">
          <div><strong>${text(course.courseName)}</strong><p>${courseTime(course)}</p></div>
                <span class="status-tag">${text(course.teacherName)}</span>
              </div>
            `).join('') || empty('暂无已选课程')}
          </div>
        </section>
        <section class="panel">
          <div class="panel-header"><div><h2>绩点趋势</h2><p>按历史学期汇总平均绩点。</p></div><button class="btn" data-action="dashboard-go" data-route="grades">详情</button></div>
          ${gpaTrend(transcriptRows)}
        </section>
      </div>
    </div>
  `;
}

function noticeList(notices, options = {}) {
  const rows = Array.isArray(notices) ? notices.slice(0, options.limit || notices.length) : [];
  if (!rows.length) return empty('暂无通知');
  return `<div class="list-stack notice-list">${rows.map((notice) => `
    <div class="list-item notice-row">
      <div class="notice-copy">
        <div class="notice-title-line">
          <strong>${text(notice.title)}</strong>
          ${options.showAudience ? audienceBadge(notice.audience) : ''}
        </div>
        <p>${text(notice.content)}</p>
      </div>
      <div class="notice-side">
        <span class="small muted">${text((notice.createdAt || '').slice(0, 10))}</span>
        ${options.adminActions ? `
          <div class="row-actions">
            <button class="btn" data-action="edit-notice" data-id="${notice.id}">编辑</button>
            <button class="btn btn-danger" data-action="delete-notice" data-id="${notice.id}">删除</button>
          </div>
        ` : ''}
      </div>
    </div>
  `).join('')}</div>`;
}

function noticeFields(notice = {}) {
  const selectedAudience = notice.audience || 'all';
  return `
    <label class="field"><span>标题</span><input name="title" value="${text(notice.title || '')}" required></label>
    <label class="field"><span>对象</span><select name="audience">
      ${Object.entries(audienceText).map(([value, label]) => `<option value="${value}" ${selectedAudience === value ? 'selected' : ''}>${label}</option>`).join('')}
    </select></label>
    <label class="field full"><span>内容</span><textarea name="content" required>${text(notice.content || '')}</textarea></label>
  `;
}

function renderNoticeEditorModal(noticeId) {
  const notices = state.catalog?.notices || [];
  const notice = notices.find((item) => String(item.id) === String(noticeId));
  const editing = Boolean(noticeId && notice);
  return `
    <div class="modal-backdrop">
      <section class="modal" role="dialog" aria-modal="true" aria-label="${editing ? '编辑通知' : '发布通知'}">
        <div class="modal-header">
          <div><h2>${editing ? '编辑通知' : '发布通知'}</h2><p class="muted">通知会按对象显示在对应用户首页和通知列表中。</p></div>
          <button class="btn btn-ghost" data-action="close-modal">&times;</button>
        </div>
        <div class="modal-body">
          <form class="form-grid" data-form="admin-notice-modal" ${editing ? `data-notice-id="${text(notice.id)}"` : ''}>
            ${noticeFields(notice || {})}
            <div class="form-actions">
              <button class="btn" type="button" data-action="close-modal">取消</button>
              <button class="btn btn-primary" type="submit">${editing ? '保存修改' : '发布通知'}</button>
            </div>
          </form>
        </div>
      </section>
    </div>
  `;
}

function noticeModal() {
  const notices = state.catalog?.notices || state.routeData.dashboard?.notices || [];
  return `
    <div class="modal-backdrop">
      <section class="modal notice-modal" role="dialog" aria-modal="true" aria-label="通知公告">
        <div class="modal-header">
          <div><h2>通知公告</h2><p class="muted">查看与你当前角色相关的通知。</p></div>
          <button class="btn" data-action="close-modal">关闭</button>
        </div>
        <div class="modal-body">
          ${noticeList(notices)}
          ${state.user?.role === 'admin' ? '<div class="form-actions"><button class="btn btn-primary" data-action="open-notice-modal">发布通知</button></div>' : ''}
        </div>
      </section>
    </div>
  `;
}

function renderNoticeManage() {
  const notices = state.catalog?.notices || [];
  const totalPages = Math.max(1, Math.ceil(notices.length / state.noticePageSize));
  if (state.noticePage > totalPages) state.noticePage = totalPages;
  if (state.noticePage < 1) state.noticePage = 1;
  const start = (state.noticePage - 1) * state.noticePageSize;
  const pageItems = notices.slice(start, start + state.noticePageSize);
  if (state.user?.role !== 'admin') {
    return `
      <section class="panel notice-manage-panel">
        <div class="panel-header"><div><h2>通知公告</h2><p>查看与你相关的通知。</p></div></div>
        ${noticeList(pageItems, { showAudience: true })}
        ${renderPagination(totalPages, state.noticePage, 'notice-page')}
      </section>
    `;
  }
  return `
    <section class="panel notice-manage-panel">
      <div class="panel-header">
        <div><h2>通知</h2><p>显示全部通知，可发布、编辑或删除已有通知。</p></div>
        <button class="btn btn-primary" data-action="open-notice-modal">发布通知</button>
      </div>
      ${noticeList(pageItems, { adminActions: true, showAudience: true })}
      ${renderPagination(totalPages, state.noticePage, 'notice-page')}
    </section>
  `;
}

function renderTeacherRoute() {
  if (state.route === 'courses') return renderTeacherCourses();
  if (state.route === 'teachingSchedule') return renderTeacherSchedule();
  if (state.route === 'gradeEntry') return renderGradeEntry();
  if (state.route === 'noticeManage') return renderNoticeManage();
  return empty('页面不存在');
}

function renderGradeEntry() {
  const courses = state.routeData.teacherGradeCourses || [];
  const roster = state.routeData.gradeRoster || [];
  const selectedCourse = courses.find((course) => String(course.id) === String(state.selected.gradeOfferingId));
  return `
    <div class="page-grid">
      <section class="panel">
        <div class="panel-header"><div><h2>成绩登记</h2><p>管理员开放登分后，教师才能录入对应学期课程成绩。</p></div></div>
        ${courses.length ? `
          <label class="field">
            <span>登分中学期课程</span>
            <select data-action="grade-course-select">
              ${courses.map((course) => `<option value="${course.id}" ${String(course.id) === String(state.selected.gradeOfferingId) ? 'selected' : ''}>${text(course.semesterName)} · ${text(course.courseCode)} ${text(course.courseName)} · 已登记 ${number(course.gradedCount)}/${number(course.studentCount)}</option>`).join('')}
            </select>
          </label>
        ` : empty('当前未开放登分')}
      </section>
      <section class="panel">
        <div class="panel-header"><div><h2>学生成绩</h2><p>${selectedCourse ? `${text(selectedCourse.courseCode)} ${text(selectedCourse.courseName)}，共 ${number(selectedCourse.studentCount)} 名学生。` : '请选择课程后录入成绩。'}</p></div></div>
        ${gradeEntryTable(roster)}
      </section>
    </div>
  `;
}

function gradeEntryTable(rows) {
  if (!rows.length) return empty('暂无学生名单');
  return `
    <div class="table-wrap grade-entry-wrap">
      <table class="grade-entry-table">
        <thead><tr><th class="col-id">学号</th><th class="col-name">姓名</th><th class="col-major">专业</th><th class="col-email">邮箱</th><th class="col-score">平时成绩</th><th class="col-score">考试成绩</th><th class="col-score">总评</th><th class="col-gpa">绩点</th><th class="col-actions">操作</th></tr></thead>
        <tbody>${rows.map((row) => `
          <tr data-enrollment-id="${row.enrollmentId}">
            <td>${text(row.studentNo)}</td>
            <td>${text(row.studentName)}</td>
            <td>${text(row.majorName)}</td>
            <td>${text(row.email)}</td>
            <td><label class="score-input"><input name="usualScore" type="number" min="0" max="100" step="0.1" value="${text(row.usualScore ?? '')}" aria-label="平时成绩"><span>/100</span></label></td>
            <td><label class="score-input"><input name="examScore" type="number" min="0" max="100" step="0.1" value="${text(row.examScore ?? '')}" aria-label="考试成绩"><span>/100</span></label></td>
            <td>${finalScoreText(row.finalScore)}</td>
            <td>${display(row.gradePoint)}</td>
            <td><button class="btn btn-primary" data-action="save-grade" data-id="${row.enrollmentId}">保存</button></td>
          </tr>
        `).join('')}</tbody>
      </table>
    </div>
  `;
}

boot();
