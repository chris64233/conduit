package com.conduit.hazard.exception;

public class HazardNotFoundException extends RuntimeException {

    public HazardNotFoundException(Long id) {
        super("隐患不存在: " + id);
    }
}
