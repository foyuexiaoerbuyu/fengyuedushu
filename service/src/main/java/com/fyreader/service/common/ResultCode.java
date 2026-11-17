package com.fyreader.service.common;

/**
 * 统一响应码。
 */
public enum ResultCode {

    SUCCESS(0, "ok"),
    BAD_REQUEST(400, "请求参数不合法"),
    NOT_FOUND(404, "资源不存在"),
    UNAUTHORIZED(401, "未授权"),
    CONFLICT(409, "资源冲突"),
    ERROR(500, "服务器内部错误");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
