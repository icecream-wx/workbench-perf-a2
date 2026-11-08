CREATE TABLE IF NOT EXISTS employee (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    employee_no VARCHAR(32) NOT NULL,
    name VARCHAR(50) NOT NULL,
    gender VARCHAR(8),
    department VARCHAR(64),
    position VARCHAR(64),
    phone VARCHAR(20),
    email VARCHAR(128),
    hire_date DATE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX uk_employee_no ON employee (employee_no);
CREATE INDEX idx_employee_department ON employee (department);
CREATE INDEX idx_employee_name ON employee (name);
