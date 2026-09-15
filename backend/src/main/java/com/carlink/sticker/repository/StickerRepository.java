package com.carlink.sticker.repository;

import com.carlink.sticker.model.Sticker;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Sticker persistence. Only the token hash is ever stored; lookups happen via
 * the hash, never the raw token.
 */
public interface StickerRepository extends JpaRepository<Sticker, UUID> {

    Optional<Sticker> findByTokenHash(String tokenHash);

    /**
     * Row-level write lock taken during activation so two concurrent claims of
     * the same sticker cannot both succeed.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Sticker s where s.tokenHash = :tokenHash")
    Optional<Sticker> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    List<Sticker> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId);
}