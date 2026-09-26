package com.conduit.emergency.dto;

import com.conduit.emergency.ResourceTransferReversalRecord;

import java.time.Instant;
import java.util.List;

public record ResourceTransferReversalResponse(
        Long id,
        String requestNo,
        Long transferId,
        Long sourceEventId,
        Long targetEventId,
        List<String> resourceCodes,
        String operator,
        String reason,
        Instant operatedAt
) {

    public static ResourceTransferReversalResponse from(ResourceTransferReversalRecord record) {
        return new ResourceTransferReversalResponse(
                record.getId(),
                record.getRequestNo(),
                record.getTransfer().getId(),
                record.getTransfer().getSourceEvent().getId(),
                record.getTransfer().getTargetEvent().getId(),
                record.getTransfer().getResourceCodes(),
                record.getOperator(),
                record.getReason(),
                record.getOperatedAt()
        );
    }
}
