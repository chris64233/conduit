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

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Entity
@Table(name = "burst_event_reassignments")
public class BurstEventReassignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "burst_event_id", nullable = false)
    private BurstEvent burstEvent;

    @Column(nullable = false, length = 64)
    private String operator;

    @Column(nullable = false, length = 1024)
    private String reason;

    @Column(nullable = false)
    private OffsetDateTime operatedAt;

    @Column(name = "previous_resource_ids", nullable = false, length = 1024)
    private String previousResourceIds;

    @Column(name = "new_resource_ids", nullable = false, length = 1024)
    private String newResourceIds;

    protected BurstEventReassignment() {
    }

    public BurstEventReassignment(BurstEvent burstEvent, String operator, String reason,
                                  OffsetDateTime operatedAt,
                                  List<Long> previousResourceIds, List<Long> newResourceIds) {
        this.burstEvent = burstEvent;
        this.operator = operator;
        this.reason = reason;
        this.operatedAt = operatedAt;
        this.previousResourceIds = join(previousResourceIds);
        this.newResourceIds = join(newResourceIds);
    }

    public Long getId() {
        return id;
    }

    public BurstEvent getBurstEvent() {
        return burstEvent;
    }

    public String getOperator() {
        return operator;
    }

    public String getReason() {
        return reason;
    }

    public OffsetDateTime getOperatedAt() {
        return operatedAt;
    }

    public String getPreviousResourceIds() {
        return previousResourceIds;
    }

    public String getNewResourceIds() {
        return newResourceIds;
    }

    private static String join(List<Long> ids) {
        return ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }
}
