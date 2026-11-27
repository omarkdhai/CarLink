package com.carlink.order.controller;

import com.carlink.admin.dto.AdminActor;
import com.carlink.common.exception.UnauthorizedException;
import com.carlink.order.dto.AdminOrderStickerView;
import com.carlink.order.dto.AdminOrderView;
import com.carlink.security.principal.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

import java.util.List;
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

    /** QR codes and activation codes of an order's stickers, to print them. Audited. */
    @GetMapping("/{reference}/stickers")
    public List<AdminOrderStickerView> stickers(@AuthenticationPrincipal UserPrincipal principal,
                                                @PathVariable String reference,
                                                HttpServletRequest request) {
        if (principal == null) {
            throw new UnauthorizedException("Authentication required");
        }
        return orderService.stickerCodes(reference,
                new AdminActor(principal.id(), request.getRemoteAddr(), request.getHeader("User-Agent")));
    }

    @PatchMapping("/{reference}/status")
    public ResponseEntity<Void> updateStatus(@PathVariable String reference, @RequestBody Map<String,String> body) {
        orderService.updateStatus(reference, OrderStatus.valueOf(body.get("status")));
        return ResponseEntity.noContent().build();
    }
}
