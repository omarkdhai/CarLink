package com.carlink.notification.contact;

import com.carlink.conversation.model.Channel;

/**
 * A visitor's message about to be delivered to the vehicle owner over a
 * channel. Internal only — never serialized to a REST DTO, and the owner
 * phone is passed straight to the sender and never logged or echoed.
 */
public record ContactDelivery(Channel channel, String ownerPhone, String message) {
}
