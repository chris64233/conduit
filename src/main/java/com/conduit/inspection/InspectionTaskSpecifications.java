package com.conduit.inspection;

import org.springframework.data.jpa.domain.Specification;

final class InspectionTaskSpecifications {

    private InspectionTaskSpecifications() {
    }

    static Specification<InspectionTask> hasInspector(String inspector) {
        return (root, query, cb) -> inspector == null ? null : cb.equal(root.get("inspector"), inspector);
    }

    static Specification<InspectionTask> hasStatus(InspectionTaskStatus status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }
}
