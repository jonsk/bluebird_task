package com.bbtc.bluebird.common.exception;

/**
 * 统一错误码（唯一权威源，02 §1.4）。
 *
 * <p>区间：{@code 1xxxx=common}、{@code 2xxxx=identity}、{@code 3xxxx=task}、
 * {@code 4xxxx=file}、{@code 5xxxx=预留}、{@code 6xxxx=org/同步}。
 */
public enum ErrorCode {
    OK(0, "成功"),

    SYSTEM_ERROR(10000, "系统异常"),
    PARAM_ERROR(10001, "参数校验失败"),
    UNAUTHENTICATED(10002, "未登录"),
    FORBIDDEN(10003, "无权限"),
    NOT_FOUND(10004, "资源不存在"),
    REPEAT_SUBMIT(10005, "重复提交"),
    VERSION_CONFLICT(10006, "数据版本冲突"),
    TOO_MANY_REQUESTS(10007, "请求过于频繁"),

    USER_NOT_FOUND(20001, "用户不存在"),
    USER_DISABLED(20002, "用户被禁用"),
    BAD_CREDENTIALS(20003, "用户名或密码错误"),
    NO_PERMISSION_EXTERNAL(20004, "外部身份源无权限"),
    TOKEN_INVALID(20005, "token 无效或过期"),
    ACCOUNT_LOCKED(20006, "账号已锁定"),

    TASK_NOT_FOUND(30001, "任务不存在"),
    TASK_DELETED(30002, "任务已删除"),
    TASK_ALREADY_COMPLETED(30003, "任务已完成"),
    PARENT_NOT_FOUND(30004, "父任务不存在"),

    FILE_NOT_FOUND(40001, "文件不存在"),
    FILE_TOO_LARGE(40002, "文件超限"),
    FILE_TYPE_NOT_ALLOW(40003, "文件类型不允许"),

    SYNC_IN_PROGRESS(60001, "同步进行中");

    private final int code;
    private final String msg;

    ErrorCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public int getCode() {
        return code;
    }

    public String getMsg() {
        return msg;
    }
}
