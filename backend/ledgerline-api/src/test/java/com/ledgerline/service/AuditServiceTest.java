package com.ledgerline.service;

import com.ledgerline.entity.AuditEvent;
import com.ledgerline.repository.AuditEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditService Unit Tests — Dual Transaction Boundary Verification")
class AuditServiceTest {

    @Mock
    private AuditEventRepository auditEventRepository;

    private AuditServiceImpl auditService;

    @BeforeEach
    void setUp() {
        auditService = new AuditServiceImpl(auditEventRepository);
    }

    @Test
    @DisplayName("recordConsistentEvent persists audit event with accurate fields")
    void testRecordConsistentEvent() {
        when(auditEventRepository.save(any(AuditEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        AuditEvent event = auditService.recordConsistentEvent(
                "TRANSFER_COMPLETED", 1L, 10L, "Transferred 500 INR", "192.168.1.1"
        );

        assertNotNull(event);
        assertEquals("TRANSFER_COMPLETED", event.getEventType());
        assertEquals(1L, event.getUserId());
        assertEquals(10L, event.getAccountId());
        assertEquals("Transferred 500 INR", event.getDetails());
        assertEquals("192.168.1.1", event.getIpAddress());

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventRepository).save(captor.capture());
        assertEquals("TRANSFER_COMPLETED", captor.getValue().getEventType());
    }

    @Test
    @DisplayName("recordAutonomousEvent persists refusal/security audit event")
    void testRecordAutonomousEvent() {
        when(auditEventRepository.save(any(AuditEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        AuditEvent event = auditService.recordAutonomousEvent(
                "TRANSFER_REFUSED_INSUFFICIENT_FUNDS", 1L, 10L, "Insufficient funds", "192.168.1.1"
        );

        assertNotNull(event);
        assertEquals("TRANSFER_REFUSED_INSUFFICIENT_FUNDS", event.getEventType());
        assertEquals(1L, event.getUserId());
        assertEquals(10L, event.getAccountId());

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventRepository).save(captor.capture());
        assertEquals("TRANSFER_REFUSED_INSUFFICIENT_FUNDS", captor.getValue().getEventType());
    }

    @Test
    @DisplayName("recordEvent delegates to consistent recording")
    void testRecordEvent() {
        when(auditEventRepository.save(any(AuditEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        AuditEvent event = auditService.recordEvent(
                "DEPOSIT_COMPLETED", 2L, 20L, "Deposited 1000 INR", "10.0.0.1"
        );

        assertNotNull(event);
        assertEquals("DEPOSIT_COMPLETED", event.getEventType());
        assertEquals(2L, event.getUserId());
        assertEquals(20L, event.getAccountId());
    }
}

