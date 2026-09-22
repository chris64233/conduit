package com.conduit.emergency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "resource_transfer_requests")
public class ResourceTransferRequest {

    @Id
    @Column(name = "request_no", nullable = false, length = 64)
    private String requestNo;

    @Column(name = "content_fingerprint", nullable = false, length = 64)
    private String contentFingerprint;

    @Column(name = "record_id", nullable = false)
    private Long recordId;

    @Column(name = "source_event_id", nullable = false)
    private Long sourceEventId;

    @Column(name = "target_event_id", nullable = false)
    private Long targetEventId;

    @Column(name = "resource_codes", nullable = false, length = 2048)
    private String resourceCodes;

    protected ResourceTransferRequest() {
    }

    public ResourceTransferRequest(String requestNo, String contentFingerprint, Long recordId,
                                   Long sourceEventId, Long targetEventId, String resourceCodes) {
        this.requestNo = requestNo;
        this.contentFingerprint = contentFingerprint;
        this.recordId = recordId;
        this.sourceEventId = sourceEventId;
        this.targetEventId = targetEventId;
        this.resourceCodes = resourceCodes;
    }

    public String getRequestNo() {
        return requestNo;
    }

    public String getContentFingerprint() {
        return contentFingerprint;
    }

    public Long getRecordId() {
        return recordId;
    }

    public Long getSourceEventId() {
        return sourceEventId;
    }

    public Long getTargetEventId() {
        return targetEventId;
    }

    public String getResourceCodes() {
        return resourceCodes;
    }
}
