package com.jperez.lgsstorecrm.employee.dto;

import com.jperez.lgsstorecrm.employee.Employee;
import com.jperez.lgsstorecrm.employee.Role;
import lombok.Getter;

import java.util.UUID;

@Getter
public class EmployeeResponse {

    private final UUID id;
    private final String firstName;
    private final String lastName;
    private final Role role;

    public EmployeeResponse(Employee employee) {
        this.id = employee.getId();
        this.firstName = employee.getFirstName();
        this.lastName = employee.getLastName();
        this.role = employee.getRole();
    }
}
