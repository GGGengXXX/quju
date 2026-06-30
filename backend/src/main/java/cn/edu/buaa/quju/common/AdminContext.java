package cn.edu.buaa.quju.common;

/** 当前登录管理员(ThreadLocal)，由 AdminAuthInterceptor 填入。 */
public final class AdminContext {
    private static final ThreadLocal<Long> CURRENT = new ThreadLocal<>();
    private AdminContext() {}
    public static void set(Long adminId) { CURRENT.set(adminId); }
    public static Long get() { return CURRENT.get(); }
    public static long require() {
        Long id = CURRENT.get();
        if (id == null) throw new BizException(ErrorCode.UNAUTHORIZED);
        return id;
    }
    public static void clear() { CURRENT.remove(); }
}
