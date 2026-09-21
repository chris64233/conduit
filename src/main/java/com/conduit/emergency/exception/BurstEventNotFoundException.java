package com.conduit.emergency.exception;

public class BurstEventNotFoundException extends RuntimeException {

    public BurstEventNotFoundException(Long id) {
        super("爆管事件不存在: " + id);
    }
}
