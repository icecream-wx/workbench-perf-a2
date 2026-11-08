package com.example.perfa2.domain;

/**
 * 员工新增/编辑请求体。createdAt/updatedAt 由服务端生成，id 以路径为准，请求中出现会被忽略。
 */
public record EmployeeRequest(
        String employeeNo,
        String name,
        String gender,
        String department,
        String position,
        String phone,
        String email,
        String hireDate
) {
}
