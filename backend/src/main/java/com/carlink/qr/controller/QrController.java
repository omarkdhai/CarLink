package com.carlink.qr.controller;

import com.carlink.common.dto.MessageResponse;
import com.carlink.common.exception.UnauthorizedException;
import com.carlink.qr.dto.QrIssuedResponse;
import com.carlink.qr.dto.QrStatusResponse;
import com.carlink.qr.service.QrService;
import com.carlink.security.principal.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Owner QR management. Every action is scoped to a vehicle owned by the
 * acting user. The raw token appears only in the issue response — it must
 * never be returned again or logged.
 */
@RestController
@RequestMapping("/api/v1/vehicles/{vehicleId}/qr")
@RequiredArgsConstructor
@Tag(name = "QR codes")
public class QrController {

    private final QrService qrService;

    @PostMapping
    @Operation(summary = "Generate (or regenerate) the vehicle's QR code. "
            + "The raw token is returned exactly once; the previous QR is deactivated.")
    public QrIssuedResponse issue(@AuthenticationPrincipal UserPrincipal principal,
                                  @PathVariable UUID vehicleId) {
        return qrService.issue(requireId(principal), vehicleId);
    }

    @GetMapping
    @Operation(summary = "Status of the currently active QR (token is never returned)")
    public QrStatusResponse current(@AuthenticationPrincipal UserPrincipal principal,
                                    @PathVariable UUID vehicleId) {
        return qrService.current(requireId(principal), vehicleId);
    }

    @GetMapping("/history")
    @Operation(summary = "Full QR issuance history for the vehicle")
    public List<QrStatusResponse> history(@AuthenticationPrincipal UserPrincipal principal,
                                          @PathVariable UUID vehicleId) {
        return qrService.history(requireId(principal), vehicleId);
    }

    @PostMapping("/deactivate")
    @Operation(summary = "Deactivate the active QR (the contact channel is turned off)")
    public MessageResponse deactivate(@AuthenticationPrincipal UserPrincipal principal,
                                      @PathVariable UUID vehicleId) {
        qrService.deactivate(requireId(principal), vehicleId);
        return new MessageResponse("QR code deactivated");
    }

    private UUID requireId(UserPrincipal principal) {
        if (principal == null) {
            throw new UnauthorizedException("Authentication required");
        }
        return principal.id();
    }
}