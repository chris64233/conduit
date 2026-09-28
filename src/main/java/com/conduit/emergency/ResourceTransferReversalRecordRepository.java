package com.conduit.emergency;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ResourceTransferReversalRecordRepository
        extends JpaRepository<ResourceTransferReversalRecord, Long> {

    @Query("SELECT reversal FROM ResourceTransferReversalRecord reversal "
            + "JOIN FETCH reversal.transfer transfer "
            + "WHERE transfer.id = :transferId "
            + "ORDER BY reversal.operatedAt ASC, reversal.id ASC")
    List<ResourceTransferReversalRecord> findByTransferIdOrderByOperatedAtAsc(@Param("transferId") Long transferId);

    @Query("SELECT reversal FROM ResourceTransferReversalRecord reversal "
            + "JOIN FETCH reversal.transfer transfer "
            + "JOIN FETCH transfer.sourceEvent source "
            + "JOIN FETCH transfer.targetEvent target "
            + "WHERE reversal.requestNo = :requestNo")
    Optional<ResourceTransferReversalRecord> findDetailByRequestNo(@Param("requestNo") String requestNo);

    @Query("SELECT reversal FROM ResourceTransferReversalRecord reversal "
            + "JOIN FETCH reversal.transfer transfer "
            + "WHERE transfer.sourceEvent.id = :eventId OR transfer.targetEvent.id = :eventId "
            + "ORDER BY reversal.operatedAt ASC, reversal.id ASC")
    List<ResourceTransferReversalRecord> findByEventIdOrderByOperatedAtAsc(@Param("eventId") Long eventId);
}
