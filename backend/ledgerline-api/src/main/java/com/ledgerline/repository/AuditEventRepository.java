package com.ledgerline.repository;

import com.ledgerline.entity.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {
    List<AuditEvent> findByAccountIdOrderByCreatedAtDesc(Long accountId);
    List<AuditEvent> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<AuditEvent> findByEventTypeOrderByCreatedAtDesc(String eventType);
}

