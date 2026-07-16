package com.backend.Ai_Gateway.controller;

import com.backend.Ai_Gateway.entity.Provider;
import com.backend.Ai_Gateway.services.ProviderService;
import com.backend.Ai_Gateway.services.dto.CreateProviderRequest;
import com.backend.Ai_Gateway.services.dto.UpdateProviderRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/providers")
public class ProviderController {

    private final ProviderService providerService;

    public ProviderController(ProviderService providerService) {
        this.providerService = providerService;
    }

    @GetMapping
    public List<Provider> list() {
        return providerService.getAllProviders();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public Provider create(@Valid @RequestBody CreateProviderRequest request) {
        return providerService.createProvider(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Provider update(@PathVariable Long id, @Valid @RequestBody UpdateProviderRequest request) {
        return providerService.updateProvider(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable Long id) {
        providerService.deleteProvider(id);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public Provider toggleStatus(@PathVariable Long id) {
        return providerService.changeStatus(id);
    }
}
