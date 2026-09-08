package com.carlink.admin.repository;

import com.carlink.admin.model.Report;
import com.carlink.admin.model.ReportStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Moderation-report persistence. Reads are admin-only; the public flow writes.
 *
 * <p>Both list queries eagerly fetch {@code conversation} and its
 * {@code vehicle}: {@link ReportCsvExporter} renders detached rows outside any
 * transaction — without the {@code @EntityGraph}, {@code report.getConversation()}
 * would be an uninitialized lazy proxy and the CSV export would 500 with a
 * {@code LazyInitializationException}.</p>
 */
public interface ReportRepository extends JpaRepository<Report, UUID> {

    @EntityGraph(attributePaths = {"conversation", "conversation.vehicle"})
    List<Report> findAllByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = {"conversation", "conversation.vehicle"})
    List<Report> findAllByStatusOrderByCreatedAtDesc(ReportStatus status);

    long countByStatus(ReportStatus status);
}