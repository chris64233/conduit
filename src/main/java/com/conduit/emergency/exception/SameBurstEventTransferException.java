package com.conduit.emergency.exception;

public class SameBurstEventTransferException extends RuntimeException {

    public SameBurstEventTransferException(Long eventId) {
        super("源事件与目标事件必须不同: " + eventId);
    }
}
