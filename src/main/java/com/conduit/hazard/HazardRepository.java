package com.conduit.hazard;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface HazardRepository extends JpaRepository<Hazard, Long>,
        JpaSpecificationExecutor<Hazard> {
}
