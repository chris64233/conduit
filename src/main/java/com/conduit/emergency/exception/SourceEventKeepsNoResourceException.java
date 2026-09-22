package com.conduit.emergency.exception;

public class SourceEventKeepsNoResourceException extends ResourceTransferConflictException {

    public SourceEventKeepsNoResourceException(Long sourceEventId) {
        super("转移后源事件至少需要保留一个资源: " + sourceEventId);
    }
}
