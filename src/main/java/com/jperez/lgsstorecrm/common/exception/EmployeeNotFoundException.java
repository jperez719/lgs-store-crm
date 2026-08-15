package com.jperez.lgsstorecrm.common.exception;

import java.util.UUID;

public class EmployeeNotFoundException extends RuntimeException {

    public EmployeeNotFoundException(UUID employeeId) {
        super("Employee not found with id: " + employeeId);
    }
}
