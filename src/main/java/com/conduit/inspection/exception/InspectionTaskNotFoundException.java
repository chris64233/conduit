package com.conduit.inspection.exception;

public class InspectionTaskNotFoundException extends RuntimeException {

    public InspectionTaskNotFoundException(Long id) {
        super("巡检任务不存在: " + id);
    }
}
