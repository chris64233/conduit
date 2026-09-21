package com.conduit.hazard.exception;

import com.conduit.hazard.HazardStatus;

public class InvalidHazardStateTransitionException extends RuntimeException {

    public InvalidHazardStateTransitionException(HazardStatus from, HazardStatus to) {
        super("不允许的状态流转: " + from + " -> " + to);
    }
}
