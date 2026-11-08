package com.example.perfa2.repository;

import com.example.perfa2.domain.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long>, JpaSpecificationExecutor<Employee> {

    Optional<Employee> findByEmployeeNo(String employeeNo);

    boolean existsByEmployeeNo(String employeeNo);

    boolean existsByEmployeeNoAndIdNot(String employeeNo, Long id);
}
