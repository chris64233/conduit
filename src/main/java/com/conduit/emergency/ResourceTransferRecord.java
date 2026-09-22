package com.conduit.emergency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@Entity
@Table(name = "burst_event_resource_transfers", uniqueConstraints = {
        @UniqueConstraint(name = "uk_resource_transfer_request_no", columnNames = "request_no")
})
public class ResourceTransferRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_no", nullable = false, length = 64)
    private String requestNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_event_id", nullable = false)
    private BurstEvent sourceEvent;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_event_id", nullable = false)
    private BurstEvent targetEvent;

    @Column(name = "resource_codes", nullable = false, length = 2048)
    private String resourceCodes;

    @Column(name = "operator_name", nullable = false, length = 64)
    private String operator;

    @Column(nullable = false, length = 1024)
    private String reason;

    @Column(name = "operated_at", nullable = false)
    private Instant operatedAt;

    protected ResourceTransferRecord() {
    }

    public ResourceTransferRecord(String requestNo, BurstEvent sourceEvent, BurstEvent targetEvent,
                                  List<String> resourceCodes, String operator, String reason) {
        this.requestNo = requestNo;
        this.sourceEvent = sourceEvent;
        this.targetEvent = targetEvent;
        this.resourceCodes = join(resourceCodes);
        this.operator = operator;
        this.reason = reason;
        this.operatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getRequestNo() {
        return requestNo;
    }

    public BurstEvent getSourceEvent() {
        return sourceEvent;
    }

    public BurstEvent getTargetEvent() {
        return targetEvent;
    }

    public List<String> getResourceCodes() {
        return split(resourceCodes);
    }

    public String getOperator() {
        return operator;
    }

    public String getReason() {
        return reason;
    }

    public Instant getOperatedAt() {
        return operatedAt;
    }

    private static String join(List<String> codes) {
        return String.join(",", codes);
    }

    private static List<String> split(String codes) {
        if (codes == null || codes.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(codes.split(",")).toList();
    }
}
