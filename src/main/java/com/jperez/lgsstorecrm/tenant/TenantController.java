package com.jperez.lgsstorecrm.tenant;

import com.jperez.lgsstorecrm.common.exception.TenantNotFoundException;
import com.jperez.lgsstorecrm.tenant.dto.CreateTenantRequest;
import com.jperez.lgsstorecrm.tenant.dto.TenantResponse;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/tenants")
public class TenantController {

    private final TenantRepository tenantRepository;

    public TenantController(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    @PostMapping
    public ResponseEntity<TenantResponse> createTenant(@Valid @RequestBody CreateTenantRequest request) {
        Tenant tenant = new Tenant(request.getName());
        Tenant saved = tenantRepository.save(tenant);
        return ResponseEntity.status(HttpStatus.CREATED).body(new TenantResponse(saved));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TenantResponse> getTenant(@PathVariable UUID id) {
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new TenantNotFoundException(id));
        return ResponseEntity.ok(new TenantResponse(tenant));
    }

    @GetMapping
    public ResponseEntity<Page<TenantResponse>> listTenants(
            @ParameterObject
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        Page<TenantResponse> tenants = tenantRepository.findAll(pageable).map(TenantResponse::new);
        return ResponseEntity.ok(tenants);
    }
}
