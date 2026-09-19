package com.conduit.pipesegment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PipeSegmentRepository extends JpaRepository<PipeSegment, Long>, JpaSpecificationExecutor<PipeSegment> {

    boolean existsByCodeKey(String codeKey);
}
