package com.conduit.emergency.exception;

import com.conduit.emergency.BurstEventStatus;

public class InvalidBurstEventStateTransitionException extends RuntimeException {

    public InvalidBurstEventStateTransitionException(BurstEventStatus from, BurstEventStatus to) {
        super("不允许的状态流转: " + from + " -> " + to);
    }
}
