package com.conduit.hazard.exception;

public class InspectionTaskNotCompletedException extends RuntimeException {

    public InspectionTaskNotCompletedException(Long taskId) {
        super("巡检任务尚未完成，不能上报隐患: " + taskId);
    }
}
