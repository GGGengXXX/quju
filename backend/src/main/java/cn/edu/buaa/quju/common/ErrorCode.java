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
    ACCOUNT_BANNED(2007, "账号已被封禁"),
    // 活动 3000-3999
    ACTIVITY_FULL(3000, "活动名额已满"),
    SIGNUP_DEADLINE_PASSED(3001, "已过报名截止"),
    ALREADY_SIGNED_UP(3002, "已报名"),
    NOT_SIGNED_UP(3003, "未报名"),
    ACTIVITY_NOT_PUBLISHED(3004, "活动未发布或不可报名"),
    SIGNUP_CHECK_FAILED(3005, "报名校验未通过"),
    WAITLIST_CONFIRM_EXPIRED(3006, "候补确认超时"),
    CHECKIN_CODE_INVALID(3007, "签到码无效"),
    CHECKIN_LOCATION_TOO_FAR(3008, "签到位置不在活动地点附近"),
    REVIEW_WINDOW_CLOSED(3009, "评价入口已关闭"),
    ACTIVITY_NOT_OWNER(3010, "非活动发起人"),
    // 社交/好友/IM 4000-4999
    ALREADY_FRIENDS(4000, "已是好友"),
    BLOCKED_RELATION(4001, "存在黑名单关系，操作受限"),
    FRIEND_REQUEST_DUPLICATE(4002, "重复的好友申请"),
    NOT_FRIENDS(4003, "非好友关系"),
    MESSAGE_RECALL_EXPIRED(4004, "超过2分钟，不可撤回"),
    // 后台管理 6000-6999
    BAN_REASON_REQUIRED(6000, "封禁需填写原因与期限"),
    REJECT_REASON_REQUIRED(6001, "驳回需填写原因"),
    TAKEDOWN_REASON_REQUIRED(6002, "下架/停用需填写原因");

    private final int code;
    private final String message;
    ErrorCode(int code, String message) { this.code = code; this.message = message; }
    public int getCode() { return code; }
    public String getMessage() { return message; }
}
