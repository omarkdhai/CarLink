package com.carlink.admin.controller;

import com.carlink.admin.dto.AdminActor;
import com.carlink.admin.dto.ReportStatusRequest;
import com.carlink.admin.model.Report;
import com.carlink.admin.model.ReportStatus;
import com.carlink.admin.service.ReportCsvExporter;
import com.carlink.admin.service.ReportService;
import com.carlink.common.dto.MessageResponse;
import com.carlink.common.exception.UnauthorizedException;
import com.carlink.security.principal.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminReportControllerTest {

    private static final UUID ADMIN_ID = UUID.randomUUID();

    @Mock private ReportService reportService;
    @Mock private ReportCsvExporter reportCsvExporter;
    @Mock private HttpServletRequest request;

    private AdminReportController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminReportController(reportService, reportCsvExporter);
    }

    @Test
    void changeStatusPassesActor() {
        UUID reportId = UUID.randomUUID();
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getHeader("User-Agent")).thenReturn("admin-ui");

        MessageResponse response = controller.changeStatus(principal(), reportId,
                new ReportStatusRequest(ReportStatus.REVIEWED), request);

        assertThat(response.message()).isEqualTo("Report marked reviewed");
        verify(reportService).changeStatus(reportId, ReportStatus.REVIEWED,
                new AdminActor(ADMIN_ID, "127.0.0.1", "admin-ui"));
    }

    @Test
    void exportReturnsTextCsvWithAttachmentFilename() {
        List<Report> rows = List.of(new ReportFixture().report());
        byte[] csv = "id\r\n".getBytes();
        when(reportService.exportRows(ReportStatus.OPEN)).thenReturn(rows);
        when(reportCsvExporter.toCsv(rows)).thenReturn(csv);

        ResponseEntity<byte[]> response = controller.export(principal(), ReportStatus.OPEN);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getHeaders().getContentType().toString())
                .startsWith("text/csv");
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .contains("attachment").contains("reports.csv");
        assertThat(response.getBody()).isEqualTo(csv);
    }

    @Test
    void nullPrincipalThrowsUnauthorized() {
        assertThatThrownBy(() -> controller.list(null, null))
                .isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> controller.get(null, UUID.randomUUID()))
                .isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> controller.export(null, null))
                .isInstanceOf(UnauthorizedException.class);
    }

    private static UserPrincipal principal() {
        return new UserPrincipal(ADMIN_ID, "admin@example.com", "ADMIN");
    }

    /** A bare Report stub so the exporter can be invoked without JPA. */
    private static final class ReportFixture {
        Report report() {
            java.time.Instant now = java.time.Instant.now();
            return Report.builder().id(UUID.randomUUID()).status(ReportStatus.OPEN)
                    .createdAt(now).build();
        }
    }
}