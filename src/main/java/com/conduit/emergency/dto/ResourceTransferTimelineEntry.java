package com.conduit.emergency.dto;

import com.conduit.emergency.ResourceTransferRecord;
import com.conduit.emergency.ResourceTransferReversalRecord;

import java.time.Instant;
import java.util.List;

public record ResourceTransferTimelineEntry(
        String type,
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

    public static final String TYPE_TRANSFER = "TRANSFER";
    public static final String TYPE_REVERSAL = "REVERSAL";

    public static ResourceTransferTimelineEntry fromTransfer(ResourceTransferRecord record) {
        return new ResourceTransferTimelineEntry(
                TYPE_TRANSFER,
                record.getId(),
                record.getRequestNo(),
                record.getId(),
                record.getSourceEvent().getId(),
                record.getTargetEvent().getId(),
                record.getResourceCodes(),
                record.getOperator(),
                record.getReason(),
                record.getOperatedAt()
        );
    }

    public static ResourceTransferTimelineEntry fromReversal(ResourceTransferReversalRecord record) {
        ResourceTransferRecord transfer = record.getTransfer();
        return new ResourceTransferTimelineEntry(
                TYPE_REVERSAL,
                record.getId(),
                record.getRequestNo(),
                transfer.getId(),
                transfer.getSourceEvent().getId(),
                transfer.getTargetEvent().getId(),
                transfer.getResourceCodes(),
                record.getOperator(),
                record.getReason(),
                record.getOperatedAt()
        );
    }
}
