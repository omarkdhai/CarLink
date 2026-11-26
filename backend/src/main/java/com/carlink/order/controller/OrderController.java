package com.carlink.order.controller;

import com.carlink.common.exception.UnauthorizedException;
import com.carlink.order.dto.MyOrderResponse;
import com.carlink.order.service.OrderService;
import com.carlink.security.principal.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The signed-in owner's own sticker purchases. */
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Tag(name = "Orders")
public class OrderController {

    private final OrderService orderService;

    @GetMapping("/me")
    @Operation(summary = "Your sticker purchases (orders placed with your verified e-mail, or whose stickers you activated)")
    public List<MyOrderResponse> listMine(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            throw new UnauthorizedException("Authentication required");
        }
        return orderService.listForAccount(principal.id());
    }
}
