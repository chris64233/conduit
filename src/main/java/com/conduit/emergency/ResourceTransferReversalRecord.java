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
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "burst_event_resource_transfer_reversals", uniqueConstraints = {
        @UniqueConstraint(name = "uk_transfer_reversal_request_no", columnNames = "request_no"),
        @UniqueConstraint(name = "uk_transfer_reversal_transfer_id", columnNames = "transfer_id")
})
public class ResourceTransferReversalRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_no", nullable = false, length = 64)
    private String requestNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transfer_id", nullable = false)
    private ResourceTransferRecord transfer;

    @Column(name = "operator_name", nullable = false, length = 64)
    private String operator;

    @Column(nullable = false, length = 1024)
    private String reason;

    @Column(name = "operated_at", nullable = false)
    private Instant operatedAt;

    protected ResourceTransferReversalRecord() {
    }

    public ResourceTransferReversalRecord(String requestNo, ResourceTransferRecord transfer,
                                          String operator, String reason) {
        this.requestNo = requestNo;
        this.transfer = transfer;
        this.operator = operator;
        this.reason = reason;
        this.operatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public Long getId() {
        return id;
    }

    public String getRequestNo() {
        return requestNo;
    }

    public ResourceTransferRecord getTransfer() {
        return transfer;
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
}
