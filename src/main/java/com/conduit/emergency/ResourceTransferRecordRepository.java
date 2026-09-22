package com.conduit.emergency;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ResourceTransferRecordRepository extends JpaRepository<ResourceTransferRecord, Long> {

    Optional<ResourceTransferRecord> findByRequestNo(String requestNo);

    @Query("SELECT record FROM ResourceTransferRecord record "
            + "JOIN FETCH record.sourceEvent source "
            + "JOIN FETCH record.targetEvent target "
            + "WHERE record.requestNo = :requestNo")
    Optional<ResourceTransferRecord> findDetailByRequestNo(@Param("requestNo") String requestNo);

    @Query("SELECT record FROM ResourceTransferRecord record "
            + "WHERE record.sourceEvent.id = :eventId OR record.targetEvent.id = :eventId "
            + "ORDER BY record.operatedAt ASC, record.id ASC")
    List<ResourceTransferRecord> findByEventIdOrderByOperatedAtAsc(@Param("eventId") Long eventId);
}
