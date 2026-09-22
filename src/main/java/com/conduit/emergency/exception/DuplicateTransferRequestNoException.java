package com.conduit.emergency.exception;

public class DuplicateTransferRequestNoException extends RuntimeException {

    public DuplicateTransferRequestNoException(String requestNo) {
        super("业务请求号已存在但请求内容不一致: " + requestNo);
    }
}
