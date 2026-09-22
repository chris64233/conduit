package com.conduit.emergency.exception;

public class DuplicateRequestNoConflictException extends ResourceTransferConflictException {

    public DuplicateRequestNoConflictException(String requestNo) {
        super("业务请求号已用于内容不同的转移请求: " + requestNo);
    }
}
