package com.bbtc.bluebird.common.exception;

/**
 * 统一错误码（唯一权威源，02 §1.4）。
 *
 * <p>区间：{@code 1xxxx=common}、{@code 2xxxx=identity}、{@code 3xxxx=task}、
 * {@code 4xxxx=file}、{@code 5xxxx=note}、{@code 6xxxx=org/同步}。
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
    LOCAL_LOGIN_DISABLED(20007, "当前已启用统一认证，请使用单点登录（仅管理员可账密登录）"),
    EXTERNAL_LOGIN_FAILED(20008, "统一认证登录失败"),

    TASK_NOT_FOUND(30001, "任务不存在"),
    TASK_DELETED(30002, "任务已删除"),
    TASK_ALREADY_COMPLETED(30003, "任务已完成"),
    PARENT_NOT_FOUND(30004, "父任务不存在"),

    FILE_NOT_FOUND(40001, "文件不存在"),
    FILE_TOO_LARGE(40002, "文件超限"),
    FILE_TYPE_NOT_ALLOW(40003, "文件类型不允许"),

    NOTE_NOT_FOUND(50001, "工作日志不存在"),
    NOTE_CONTENT_TOO_LONG(50002, "日志内容超过 20000 字符"),
    NOTE_TAG_INVALID(50003, "标签不存在或不属于日志所有者"),
    NOTE_ATTACHMENT_INVALID(50004, "附件不存在或不属于当前用户"),
    NOTE_ATTACHMENT_NOT_LINKED(50005, "附件未关联到该工作日志"),
    NOTE_RELATION_TARGET_NOT_FOUND(50006, "引用目标不存在或不可见"),
    NOTE_GRANT_DUPLICATE(50007, "已存在对该用户的授权（待确认或已生效）"),
    NOTE_GRANT_SELF(50008, "不能给自己授权"),
    NOTE_GRANT_NOT_FOUND(50009, "授权记录不存在"),
    NOTE_GRANT_NOT_PENDING(50010, "授权记录非待确认状态"),
    NOTE_GRANT_NOT_SUBORDINATE(50011, "只能向下属发起授权"),
    NOTE_LOG_DATE_INVALID(50012, "日志日期业务非法（未来超 7 天）；格式不可解析（如 2026-02-30）走 HTTP 400"),
    NOTE_FIELD_FORBIDDEN(50013, "当前角色无权修改该字段"),
    NOTE_GRANT_FLOW_FORBIDDEN(50014, "授权状态机不允许该操作"),
    NOTE_GRANT_MODE_INVALID(50015, "授权发起模式或其必填字段不合法"),
    NOTE_SCOPE_DISABLED(50016, "当前作用域不可用（如下属可见开关关闭）"),
    NOTE_DEPT_REQUIRED(50017, "INTERNAL 日志需要 owner 有部门"),
    NOTE_ATTACHMENT_CANNOT_BIND(50018, "附件不可绑定（HTTP 权限层：附件创建者≠me 且非 ADMIN）"),
    NOTE_ATTACHMENT_BOUND(50019, "附件已被其他 task 绑定（task 场景，非 note）"),
    TASK_ATTACHMENT_NOT_LINKED(50020, "附件不属于该任务"),
    TASK_ATTACHMENT_FORBIDDEN(50021, "通用文件上传不允许携带 taskId；请走任务附件专用端点"),

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
