import { get, request } from './http';

export type Gender = 'MALE' | 'FEMALE' | 'OTHER';

export interface Employee {
  id: number;
  employeeNo: string;
  name: string;
  gender: Gender | null;
  department: string | null;
  position: string | null;
  phone: string | null;
  email: string | null;
  hireDate: string | null;
  createdAt: string;
  updatedAt: string;
}

/** Form model: empty values use '' so inputs/dates bind cleanly. */
export interface EmployeeInput {
  employeeNo: string;
  name: string;
  gender: Gender | '';
  department: string;
  position: string;
  phone: string;
  email: string;
  hireDate: string;
}

export interface EmployeeListQuery {
  page: number;
  size: number;
  keyword?: string;
  department?: string;
}

export interface EmployeePage {
  items: Employee[];
  total: number;
  page: number;
  size: number;
}

export function emptyEmployeeInput(): EmployeeInput {
  return {
    employeeNo: '',
    name: '',
    gender: '',
    department: '',
    position: '',
    phone: '',
    email: '',
    hireDate: '',
  };
}

function toPayload(input: EmployeeInput) {
  const optional = (value: string): string | null => {
    const trimmed = value.trim();
    return trimmed === '' ? null : trimmed;
  };
  return {
    employeeNo: input.employeeNo.trim(),
    name: input.name.trim(),
    gender: input.gender === '' ? null : input.gender,
    department: optional(input.department),
    position: optional(input.position),
    phone: optional(input.phone),
    email: optional(input.email),
    hireDate: optional(input.hireDate),
  };
}

export function fetchEmployees(query: EmployeeListQuery): Promise<EmployeePage> {
  return get<EmployeePage>('/employees', {
    params: {
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
      department: query.department || undefined,
    },
  });
}

export function createEmployee(input: EmployeeInput): Promise<Employee> {
  return request<Employee>({ method: 'POST', url: '/employees', data: toPayload(input) });
}

export function updateEmployee(id: number, input: EmployeeInput): Promise<Employee> {
  return request<Employee>({ method: 'PUT', url: `/employees/${id}`, data: toPayload(input) });
}
