package com.conduit.emergency;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BurstEventReassignmentRepository extends JpaRepository<BurstEventReassignment, Long> {

    List<BurstEventReassignment> findByBurstEventIdOrderByOperatedAtAscIdAsc(Long burstEventId);
}
