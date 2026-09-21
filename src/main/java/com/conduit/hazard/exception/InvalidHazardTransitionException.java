package com.conduit.hazard.exception;

import com.conduit.hazard.HazardStatus;

public class InvalidHazardTransitionException extends RuntimeException {

    public InvalidHazardTransitionException(HazardStatus from, HazardStatus to) {
        super("不允许的隐患状态流转: " + from + " -> " + to);
    }
}
