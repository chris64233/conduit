package com.conduit.hazard.exception;

public class InspectionTaskNotCompletedException extends RuntimeException {

    public InspectionTaskNotCompletedException(Long id) {
        super("巡检任务尚未完成，不允许上报隐患: " + id);
    }
}
