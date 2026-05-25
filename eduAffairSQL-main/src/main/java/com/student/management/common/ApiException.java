package com.student.management.common;

/**
 * 业务异常，携带 HTTP 状态码。
 * 抛出后被 {@link GlobalExceptionHandler} 捕获并转为统一的 JSON 错误响应。
 * 这样 Service 层不需要依赖 HttpServletResponse，只需 throw 即可。
 */
public class ApiException extends RuntimeException {
    private final int status;

    public ApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
