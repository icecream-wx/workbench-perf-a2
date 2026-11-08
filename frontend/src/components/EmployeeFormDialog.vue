<script setup lang="ts">
import { nextTick, reactive, ref } from 'vue';
import {
  ElButton,
  ElDatePicker,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElOption,
  ElSelect,
} from 'element-plus';
import type { FormInstance, FormItemRule, FormRules } from 'element-plus';
import {
  createEmployee,
  emptyEmployeeInput,
  updateEmployee,
  type Employee,
  type EmployeeInput,
} from '../api/employee';
import { ApiError } from '../api/http';

const emit = defineEmits<{ saved: [] }>();

const PHONE_PATTERN = /^(?=(?:\D*\d){6,20}$)[\d+\-]{6,20}$/;
const EMAIL_PATTERN = /^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/;

const visible = ref(false);
const mode = ref<'create' | 'edit'>('create');
const editingId = ref<number | null>(null);
const submitting = ref(false);
const formRef = ref<FormInstance>();
const serverErrors = reactive<Record<string, string>>({});
const form = reactive<EmployeeInput>(emptyEmployeeInput());

function phoneValidator(_rule: FormItemRule, value: string, callback: (error?: Error) => void): void {
  const trimmed = (value || '').trim();
  if (!trimmed) {
    callback();
    return;
  }
  if (!PHONE_PATTERN.test(trimmed)) {
    callback(new Error('手机号格式不正确（6—20 位数字，可含 + 或 -）'));
    return;
  }
  callback();
}

function emailValidator(_rule: FormItemRule, value: string, callback: (error?: Error) => void): void {
  const trimmed = (value || '').trim();
  if (!trimmed) {
    callback();
    return;
  }
  if (trimmed.length > 128) {
    callback(new Error('邮箱长度不能超过 128 个字符'));
    return;
  }
  if (!EMAIL_PATTERN.test(trimmed)) {
    callback(new Error('邮箱格式不正确'));
    return;
  }
  callback();
}

const rules: FormRules = {
  employeeNo: [
    { required: true, whitespace: true, message: '请输入工号', trigger: 'blur' },
    { max: 32, message: '工号长度不能超过 32 个字符', trigger: 'blur' },
  ],
  name: [
    { required: true, whitespace: true, message: '请输入姓名', trigger: 'blur' },
    { max: 50, message: '姓名长度不能超过 50 个字符', trigger: 'blur' },
  ],
  department: [{ max: 64, message: '部门长度不能超过 64 个字符', trigger: 'blur' }],
  position: [{ max: 64, message: '职位长度不能超过 64 个字符', trigger: 'blur' }],
  phone: [{ validator: phoneValidator, trigger: 'blur' }],
  email: [{ validator: emailValidator, trigger: 'blur' }],
};

function resetServerErrors(): void {
  Object.keys(serverErrors).forEach((key) => {
    delete serverErrors[key];
  });
}

function clearServerError(field: string): void {
  delete serverErrors[field];
}

function openCreate(): void {
  mode.value = 'create';
  editingId.value = null;
  Object.assign(form, emptyEmployeeInput());
  resetServerErrors();
  visible.value = true;
  void nextTick(() => formRef.value?.clearValidate());
}

function openEdit(employee: Employee): void {
  mode.value = 'edit';
  editingId.value = employee.id;
  Object.assign(form, {
    employeeNo: employee.employeeNo,
    name: employee.name,
    gender: employee.gender ?? '',
    department: employee.department ?? '',
    position: employee.position ?? '',
    phone: employee.phone ?? '',
    email: employee.email ?? '',
    hireDate: employee.hireDate ?? '',
  });
  resetServerErrors();
  visible.value = true;
  void nextTick(() => formRef.value?.clearValidate());
}

function close(): void {
  if (submitting.value) return;
  visible.value = false;
}

function handleBeforeClose(done: () => void): void {
  if (submitting.value) return;
  done();
}

