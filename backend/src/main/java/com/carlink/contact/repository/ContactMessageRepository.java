package com.carlink.contact.repository;

import com.carlink.contact.model.ContactMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ContactMessageRepository extends JpaRepository<ContactMessage, UUID> {
    Page<ContactMessage> findAll(Pageable pageable);

    Page<ContactMessage> findByNameContainingIgnoreCaseOrEmailContainingIgnoreCaseOrReasonContainingIgnoreCaseOrNoteContainingIgnoreCase(
            String name, String email, String reason, String note, Pageable pageable);
}
