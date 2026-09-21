package com.conduit.hazard;

import org.springframework.data.jpa.domain.Specification;

final class HazardSpecifications {

    private HazardSpecifications() {
    }

    static Specification<Hazard> hasLevel(HazardLevel level) {
        return (root, query, cb) -> level == null ? null : cb.equal(root.get("level"), level);
    }

    static Specification<Hazard> hasStatus(HazardStatus status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }
}
