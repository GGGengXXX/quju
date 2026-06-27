package cn.edu.buaa.quju.common;

/** 统一错误码（与 contracts/error-codes.md 一致）。新增码请同步登记。 */
public enum ErrorCode {
    BAD_REQUEST(1000, "参数错误"),
    UNAUTHORIZED(1001, "未登录或登录失效"),
    FORBIDDEN(1002, "无权限"),
    NOT_FOUND(1003, "资源不存在"),
    CONFLICT(1004, "状态冲突"),
    INTERNAL_ERROR(1500, "服务器内部错误"),
    THIRD_PARTY_ERROR(1501, "第三方服务调用失败"),
    // 用户/商家 2000-2999
    EMAIL_ALREADY_REGISTERED(2000, "邮箱已注册"),
    NICKNAME_TAKEN(2001, "昵称已被占用"),
    ACCOUNT_NOT_ACTIVATED(2002, "账号未激活"),
    INVALID_CREDENTIALS(2003, "邮箱或密码错误"),
    ACTIVATION_TOKEN_INVALID(2004, "激活/重置令牌无效或过期"),
    MERCHANT_LICENSE_REQUIRED(2005, "商家需上传营业执照"),
    ACCOUNT_BANNED(2007, "账号已被封禁");

    private final int code;
    private final String message;
    ErrorCode(int code, String message) { this.code = code; this.message = message; }
    public int getCode() { return code; }
    public String getMessage() { return message; }
}
