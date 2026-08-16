package com.jperez.lgsstorecrm.employee;

import com.jperez.lgsstorecrm.employee.dto.CreateEmployeeRequest;
import com.jperez.lgsstorecrm.employee.dto.EmployeeResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/employees")
public class EmployeeController {

    private final EmployeeRepository employeeRepository;

    public EmployeeController(EmployeeRepository employeeRepository) {
        this.employeeRepository = employeeRepository;
    }

    @PostMapping
    public ResponseEntity<EmployeeResponse> createEmployee(@Valid @RequestBody CreateEmployeeRequest request) {
        Employee employee = new Employee(request.getFirstName(), request.getLastName(), request.getRole());
        Employee saved = employeeRepository.save(employee);
        return ResponseEntity.status(HttpStatus.CREATED).body(new EmployeeResponse(saved));
    }

    @GetMapping
    public ResponseEntity<List<EmployeeResponse>> listEmployees() {
        List<EmployeeResponse> employees = employeeRepository.findAll()
                .stream()
                .map(EmployeeResponse::new)
                .toList();
        return ResponseEntity.ok(employees);
    }
}
