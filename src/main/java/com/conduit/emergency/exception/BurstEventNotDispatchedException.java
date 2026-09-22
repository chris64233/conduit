package com.conduit.emergency.exception;

import com.conduit.emergency.BurstEventStatus;

public class BurstEventNotDispatchedException extends RuntimeException {

    public BurstEventNotDispatchedException(Long eventId, BurstEventStatus status) {
        super("仅已派发状态的事件允许改派，事件 " + eventId + " 当前状态: " + status);
    }
}
