package com.student.management.dto;

import com.student.management.security.SessionUser;

/** 登录响应：token 供前端存储并用于后续请求的 Authorization 头 */
public record LoginResponse(String token, SessionUser user) {
}
