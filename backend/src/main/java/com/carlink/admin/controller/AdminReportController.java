package com.carlink.admin.controller;

import com.carlink.admin.dto.AdminActor;
import com.carlink.admin.dto.ReportDetailResponse;
import com.carlink.admin.dto.ReportStatusRequest;
import com.carlink.admin.dto.ReportSummaryResponse;
import com.carlink.admin.model.ReportStatus;
import com.carlink.admin.service.ReportCsvExporter;
import com.carlink.admin.service.ReportService;
import com.carlink.common.dto.MessageResponse;
import com.carlink.common.exception.UnauthorizedException;
import com.carlink.security.principal.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Admin moderation of visitor reports, plus CSV export for analytics.
 * Access is enforced by the {@code ROLE_ADMIN} URL matcher.
 */
@RestController
@RequestMapping("/api/v1/admin/reports")
@RequiredArgsConstructor
@Tag(name = "Admin reports")
public class AdminReportController {

    private final ReportService reportService;
    private final ReportCsvExporter reportCsvExporter;

    @GetMapping
    @Operation(summary = "List moderation reports, optionally filtered by status")
    public List<ReportSummaryResponse> list(@AuthenticationPrincipal UserPrincipal principal,
                                            @RequestParam(required = false) ReportStatus status) {
        requireId(principal);
        return reportService.list(status);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one report with conversation context (admin-only message content)")
    public ReportDetailResponse get(@AuthenticationPrincipal UserPrincipal principal,
                                    @PathVariable UUID id) {
        requireId(principal);
        return reportService.get(id);
    }

    @PostMapping("/{id}/status")
    @Operation(summary = "Transition a report between OPEN/REVIEWED/CLOSED")
    public MessageResponse changeStatus(@AuthenticationPrincipal UserPrincipal principal,
                                        @PathVariable UUID id,
                                        @Valid @RequestBody ReportStatusRequest body,
                                        HttpServletRequest request) {
        reportService.changeStatus(id, body.status(), actor(principal, request));
        return MessageResponse.of("Report marked " + body.status().name().toLowerCase());
    }

    @GetMapping("/export")
    @Operation(summary = "Download all reports as CSV")
    public ResponseEntity<byte[]> export(@AuthenticationPrincipal UserPrincipal principal,
                                         @RequestParam(required = false) ReportStatus status) {
        requireId(principal);
        byte[] csv = reportCsvExporter.toCsv(
                reportService.exportRows(status));
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("reports.csv").build().toString())
                .body(csv);
    }

    private AdminActor actor(UserPrincipal principal, HttpServletRequest request) {
        return new AdminActor(requireId(principal),
                request.getRemoteAddr(), request.getHeader("User-Agent"));
    }

    private UUID requireId(UserPrincipal principal) {
        if (principal == null) {
            throw new UnauthorizedException("Authentication required");
        }
        return principal.id();
    }
}