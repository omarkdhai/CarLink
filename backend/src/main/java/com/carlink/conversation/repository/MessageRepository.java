package com.carlink.conversation.repository;

import com.carlink.conversation.model.Message;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Message persistence.
 */
public interface MessageRepository extends JpaRepository<Message, UUID> {
}