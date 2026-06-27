package cn.edu.buaa.quju.common;

/** 当前登录用户(ThreadLocal)。受保护接口用 UserContext.require() 取 userId。 */
public final class UserContext {
    private static final ThreadLocal<Long> CURRENT = new ThreadLocal<>();
    private UserContext() {}

    public static void set(Long userId) { CURRENT.set(userId); }
    public static Long get() { return CURRENT.get(); }
    public static long require() {
        Long id = CURRENT.get();
        if (id == null) throw new BizException(ErrorCode.UNAUTHORIZED);
        return id;
    }
    public static void clear() { CURRENT.remove(); }
}
