package com.carlink.vehicle.controller;

import com.carlink.common.dto.MessageResponse;
import com.carlink.common.exception.UnauthorizedException;
import com.carlink.security.principal.UserPrincipal;
import com.carlink.vehicle.dto.CreateVehicleRequest;
import com.carlink.vehicle.dto.UpdateVehicleRequest;
import com.carlink.vehicle.dto.VehicleResponse;
import com.carlink.vehicle.service.VehicleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Owner-facing vehicle CRUD. Every endpoint resolves the acting user from the
 * JWT and scopes access to their own vehicles.
 */
@RestController
@RequestMapping("/api/v1/vehicles")
@RequiredArgsConstructor
@Tag(name = "Vehicles")
public class VehicleController {

    private final VehicleService vehicleService;

    @GetMapping
    @Operation(summary = "List own vehicles (optionally filtered by status)")
    public List<VehicleResponse> list(@AuthenticationPrincipal UserPrincipal principal,
                                      @RequestParam(required = false) String status) {
        return vehicleService.list(requireId(principal), status);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one of own vehicles")
    public VehicleResponse get(@AuthenticationPrincipal UserPrincipal principal,
                               @PathVariable UUID id) {
        return vehicleService.get(requireId(principal), id);
    }

    @PostMapping
    @Operation(summary = "Register a new vehicle")
    public VehicleResponse create(@AuthenticationPrincipal UserPrincipal principal,
                                  @Valid @RequestBody CreateVehicleRequest request) {
        return vehicleService.create(requireId(principal), request);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update own vehicle details")
    public VehicleResponse update(@AuthenticationPrincipal UserPrincipal principal,
                                  @PathVariable UUID id,
                                  @Valid @RequestBody UpdateVehicleRequest request) {
        return vehicleService.update(requireId(principal), id, request);
    }

    @PostMapping("/{id}/archive")
    @Operation(summary = "Archive a vehicle (keeps history, removes from active list)")
    public MessageResponse archive(@AuthenticationPrincipal UserPrincipal principal,
                                   @PathVariable UUID id) {
        vehicleService.archive(requireId(principal), id);
        return new MessageResponse("Vehicle archived");
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a vehicle and its QR history")
    public MessageResponse delete(@AuthenticationPrincipal UserPrincipal principal,
                                  @PathVariable UUID id) {
        vehicleService.delete(requireId(principal), id);
        return new MessageResponse("Vehicle deleted");
    }

    private UUID requireId(UserPrincipal principal) {
        if (principal == null) {
            throw new UnauthorizedException("Authentication required");
        }
        return principal.id();
    }
}
