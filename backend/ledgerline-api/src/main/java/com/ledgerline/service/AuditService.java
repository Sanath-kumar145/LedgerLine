package com.ledgerline.service;

import com.ledgerline.entity.AuditEvent;

public interface AuditService {

    AuditEvent recordEvent(String eventType, Long userId, Long accountId, String details, String ipAddress);
}

