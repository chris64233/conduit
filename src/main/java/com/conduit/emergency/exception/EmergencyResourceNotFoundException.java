package com.conduit.emergency.exception;

public class EmergencyResourceNotFoundException extends RuntimeException {

    public EmergencyResourceNotFoundException(Long id) {
        super("应急资源不存在: " + id);
    }
}
