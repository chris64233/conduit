package com.conduit.inspection.exception;

import com.conduit.inspection.InspectionStatus;

public class InvalidStateTransitionException extends RuntimeException {

    public InvalidStateTransitionException(InspectionStatus from, InspectionStatus to) {
        super("不允许的状态流转: " + from + " -> " + to);
    }
}
