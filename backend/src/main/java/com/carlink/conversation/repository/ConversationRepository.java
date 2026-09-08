package com.carlink.conversation.repository;

import com.carlink.conversation.model.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Conversation persistence. The public flow writes conversations; the owner
 * dashboard (Phase 7) reads them, always scoped by the acting owner's vehicle.
 */
public interface ConversationRepository extends JpaRepository<Conversation, UUID> {
}