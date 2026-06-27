package cn.edu.buaa.quju.common;

/**
 * 统一响应信封：{ code, message, data }。code=0 成功，非 0 见 contracts/error-codes.md。
 * 所有 controller 返回 R<T>，不要返回裸实体。
 */
public class R<T> {
    private int code;
    private String message;
    private T data;

    public R() {}

    public R(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> R<T> ok(T data) {
        return new R<>(0, "success", data);
    }

    public static <T> R<T> fail(int code, String message) {
        return new R<>(code, message, null);
    }

    public int getCode() { return code; }
    public String getMessage() { return message; }
    public T getData() { return data; }
    public void setCode(int code) { this.code = code; }
    public void setMessage(String message) { this.message = message; }
    public void setData(T data) { this.data = data; }
}
