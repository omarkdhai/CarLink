package com.carlink.order.controller;

import com.carlink.order.dto.AdminOrderView;
import com.carlink.order.service.OrderService;
import com.carlink.order.model.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/orders")
@PreAuthorize("hasRole('ADMIN')")
public class AdminOrderController {

    private final OrderService orderService;

    public AdminOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public ResponseEntity<Page<AdminOrderView>> list(@RequestParam(required = false) String q, @RequestParam(required = false) String status, Pageable pageable) {
        return ResponseEntity.ok(orderService.listOrders(q, status, pageable));
    }

    @PatchMapping("/{reference}/status")
    public ResponseEntity<Void> updateStatus(@PathVariable String reference, @RequestBody Map<String,String> body) {
        orderService.updateStatus(reference, OrderStatus.valueOf(body.get("status")));
        return ResponseEntity.noContent().build();
    }
}
