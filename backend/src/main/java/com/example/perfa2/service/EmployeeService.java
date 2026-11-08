package com.example.perfa2.service;

import com.example.perfa2.domain.Employee;
import com.example.perfa2.domain.EmployeeRequest;
import com.example.perfa2.domain.EmployeeResponse;
import com.example.perfa2.domain.PageResponse;
import com.example.perfa2.exception.BizException;
import com.example.perfa2.repository.EmployeeRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class EmployeeService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final Set<String> GENDERS = Set.of("MALE", "FEMALE", "OTHER");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^(?=(?:\\D*\\d){6,20}$)[\\d+\\-]{6,20}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final EmployeeRepository repository;

    public EmployeeService(EmployeeRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public PageResponse<EmployeeResponse> list(String pageRaw, String sizeRaw, String keyword, String department) {
        int page = parsePage(pageRaw);
        int size = parseSize(sizeRaw);
        String keywordValue = trimToNull(keyword);
        String departmentValue = trimToNull(department);

        Specification<Employee> specification = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (keywordValue != null) {
                String like = "%" + keywordValue.toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("employeeNo")), like),
                        cb.like(cb.lower(root.get("name")), like)
                ));
            }
            if (departmentValue != null) {
                predicates.add(cb.equal(root.get("department"), departmentValue));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Employee> result = repository.findAll(specification,
                PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "id")));
        List<EmployeeResponse> items = result.getContent().stream().map(EmployeeResponse::from).toList();
        return new PageResponse<>(items, result.getTotalElements(), page, size);
    }

    @Transactional
    public EmployeeResponse create(EmployeeRequest request) {
        NormalizedEmployee data = validate(request);
        if (repository.existsByEmployeeNo(data.employeeNo())) {
            throw duplicateEmployeeNo();
        }
        Employee employee = new Employee();
        apply(employee, data);
        LocalDateTime now = LocalDateTime.now();
        employee.setCreatedAt(now);
        employee.setUpdatedAt(now);
        return EmployeeResponse.from(repository.saveAndFlush(employee));
    }

    @Transactional
    public EmployeeResponse update(Long id, EmployeeRequest request) {
        Employee employee = repository.findById(id)
                .orElseThrow(() -> new BizException(HttpStatus.NOT_FOUND, "EMPLOYEE_NOT_FOUND", "员工不存在"));
        NormalizedEmployee data = validate(request);
        if (repository.existsByEmployeeNoAndIdNot(data.employeeNo(), id)) {
            throw duplicateEmployeeNo();
        }
        apply(employee, data);
        employee.setUpdatedAt(LocalDateTime.now());
        return EmployeeResponse.from(repository.saveAndFlush(employee));
    }

    private NormalizedEmployee validate(EmployeeRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();

        String employeeNo = trimToNull(request.employeeNo());
        if (employeeNo == null) {
            errors.put("employeeNo", "工号不能为空");
        } else if (employeeNo.length() > 32) {
            errors.put("employeeNo", "工号长度不能超过 32 个字符");
        }

        String name = trimToNull(request.name());
        if (name == null) {
            errors.put("name", "姓名不能为空");
        } else if (name.length() > 50) {
            errors.put("name", "姓名长度不能超过 50 个字符");
        }

        String gender = trimToNull(request.gender());
        if (gender != null && !GENDERS.contains(gender)) {
            errors.put("gender", "性别只能是 MALE、FEMALE 或 OTHER");
        }

        String department = trimToNull(request.department());
        if (department != null && department.length() > 64) {
            errors.put("department", "部门长度不能超过 64 个字符");
        }

        String position = trimToNull(request.position());
        if (position != null && position.length() > 64) {
            errors.put("position", "职位长度不能超过 64 个字符");
        }

        String phone = trimToNull(request.phone());
        if (phone != null && !PHONE_PATTERN.matcher(phone).matches()) {
            errors.put("phone", "手机号格式不正确（6—20 位数字，可含 + 或 -）");
        }

        String email = trimToNull(request.email());
        if (email != null) {
            if (email.length() > 128) {
                errors.put("email", "邮箱长度不能超过 128 个字符");
            } else if (!EMAIL_PATTERN.matcher(email).matches()) {
                errors.put("email", "邮箱格式不正确");
            }
        }

        LocalDate hireDate = null;
        String hireDateRaw = trimToNull(request.hireDate());
        if (hireDateRaw != null) {
            try {
                hireDate = LocalDate.parse(hireDateRaw, DATE_FORMAT);
            } catch (DateTimeParseException exception) {
                errors.put("hireDate", "入职日期格式应为 yyyy-MM-dd");
            }
        }

        if (!errors.isEmpty()) {
            throw new BizException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "请求参数校验失败", errors);
        }

        return new NormalizedEmployee(employeeNo, name, gender, department, position, phone, email, hireDate);
    }

    private void apply(Employee employee, NormalizedEmployee data) {
        employee.setEmployeeNo(data.employeeNo());
        employee.setName(data.name());
        employee.setGender(data.gender());
        employee.setDepartment(data.department());
        employee.setPosition(data.position());
        employee.setPhone(data.phone());
        employee.setEmail(data.email());
        employee.setHireDate(data.hireDate());
    }

    private int parsePage(String raw) {
        if (raw == null || raw.isBlank()) {
            return 1;
        }
        try {
            return Math.max(Integer.parseInt(raw.trim()), 1);
        } catch (NumberFormatException exception) {
            throw validationFailure("page", "页码必须是数字");
        }
    }

    private int parseSize(String raw) {
        if (raw == null || raw.isBlank()) {
            return 10;
        }
        int size;
        try {
            size = Integer.parseInt(raw.trim());
        } catch (NumberFormatException exception) {
            throw validationFailure("size", "每页条数必须是数字");
        }
        if (size <= 0) {
            throw validationFailure("size", "每页条数必须大于 0");
        }
        return Math.min(size, 100);
    }

    private BizException validationFailure(String field, String message) {
        Map<String, String> errors = new LinkedHashMap<>();
        errors.put(field, message);
        return new BizException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "请求参数校验失败", errors);
    }

    private BizException duplicateEmployeeNo() {
        return new BizException(HttpStatus.CONFLICT, "EMPLOYEE_NO_DUPLICATE", "工号已存在");
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record NormalizedEmployee(
            String employeeNo,
            String name,
            String gender,
            String department,
            String position,
            String phone,
            String email,
            LocalDate hireDate
    ) {
    }
}
