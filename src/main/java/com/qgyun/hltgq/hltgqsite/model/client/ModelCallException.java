package com.qgyun.hltgq.hltgqsite.model.client;

/**
 * 模型服务调用异常。
 * <p>code 沿用模型侧约定：0=成功、1=参数错误、2=模型异常；-1=网络/超时/解析类本地异常。
 */
public class ModelCallException extends RuntimeException {

    private final int code;

    public ModelCallException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
