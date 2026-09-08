package com.carlink.admin.service;

import com.carlink.admin.model.AuditLog;
import com.carlink.admin.repository.AuditLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock private AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private AuditLogService service;

    @BeforeEach
    void setUp() {
        service = new AuditLogService(auditLogRepository, objectMapper);
    }

    @Test
    void recordSerializesDetailsToJsonAndSaves() {
        UUID actorId = UUID.randomUUID();

        service.record("USER_ACTIVATE", "USER", UUID.randomUUID(), actorId,
                "127.0.0.1", "curl/8", Map.of("action", "activate"));

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog saved = captor.getValue();
        assertThat(saved.getAction()).isEqualTo("USER_ACTIVATE");
        assertThat(saved.getEntityType()).isEqualTo("USER");
        assertThat(saved.getActor()).isNotNull();
        assertThat(saved.getActor().getId()).isEqualTo(actorId);
        assertThat(saved.getIpAddress()).isEqualTo("127.0.0.1");
        assertThat(saved.getDetails()).isEqualTo("{\"action\":\"activate\"}");
    }

    @Test
    void recordSwallowsSaveFailures() {
        doThrow(new RuntimeException("disk full"))
                .when(auditLogRepository).save(any());

        service.record("X", "Y", null, null, null, null, Map.of("k", "v"));
        // No exception propagates; that is the whole assertion.
    }

    @Test
    void listCapsLimitTo500AndFilters() {
        var some = new AuditLogResponseFixture().auditLog();
        when(auditLogRepository.search(eq("A"), eq("USER"), any()))
                .thenReturn(List.of(some));

        var result = service.list("A", "USER", 9999);

        assertThat(result).hasSize(1);
        verify(auditLogRepository).search(eq("A"), eq("USER"), eq(PageRequest.of(0, 500)));
    }

    /** Stand-in so the test does not depend on JPA-mapped entities. */
    private static final class AuditLogResponseFixture {
        AuditLog auditLog() {
            return AuditLog.of(null, "A", "USER", null, "127.0.0.1", null,
                    "{\"action\":\"activate\"}", java.time.Instant.now());
        }
    }
}