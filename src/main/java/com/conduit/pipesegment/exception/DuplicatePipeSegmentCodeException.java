package com.conduit.pipesegment.exception;

public class DuplicatePipeSegmentCodeException extends RuntimeException {

    public DuplicatePipeSegmentCodeException(String code) {
        super("管段编码已存在: " + code);
    }
}
