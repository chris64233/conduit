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

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@Entity
@Table(name = "burst_event_reassignments")
public class ReassignmentRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "burst_event_id", nullable = false)
    private BurstEvent burstEvent;

    @Column(name = "operator_name", nullable = false, length = 64)
    private String operator;

    @Column(nullable = false, length = 1024)
    private String reason;

    @Column(nullable = false)
    private Instant operatedAt;

    @Column(name = "previous_resource_ids", nullable = false, length = 1024)
    private String previousResourceIds;

    @Column(name = "new_resource_ids", nullable = false, length = 1024)
    private String newResourceIds;

    protected ReassignmentRecord() {
    }

    public ReassignmentRecord(BurstEvent burstEvent, String operator, String reason,
                              List<Long> previousResourceIds, List<Long> newResourceIds) {
        this.burstEvent = burstEvent;
        this.operator = operator;
        this.reason = reason;
        this.operatedAt = Instant.now();
        this.previousResourceIds = join(previousResourceIds);
        this.newResourceIds = join(newResourceIds);
    }

    public Long getId() {
        return id;
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

    public List<Long> getPreviousResourceIds() {
        return split(previousResourceIds);
    }

    public List<Long> getNewResourceIds() {
        return split(newResourceIds);
    }

    private static String join(List<Long> ids) {
        StringBuilder builder = new StringBuilder();
        for (Long id : ids) {
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(id);
        }
        return builder.toString();
    }

    private static List<Long> split(String ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(ids.split(",")).map(Long::valueOf).toList();
    }
}
