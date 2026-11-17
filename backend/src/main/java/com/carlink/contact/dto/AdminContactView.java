package com.carlink.contact.dto;

import java.time.Instant;

public record AdminContactView(
        String id,
        String name,
        String email,
        String mobile,
        String reason,
        String note,
        Instant createdAt
) {}
