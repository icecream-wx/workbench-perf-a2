#!/usr/bin/env node
/**
 * UI smoke for perf-a2 via Chrome DevTools Protocol (no npm dependency).
 * Seeds an employee through the API, renders /employees, then exercises the
 * create dialog: empty-form validation, successful create, duplicate employee
 * number (409) and edit-save refresh. Ends by checking browser console errors.
 */
import fs from 'node:fs';
import path from 'node:path';
import { Browser } from './cdp.mjs';

const URL = process.env.APP_FRONTEND_URL || 'http://127.0.0.1:5173';
const BACKEND = process.env.APP_BACKEND_URL || 'http://127.0.0.1:8080';
const STAMP = Date.now();
const seededNo = `SMOKE-UI-${STAMP}`;
const createdNo = `SMOKE-FORM-${STAMP}`;
const candidates = [
  process.env.CHROME_PATH,
  'C:/Program Files/Google/Chrome/Application/chrome.exe',
  'C:/Program Files (x86)/Google/Chrome/Application/chrome.exe',
  'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
  'C:/Program Files/Microsoft/Edge/Application/msedge.exe',
].filter(Boolean);
const chrome = candidates.find((candidate) => fs.existsSync(candidate));

if (!chrome) {
  if (process.env.SKIP_UI_SMOKE === '1') {
    console.log('UI smoke: SKIP (no Chrome/Edge, SKIP_UI_SMOKE=1)');
    process.exit(0);
  }
  console.error('UI smoke: no Chrome/Edge found; set CHROME_PATH or SKIP_UI_SMOKE=1');
  process.exit(1);
}

async function seedEmployee() {
  const response = await fetch(`${BACKEND}/api/employees`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
    body: JSON.stringify({
      employeeNo: seededNo,
      name: '冒烟页面员工',
      gender: 'FEMALE',
      department: '冒烟部',
      position: '冒烟岗',
      phone: '13800138001',
      email: 'smoke-ui@example.com',
      hireDate: '2024-06-01',
    }),
  });
  if (response.status !== 201) {
    throw new Error(`seed employee failed: ${response.status}`);
  }
}

try {
  await seedEmployee();
} catch (error) {
  console.error('UI smoke seed failed:', error.message);
  process.exit(1);
}

let failed = false;

function check(condition, label) {
  if (condition) {
    console.log(`  PASS ${label}`);
  } else {
    console.error(`  FAIL ${label}`);
    failed = true;
  }
}

const browser = await new Browser({ chromePath: chrome, width: 1440, height: 900 }).launch();

function inputSelector(testId) {
  return `[data-testid="${testId}"]`;
}

async function fillTestId(testId, value) {
  await browser.evaluate(`(() => {
    const root = document.querySelector(${JSON.stringify(inputSelector(testId))});
    const el = root && (root.tagName === 'INPUT' || root.tagName === 'TEXTAREA'
      ? root
      : root.querySelector('input, textarea'));
    if (!el) throw new Error('input not found: ${testId}');
    el.focus();
    el.value = ${JSON.stringify(value)};
    el.dispatchEvent(new Event('input', { bubbles: true }));
    el.dispatchEvent(new Event('change', { bubbles: true }));
  })()`);
}

async function inputValue(testId) {
  return browser.evaluate(`(() => {
    const root = document.querySelector(${JSON.stringify(inputSelector(testId))});
    if (!root) return '';
    const el = root.tagName === 'INPUT' ? root : root.querySelector('input');
    return el?.value ?? '';
  })()`);
}

async function dialogOpen() {
  return browser.evaluate(
    `Boolean(document.querySelector('[data-testid="employee-no"]')?.offsetParent)`,
  );
}

async function waitDialogOpen() {
  await browser.waitFor(
    `Boolean(document.querySelector('[data-testid="employee-no"]')?.offsetParent)`,
    10000,
  );
}

async function waitDialogClosed() {
  await browser.waitFor(
    `!document.querySelector('[data-testid="employee-no"]')?.offsetParent`,
    10000,
  );
}

