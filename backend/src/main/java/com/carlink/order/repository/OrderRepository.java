package com.carlink.order.repository;

import com.carlink.order.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    boolean existsByReference(String reference);

    Optional<Order> findByReference(String reference);
}