function handleSubmitError(error: unknown): void {
  if (error instanceof ApiError) {
    if (error.status === 409) {
      serverErrors.employeeNo = '工号已存在';
      ElMessage.error(error.message || '工号已存在');
      return;
    }
    if (error.status === 400 && error.fieldErrors) {
      let mapped = false;
      Object.entries(error.fieldErrors).forEach(([field, message]) => {
        if (field in form) {
          serverErrors[field] = message;
          mapped = true;
        }
      });
      if (!mapped) {
        ElMessage.error(error.message || '保存失败，请检查输入');
      }
      return;
    }
    if (error.status === 404) {
      ElMessage.error(error.message || '员工不存在');
      visible.value = false;
      emit('saved');
      return;
    }
    ElMessage.error(error.message || '保存失败，请稍后重试');
    return;
  }
  ElMessage.error(error instanceof Error ? error.message : '保存失败，请稍后重试');
}

async function submit(): Promise<void> {
  if (!formRef.value || submitting.value) return;
  const valid = await formRef.value.validate().catch(() => false);
  if (!valid) return;

  submitting.value = true;
  resetServerErrors();
  try {
    if (mode.value === 'create') {
      await createEmployee(form);
      ElMessage.success('新增成功');
    } else if (editingId.value !== null) {
      await updateEmployee(editingId.value, form);
      ElMessage.success('保存成功');
    }
    visible.value = false;
    emit('saved');
  } catch (error) {
    handleSubmitError(error);
  } finally {
    submitting.value = false;
  }
}

defineExpose({ openCreate, openEdit });
</script>

<template>
  <ElDialog
    v-model="visible"
    :title="mode === 'create' ? '新增员工' : '编辑员工'"
    width="560px"
    :close-on-click-modal="!submitting"
    :close-on-press-escape="!submitting"
    :show-close="!submitting"
    :before-close="handleBeforeClose"
    append-to-body
  >
    <ElForm ref="formRef" :model="form" :rules="rules" label-width="88px" @submit.prevent>
      <ElFormItem label="工号" prop="employeeNo" :error="serverErrors.employeeNo">
        <ElInput
          v-model="form.employeeNo"
          maxlength="32"
          placeholder="请输入工号"
          data-testid="employee-no"
          @input="clearServerError('employeeNo')"
        />
      </ElFormItem>
      <ElFormItem label="姓名" prop="name" :error="serverErrors.name">
        <ElInput
          v-model="form.name"
          maxlength="50"
          placeholder="请输入姓名"
          data-testid="employee-name"
          @input="clearServerError('name')"
        />
      </ElFormItem>
      <ElFormItem label="性别" prop="gender" :error="serverErrors.gender">
        <ElSelect
          v-model="form.gender"
          placeholder="请选择性别"
          clearable
          style="width: 100%"
          data-testid="employee-gender"
          @change="clearServerError('gender')"
        >
          <ElOption label="男" value="MALE" />
          <ElOption label="女" value="FEMALE" />
          <ElOption label="其他" value="OTHER" />
        </ElSelect>
      </ElFormItem>
      <ElFormItem label="部门" prop="department" :error="serverErrors.department">
        <ElInput
          v-model="form.department"
          maxlength="64"
          placeholder="请输入部门"
          data-testid="employee-department"
          @input="clearServerError('department')"
        />
      </ElFormItem>
      <ElFormItem label="职位" prop="position" :error="serverErrors.position">
        <ElInput
          v-model="form.position"
          maxlength="64"
          placeholder="请输入职位"
          data-testid="employee-position"
          @input="clearServerError('position')"
        />
      </ElFormItem>
      <ElFormItem label="手机号" prop="phone" :error="serverErrors.phone">
        <ElInput
          v-model="form.phone"
          maxlength="20"
          placeholder="请输入手机号"
          data-testid="employee-phone"
          @input="clearServerError('phone')"
        />
      </ElFormItem>
      <ElFormItem label="邮箱" prop="email" :error="serverErrors.email">
        <ElInput
          v-model="form.email"
          maxlength="128"
          placeholder="请输入邮箱"
          data-testid="employee-email"
          @input="clearServerError('email')"
        />
      </ElFormItem>
      <ElFormItem label="入职日期" prop="hireDate" :error="serverErrors.hireDate">
        <ElDatePicker
          v-model="form.hireDate"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="请选择入职日期"
          style="width: 100%"
          data-testid="employee-hire-date"
          @change="clearServerError('hireDate')"
        />
      </ElFormItem>
    </ElForm>
    <template #footer>
      <ElButton :disabled="submitting" data-testid="employee-cancel" @click="close">取消</ElButton>
      <ElButton type="primary" :loading="submitting" data-testid="employee-save" @click="submit">
        保存
      </ElButton>
    </template>
  </ElDialog>
</template>
