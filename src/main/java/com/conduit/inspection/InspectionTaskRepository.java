package com.conduit.inspection;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface InspectionTaskRepository extends JpaRepository<InspectionTask, Long>,
        JpaSpecificationExecutor<InspectionTask> {
}
