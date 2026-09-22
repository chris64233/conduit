package com.conduit.emergency;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ResourceTransferRecordRepository extends JpaRepository<ResourceTransferRecord, Long> {

    @Query("select record from ResourceTransferRecord record "
            + "where record.sourceEvent.id = :eventId or record.targetEvent.id = :eventId "
            + "order by record.operatedAt asc, record.id asc")
    List<ResourceTransferRecord> findRelatedByEventId(@Param("eventId") Long eventId);
}
