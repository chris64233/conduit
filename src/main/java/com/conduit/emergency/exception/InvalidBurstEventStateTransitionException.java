package com.conduit.emergency.exception;

import com.conduit.emergency.BurstStatus;

public class InvalidBurstEventStateTransitionException extends RuntimeException {

    public InvalidBurstEventStateTransitionException(BurstStatus from, BurstStatus to) {
        super("不允许的状态流转: " + from + " -> " + to);
    }
}
