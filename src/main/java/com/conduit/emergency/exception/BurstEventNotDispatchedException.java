package com.conduit.emergency.exception;

public class BurstEventNotDispatchedException extends RuntimeException {

    public BurstEventNotDispatchedException(Long eventId) {
        super("爆管事件不在已派发状态，不能改派: " + eventId);
    }
}
