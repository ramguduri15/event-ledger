package com.eventledger.gateway.repository;

import com.eventledger.gateway.entity.EventRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EventRepository extends JpaRepository<EventRecord, String> {

    List<EventRecord> findByAccountIdOrderByEventTimestampAsc(String accountId);

    /**
     * Simple existence check used by the health endpoint.
     */
    @Query("SELECT COUNT(e) FROM EventRecord e")
    long countAll();
}
