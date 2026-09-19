package com.conduit.pipesegment;

import org.springframework.data.jpa.domain.Specification;

final class PipeSegmentSpecifications {

    private PipeSegmentSpecifications() {
    }

    static Specification<PipeSegment> hasUtilityType(UtilityType utilityType) {
        return (root, query, cb) -> utilityType == null ? null : cb.equal(root.get("utilityType"), utilityType);
    }

    static Specification<PipeSegment> hasStatus(PipeSegmentStatus status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }
}
