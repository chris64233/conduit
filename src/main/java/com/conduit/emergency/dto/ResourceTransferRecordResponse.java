package com.conduit.emergency.dto;

import com.conduit.emergency.ResourceTransferRecord;

import java.time.Instant;
import java.util.List;

public record ResourceTransferRecordResponse(
        Long id,
        String requestNo,
        Long sourceEventId,
        Long targetEventId,
        List<String> resourceCodes,
        String operator,
        String reason,
        Instant operatedAt
) {

    public static ResourceTransferRecordResponse from(ResourceTransferRecord record) {
        return new ResourceTransferRecordResponse(
                record.getId(),
                record.getRequestNo(),
                record.getSourceEvent().getId(),
                record.getTargetEvent().getId(),
                record.getResourceCodes(),
                record.getOperator(),
                record.getReason(),
                record.getOperatedAt()
        );
    }
}
