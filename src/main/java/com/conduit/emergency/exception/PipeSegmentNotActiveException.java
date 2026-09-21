package com.conduit.emergency.exception;

public class PipeSegmentNotActiveException extends RuntimeException {

    public PipeSegmentNotActiveException(Long segmentId) {
        super("管段已停用，不能上报爆管事件: " + segmentId);
    }
}
