package com.conduit.emergency.exception;

public class ResourceNotAvailableException extends RuntimeException {

    public ResourceNotAvailableException(Long resourceId) {
        super("应急资源不可用: " + resourceId);
    }
}
