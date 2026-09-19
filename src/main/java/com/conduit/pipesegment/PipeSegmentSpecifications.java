package com.conduit.pipesegment;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

final class PipeSegmentSpecifications {

    private PipeSegmentSpecifications() {
    }

    static Specification<PipeSegment> withFilters(UtilityType utilityType, PipeSegmentStatus status,
                                                  Double minLongitude, Double minLatitude,
                                                  Double maxLongitude, Double maxLatitude) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (utilityType != null) {
                predicates.add(cb.equal(root.get("utilityType"), utilityType));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (minLongitude != null) {
                predicates.add(overlaps(cb,
                        root.get("startLongitude"), root.get("endLongitude"), minLongitude, maxLongitude));
                predicates.add(overlaps(cb,
                        root.get("startLatitude"), root.get("endLatitude"), minLatitude, maxLatitude));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static Predicate overlaps(jakarta.persistence.criteria.CriteriaBuilder cb,
                                      jakarta.persistence.criteria.Expression<Double> start,
                                      jakarta.persistence.criteria.Expression<Double> end,
                                      double min, double max) {
        return cb.not(cb.or(
                cb.and(cb.lessThan(start, min), cb.lessThan(end, min)),
                cb.and(cb.greaterThan(start, max), cb.greaterThan(end, max))
        ));
    }
}
