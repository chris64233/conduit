package com.conduit.emergency.exception;

public class ResourceTransferNotFoundException extends RuntimeException {

    public ResourceTransferNotFoundException(Long id) {
        super("资源转移记录不存在: " + id);
    }
}
