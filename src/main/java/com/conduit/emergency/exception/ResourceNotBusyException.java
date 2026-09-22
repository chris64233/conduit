package com.conduit.emergency.exception;

public class ResourceNotBusyException extends ResourceTransferConflictException {

    public ResourceNotBusyException(String code) {
        super("待转移资源当前不是 BUSY 状态: " + code);
    }
}
