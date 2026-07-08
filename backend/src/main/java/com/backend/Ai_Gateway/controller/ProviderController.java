package com.backend.Ai_Gateway.controller;

import com.backend.Ai_Gateway.entity.Provider;
import com.backend.Ai_Gateway.services.ProviderService;
import com.backend.Ai_Gateway.services.dto.UpdateProviderRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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
    public Provider create(@RequestBody Provider provider) {
        return providerService.createProvider(provider);
    }

    @PutMapping("/{id}")
    public Provider update(@PathVariable Long id, @Valid @RequestBody UpdateProviderRequest request) {
        return providerService.updateProvider(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        providerService.deleteProvider(id);
    }

    @PatchMapping("/{id}/status")
    public Provider toggleStatus(@PathVariable Long id) {
        return providerService.changeStatus(id);
    }
}
