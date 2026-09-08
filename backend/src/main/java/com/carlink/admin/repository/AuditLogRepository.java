package com.carlink.admin.repository;

import com.carlink.admin.model.AuditLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Immutable audit-trail persistence. Writes happen inside admin mutations;
 * reads are admin-only and always capped by a {@link Pageable}.
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    /**
     * Newest-first audit rows, optionally filtered by action and/or entity
     * type. The caller supplies a capped {@link Pageable} so no code path can
     * ever request the whole table.
     */
    @Query("""
            select a from AuditLog a
            where (:action is null or a.action = :action)
              and (:entityType is null or a.entityType = :entityType)
            order by a.createdAt desc""")
    List<AuditLog> search(@Param("action") String action,
                          @Param("entityType") String entityType,
                          Pageable pageable);
}