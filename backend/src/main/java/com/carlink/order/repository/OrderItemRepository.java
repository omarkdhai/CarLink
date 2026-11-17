package com.carlink.order.repository;

import com.carlink.order.model.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {

    List<OrderItem> findAllByOrderId(UUID orderId);

    /** Admin list: items of a whole page of orders in one query. */
    List<OrderItem> findAllByOrderIdIn(Collection<UUID> orderIds);
}
