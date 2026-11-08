package com.example.perfa2.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record EmployeeResponse(
        Long id,
        String employeeNo,
        String name,
        String gender,
        String department,
        String position,
        String phone,
        String email,
        LocalDate hireDate,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static EmployeeResponse from(Employee employee) {
        return new EmployeeResponse(
                employee.getId(),
                employee.getEmployeeNo(),
                employee.getName(),
                employee.getGender(),
                employee.getDepartment(),
                employee.getPosition(),
                employee.getPhone(),
                employee.getEmail(),
                employee.getHireDate(),
                employee.getCreatedAt(),
                employee.getUpdatedAt()
        );
    }
}
