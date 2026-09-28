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

/**
 * 部分撤销回迁明细：一个资源在同一笔转移下只能回迁一次。
 */
@Entity
@Table(name = "burst_event_resource_transfer_reversal_items", uniqueConstraints = {
        @UniqueConstraint(name = "uk_transfer_reversal_item",
                columnNames = {"transfer_id", "resource_code_key"})
})
public class ResourceTransferReversalItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transfer_id", nullable = false)
    private ResourceTransferRecord transfer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reversal_id", nullable = false)
    private ResourceTransferReversalRecord reversal;

    @Column(name = "resource_code", nullable = false, length = 64)
    private String resourceCode;

    @Column(name = "resource_code_key", nullable = false, length = 64)
    private String resourceCodeKey;

    protected ResourceTransferReversalItem() {
    }

    public ResourceTransferReversalItem(ResourceTransferReversalRecord reversal,
                                        String resourceCode, String resourceCodeKey) {
        this.reversal = reversal;
        this.transfer = reversal.getTransfer();
        this.resourceCode = resourceCode;
        this.resourceCodeKey = resourceCodeKey;
    }

    public Long getId() {
        return id;
    }

    public ResourceTransferReversalRecord getReversal() {
        return reversal;
    }

    public ResourceTransferRecord getTransfer() {
        return transfer;
    }

    public String getResourceCode() {
        return resourceCode;
    }

    public String getResourceCodeKey() {
        return resourceCodeKey;
    }
}
