package com.conduit.inspection.exception;

import com.conduit.inspection.InspectionTaskStatus;

public class InvalidTaskStatusTransitionException extends RuntimeException {

    public InvalidTaskStatusTransitionException(InspectionTaskStatus current, InspectionTaskStatus target) {
        super("不允许的状态流转: " + current + " -> " + target);
    }
}
