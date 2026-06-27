package cn.edu.buaa.quju.common;

/** 业务异常：在 service 里 throw new BizException(ErrorCode.XXX)；由 GlobalExceptionHandler 统一转信封。 */
public class BizException extends RuntimeException {
    private final int code;

    public BizException(ErrorCode ec) {
        super(ec.getMessage());
        this.code = ec.getCode();
    }

    public BizException(ErrorCode ec, String message) {
        super(message);
        this.code = ec.getCode();
    }

    public int getCode() { return code; }
}
