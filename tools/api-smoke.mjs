#!/usr/bin/env node
/**
 * API smoke for perf-a2: health + OpenAPI + employee CRUD/validation flows.
 * Smoke rows use a SMOKE- prefix with a timestamp suffix so repeated runs
 * never collide on the employee_no unique index.
 */
const BACKEND = process.env.APP_BACKEND_URL || 'http://127.0.0.1:8080';
const FRONTEND = process.env.APP_FRONTEND_URL || 'http://127.0.0.1:5173';
const STAMP = Date.now();

const failures = [];

function check(condition, label, detail = '') {
  if (condition) {
    console.log(`  PASS ${label}`);
  } else {
    console.error(`  FAIL ${label}${detail ? ` :: ${detail}` : ''}`);
    failures.push(label);
  }
}

async function call(method, path, body) {
  const response = await fetch(BACKEND + path, {
    method,
    headers: {
      Accept: 'application/json',
      ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await response.text();
  let json;
  try {
    json = JSON.parse(text);
  } catch {
    json = undefined;
  }
  return { status: response.status, json, text };
}

const employeeNo = `SMOKE-API-${STAMP}`;
const payload = {
  employeeNo,
  name: '冒烟员工',
  gender: 'MALE',
  department: '冒烟部',
  position: '冒烟岗',
  phone: '13800138000',
  email: 'smoke@example.com',
  hireDate: '2024-05-01',
};

try {
  console.log('== health ==');
  const health = await call('GET', '/actuator/health');
  check(health.status === 200 && health.json?.status === 'UP', 'health is UP', health.text);

  console.log('== openapi ==');
  const openapi = await call('GET', '/v3/api-docs');
  const paths = openapi.json?.paths || {};
  check(openapi.status === 200, 'openapi returns 200');
  check(Boolean(paths['/api/employees']?.get), 'openapi has GET /api/employees');
  check(Boolean(paths['/api/employees']?.post), 'openapi has POST /api/employees');
  check(Boolean(paths['/api/employees/{id}']?.put), 'openapi has PUT /api/employees/{id}');

  console.log('== employee create ==');
  const created = await call('POST', '/api/employees', payload);
  check(created.status === 201, 'create returns 201', created.text);
  check(typeof created.json?.id === 'number', 'create returns numeric id');
  check(created.json?.employeeNo === employeeNo, 'create returns employeeNo');
  check(Boolean(created.json?.createdAt), 'create returns createdAt');

  console.log('== employee list (keyword) ==');
  const listed = await call('GET', `/api/employees?keyword=${encodeURIComponent(employeeNo)}`);
  check(listed.status === 200, 'list returns 200');
  check(listed.json?.total >= 1, 'list total >= 1', listed.text);
  check(
    Array.isArray(listed.json?.items) && listed.json.items.some((item) => item.employeeNo === employeeNo),
    'list contains created employee',
  );

  console.log('== employee update ==');
  const updated = await call('PUT', `/api/employees/${created.json.id}`, {
    ...payload,
    name: '冒烟改名',
    department: '冒烟新部门',
  });
  check(updated.status === 200, 'update returns 200', updated.text);
  check(updated.json?.name === '冒烟改名', 'update returns new name');
  check(updated.json?.department === '冒烟新部门', 'update returns new department');
  check(
    updated.json?.updatedAt >= updated.json?.createdAt,
    'updatedAt is not earlier than createdAt',
  );

  const listedAfterUpdate = await call('GET', `/api/employees?keyword=${encodeURIComponent(employeeNo)}`);
  const updatedItem = listedAfterUpdate.json?.items?.find((item) => item.id === created.json.id);
  check(updatedItem?.name === '冒烟改名', 'list shows new name after update');

  console.log('== duplicate employee_no ==');
  const duplicate = await call('POST', '/api/employees', payload);
  check(duplicate.status === 409, 'duplicate returns 409', duplicate.text);
  check(duplicate.json?.code === 'EMPLOYEE_NO_DUPLICATE', 'duplicate code is EMPLOYEE_NO_DUPLICATE');

  console.log('== list validation ==');
  const zeroSize = await call('GET', '/api/employees?size=0');
  check(zeroSize.status === 400 && zeroSize.json?.code === 'VALIDATION_FAILED', 'size=0 returns 400');
  const badSize = await call('GET', '/api/employees?size=abc');
  check(badSize.status === 400 && badSize.json?.code === 'VALIDATION_FAILED', 'size=abc returns 400');
  const missing = await call('PUT', '/api/employees/999999', payload);
  check(missing.status === 404 && missing.json?.code === 'EMPLOYEE_NOT_FOUND', 'missing id returns 404');

  console.log('== frontend ==');
  const root = await fetch(FRONTEND + '/');
  check(root.status === 200, 'frontend root returns 200');
} catch (error) {
  console.error('API smoke failed:', error.message);
  failures.push(error.message);
}

if (failures.length) {
  console.error(`API smoke: FAIL (${failures.length})`);
  process.exitCode = 1;
} else {
  console.log('API smoke: PASS');
}
