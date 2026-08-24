package com.jperez.lgsstorecrm.employee;

import com.jperez.lgsstorecrm.common.exception.TenantNotFoundException;
import com.jperez.lgsstorecrm.employee.dto.CreateEmployeeRequest;
import com.jperez.lgsstorecrm.employee.dto.EmployeeResponse;
import com.jperez.lgsstorecrm.tenant.Tenant;
import com.jperez.lgsstorecrm.tenant.TenantRepository;
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
@RequestMapping("/api/tenants/{tenantId}/employees")
public class EmployeeController {

    private final EmployeeRepository employeeRepository;
    private final TenantRepository tenantRepository;

    public EmployeeController(EmployeeRepository employeeRepository, TenantRepository tenantRepository) {
        this.employeeRepository = employeeRepository;
        this.tenantRepository = tenantRepository;
    }

    @PostMapping
    public ResponseEntity<EmployeeResponse> createEmployee(@PathVariable UUID tenantId,
                                                           @Valid @RequestBody CreateEmployeeRequest request) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException(tenantId));

        Employee employee = new Employee(request.getFirstName(), request.getLastName(), request.getRole());
        employee.setTenant(tenant);
        Employee saved = employeeRepository.save(employee);
        return ResponseEntity.status(HttpStatus.CREATED).body(new EmployeeResponse(saved));
    }

    @GetMapping
    public ResponseEntity<Page<EmployeeResponse>> listEmployees(
            @PathVariable UUID tenantId,
            @ParameterObject
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        Page<EmployeeResponse> employees = employeeRepository.findByTenantId(tenantId, pageable)
                .map(EmployeeResponse::new);
        return ResponseEntity.ok(employees);
    }
}