async function waitTableContains(text) {
  await browser.waitFor(
    `(document.querySelector('[data-testid="employee-table"]')?.innerText || '').includes(${JSON.stringify(text)})`,
    15000,
  );
}

try {
  console.log('== list render ==');
  await browser.goto(`${URL}/employees`, { waitMs: 1500 });
  await browser.waitFor('document.querySelector("#app")', 15000);
  await browser.waitFor(
    'document.querySelectorAll(\'[data-testid="employee-table"] .el-table__row\').length > 0',
    15000,
  );
  const info = await browser.evaluate(`({
    title: document.title,
    url: location.href,
    rows: document.querySelectorAll('[data-testid="employee-table"] .el-table__row').length,
    text: (document.querySelector('[data-testid="employee-table"]')?.innerText || '').slice(0, 300),
  })`);
  console.log('  list:', JSON.stringify({ title: info.title, url: info.url, rows: info.rows }));
  check(info.url.endsWith('/employees'), 'route /employees opens directly');
  check(info.text.includes(seededNo), 'table renders seeded employee');

  console.log('== create dialog: empty form validation ==');
  await browser.click('[data-testid="employee-create"]');
  await waitDialogOpen();
  await browser.click('[data-testid="employee-save"]');
  await browser.waitFor('document.querySelectorAll(\'.el-form-item__error\').length >= 2', 10000);
  const validationText = await browser.evaluate(
    `Array.from(document.querySelectorAll('.el-form-item__error')).map((node) => node.innerText).join('|')`,
  );
  console.log('  validation:', validationText);
  check(validationText.includes('请输入工号'), 'employeeNo required message');
  check(validationText.includes('请输入姓名'), 'name required message');
  check(await dialogOpen(), 'dialog stays open after invalid submit');

  console.log('== create success ==');
  await fillTestId('employee-no', createdNo);
  await fillTestId('employee-name', '冒烟页面新增');
  await browser.click('[data-testid="employee-save"]');
  await waitDialogClosed();
  await waitTableContains(createdNo);
  check(true, 'created employee appears in list');

  console.log('== duplicate employee number ==');
  await browser.click('[data-testid="employee-create"]');
  await waitDialogOpen();
  await fillTestId('employee-no', createdNo);
  await fillTestId('employee-name', '冒烟重复');
  await browser.click('[data-testid="employee-save"]');
  await browser.waitFor(
    `Array.from(document.querySelectorAll('.el-form-item__error')).some((node) => node.innerText.includes('工号已存在'))`,
    10000,
  );
  check(true, 'duplicate employeeNo shows inline field error');
  check(await dialogOpen(), 'dialog stays open on duplicate');
  check((await inputValue('employee-no')) === createdNo, 'dialog keeps input after duplicate');
  await browser.click('[data-testid="employee-cancel"]');
  await waitDialogClosed();

  console.log('== edit row ==');
  await browser.click(
    '[data-testid="employee-table"] .el-table__body tr.el-table__row:first-child [data-testid="employee-edit"]',
  );
  await waitDialogOpen();
  check((await inputValue('employee-no')) === createdNo, 'edit dialog prefills row data');
  await fillTestId('employee-name', '冒烟页面改名');
  await browser.click('[data-testid="employee-save"]');
  await waitDialogClosed();
  await waitTableContains('冒烟页面改名');
  check(true, 'edited name appears in list');

  if (process.env.UI_SMOKE_SCREENSHOT) {
    const file = path.resolve('verify', 'ui-smoke.png');
    fs.mkdirSync(path.dirname(file), { recursive: true });
    await browser.screenshot(file);
    console.log('screenshot:', file);
  }

  const errors = browser.consoleLogs.filter(
    (entry) => entry.type === 'error' && !/favicon/i.test(entry.text),
  );
  if (browser.exceptions.length || errors.length) {
    console.error(
      'browser errors:',
      browser.exceptions.join(' | '),
      errors.map((entry) => entry.text).join(' | '),
    );
    failed = true;
  }
} catch (error) {
  console.error('UI smoke failed:', error.message);
  failed = true;
} finally {
  await browser.close();
}
if (failed) process.exitCode = 1;
else console.log('UI smoke: PASS');
