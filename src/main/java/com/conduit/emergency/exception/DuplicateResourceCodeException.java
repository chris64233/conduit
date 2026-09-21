package com.conduit.emergency.exception;

public class DuplicateResourceCodeException extends RuntimeException {

    public DuplicateResourceCodeException(String code) {
        super("应急资源编号已存在: " + code);
    }
}
