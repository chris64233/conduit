package com.conduit.emergency.exception;

public class ResourceAlreadyOnTargetEventException extends ResourceTransferConflictException {

    public ResourceAlreadyOnTargetEventException(String code, Long targetEventId) {
        super("目标事件已包含该资源: code=" + code + ", targetEventId=" + targetEventId);
    }
}
