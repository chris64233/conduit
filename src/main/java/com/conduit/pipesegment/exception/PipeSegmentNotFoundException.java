package com.conduit.pipesegment.exception;

public class PipeSegmentNotFoundException extends RuntimeException {

    public PipeSegmentNotFoundException(Long id) {
        super("管段不存在: " + id);
    }
}
