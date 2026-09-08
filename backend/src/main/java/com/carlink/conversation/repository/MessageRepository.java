package com.carlink.conversation.repository;

import com.carlink.conversation.model.Message;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Message persistence.
 */
public interface MessageRepository extends JpaRepository<Message, UUID> {

    /** Returns all messages for a conversation in chronological order. */
    List<Message> findAllByConversation_IdOrderByCreatedAtAsc(UUID conversationId);
}