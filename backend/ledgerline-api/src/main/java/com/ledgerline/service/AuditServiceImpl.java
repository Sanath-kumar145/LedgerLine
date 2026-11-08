package com.ledgerline.service;

import com.ledgerline.entity.AuditEvent;
import com.ledgerline.repository.AuditEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditServiceImpl implements AuditService {

    private final AuditEventRepository auditEventRepository;

    public AuditServiceImpl(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditEvent recordEvent(String eventType, Long userId, Long accountId, String details, String ipAddress) {
        return auditEventRepository.save(new AuditEvent(eventType, userId, accountId, details, ipAddress));
    }
}

