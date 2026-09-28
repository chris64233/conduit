package com.conduit.emergency.dto;

import com.conduit.emergency.BurstEvent;
import com.conduit.emergency.BurstEventLevel;
import com.conduit.emergency.BurstEventStatus;
import com.conduit.emergency.ResourceTransferRecord;
import com.conduit.emergency.ResourceTransferReversalRecord;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record BurstEventResponse(
        Long id,
        Long pipeSegmentId,
        String description,
        BurstEventLevel level,
        String reporter,
        BurstEventStatus status,
        String resolution,
        List<EmergencyResourceResponse> resources,
        List<ReassignmentRecordResponse> reassignments,
        List<ResourceTransferRecordResponse> resourceTransfers,
        List<ResourceTransferTimelineEntry> resourceTransferTimeline
) {

    public static BurstEventResponse from(BurstEvent event, List<ResourceTransferRecord> transferRecords,
                                          List<ResourceTransferReversalRecord> reversalRecords) {
        Map<Long, List<String>> reversedCodesByTransfer = new LinkedHashMap<>();
        for (ResourceTransferReversalRecord reversal : reversalRecords) {
            reversedCodesByTransfer
                    .computeIfAbsent(reversal.getTransfer().getId(), key -> new ArrayList<>())
                    .addAll(reversal.getResourceCodes());
        }
        List<ResourceTransferTimelineEntry> timeline = new ArrayList<>();
        for (ResourceTransferRecord record : transferRecords) {
            timeline.add(ResourceTransferTimelineEntry.fromTransfer(record));
        }
        for (ResourceTransferReversalRecord record : reversalRecords) {
            timeline.add(ResourceTransferTimelineEntry.fromReversal(record));
        }
        timeline.sort(Comparator.comparing(ResourceTransferTimelineEntry::operatedAt)
                .thenComparing(entry -> ResourceTransferTimelineEntry.TYPE_TRANSFER.equals(entry.type()) ? 0 : 1));
        return new BurstEventResponse(
                event.getId(),
                event.getPipeSegment().getId(),
                event.getDescription(),
                event.getLevel(),
                event.getReporter(),
                event.getStatus(),
                event.getResolution(),
                event.getResources().stream()
                        .map(EmergencyResourceResponse::from)
                        .toList(),
                event.getReassignments().stream()
                        .map(ReassignmentRecordResponse::from)
                        .toList(),
                transferRecords.stream()
                        .map(record -> ResourceTransferRecordResponse.from(
                                record, reversedCodesByTransfer.getOrDefault(record.getId(), List.of())))
                        .toList(),
                timeline
        );
    }
}
