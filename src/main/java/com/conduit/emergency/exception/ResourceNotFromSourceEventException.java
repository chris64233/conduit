package com.conduit.emergency.exception;

public class ResourceNotFromSourceEventException extends ResourceTransferConflictException {

    public ResourceNotFromSourceEventException(String code, Long sourceEventId) {
        super("资源不属于源事件: code=" + code + ", sourceEventId=" + sourceEventId);
    }
}
