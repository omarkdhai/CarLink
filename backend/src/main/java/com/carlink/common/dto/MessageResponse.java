package com.carlink.common.dto;

/**
 * Simple success payload for actions that return no data.
 */
public record MessageResponse(String message) {

    public static MessageResponse of(String message) {
        return new MessageResponse(message);
    }
}