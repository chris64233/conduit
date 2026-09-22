package com.conduit.emergency.exception;

public class EmergencyResourceCodeNotFoundException extends RuntimeException {

    public EmergencyResourceCodeNotFoundException(String code) {
        super("应急资源不存在: " + code);
    }
}
