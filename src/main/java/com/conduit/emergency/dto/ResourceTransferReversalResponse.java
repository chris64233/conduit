package com.conduit.emergency.dto;

import com.conduit.emergency.ResourceTransferRecord;
import com.conduit.emergency.ResourceTransferReversalRecord;

import java.time.Instant;
import java.util.List;

public record ResourceTransferReversalResponse(
        Long id,
        String requestNo,
        Long transferId,
        String transferRequestNo,
        Long sourceEventId,
        Long targetEventId,
        List<String> resourceCodes,
        String operator,
        String reason,
        Instant operatedAt
) {

    public static ResourceTransferReversalResponse from(ResourceTransferReversalRecord record) {
        ResourceTransferRecord transfer = record.getTransfer();
        return new ResourceTransferReversalResponse(
                record.getId(),
                record.getRequestNo(),
                transfer.getId(),
                transfer.getRequestNo(),
                transfer.getSourceEvent().getId(),
                transfer.getTargetEvent().getId(),
                transfer.getResourceCodes(),
                record.getOperator(),
                record.getReason(),
                record.getOperatedAt()
        );
    }
}
