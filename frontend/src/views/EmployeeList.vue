<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { RouterLink } from 'vue-router';
import {
  ElButton,
  ElInput,
  ElMessage,
  ElPagination,
  ElTable,
  ElTableColumn,
} from 'element-plus';
import PageHeader from '../components/PageHeader.vue';
import EmployeeFormDialog from '../components/EmployeeFormDialog.vue';
import { fetchEmployees, type Employee } from '../api/employee';

const keywordInput = ref('');
const keyword = ref('');
const page = ref(1);
const size = ref(10);
const total = ref(0);
const items = ref<Employee[]>([]);
const loading = ref(false);
const dialogRef = ref<InstanceType<typeof EmployeeFormDialog> | null>(null);
let requestToken = 0;

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback;
}

async function load(): Promise<void> {
  const token = ++requestToken;
  loading.value = true;
  try {
    const result = await fetchEmployees({
      page: page.value,
      size: size.value,
      keyword: keyword.value || undefined,
    });
    if (token !== requestToken) return;
    items.value = result.items;
    total.value = result.total;
    page.value = result.page;
    size.value = result.size;
  } catch (error) {
    if (token !== requestToken) return;
    ElMessage.error(errorMessage(error, '员工列表加载失败，请稍后重试'));
  } finally {
    if (token === requestToken) loading.value = false;
  }
}

function search(): void {
  keyword.value = keywordInput.value.trim();
  page.value = 1;
  void load();
}

function reset(): void {
  keywordInput.value = '';
  keyword.value = '';
  page.value = 1;
  void load();
}

function handleCurrentChange(nextPage: number): void {
  page.value = nextPage;
  void load();
}

function handleSizeChange(nextSize: number): void {
  size.value = nextSize;
  page.value = 1;
  void load();
}

function openCreate(): void {
  dialogRef.value?.openCreate();
}

function openEdit(row: unknown): void {
  dialogRef.value?.openEdit(row as Employee);
}

async function handleSaved(): Promise<void> {
  await load();
  if (items.value.length === 0 && page.value > 1) {
    page.value = 1;
    await load();
  }
}

function genderText(gender: Employee['gender']): string {
  if (gender === 'MALE') return '男';
  if (gender === 'FEMALE') return '女';
  if (gender === 'OTHER') return '其他';
  return '-';
}

function display(value: string | null | undefined): string {
  return value && value.trim() ? value : '-';
}

function dateText(value: string | null): string {
  return value ? value.slice(0, 10) : '-';
}

onMounted(() => {
  void load();
});
</script>

<template>
  <main class="page employee-page">
    <PageHeader title="员工管理" subtitle="维护员工基础信息，支持按工号或姓名筛选。">
      <template #actions>
        <div class="header-actions">
          <RouterLink class="back-link" to="/">返回首页</RouterLink>
          <ElButton type="primary" data-testid="employee-create" @click="openCreate">新增员工</ElButton>
        </div>
      </template>
    </PageHeader>

    <section class="card">
      <div class="toolbar">
        <ElInput
          v-model="keywordInput"
          class="keyword"
          placeholder="工号/姓名"
          clearable
          data-testid="employee-keyword"
          @keyup.enter="search"
          @clear="search"
        />
        <ElButton type="primary" data-testid="employee-search" @click="search">查询</ElButton>
        <ElButton data-testid="employee-reset" @click="reset">重置</ElButton>
      </div>

      <ElTable
        v-loading="loading"
        :data="items"
        border
        stripe
        empty-text="暂无员工数据"
        data-testid="employee-table"
      >
        <ElTableColumn prop="employeeNo" label="工号" width="130" />
        <ElTableColumn prop="name" label="姓名" width="110" />
        <ElTableColumn label="性别" width="80">
          <template #default="{ row }">{{ genderText(row.gender) }}</template>
        </ElTableColumn>
        <ElTableColumn label="部门" min-width="120">
          <template #default="{ row }">{{ display(row.department) }}</template>
        </ElTableColumn>
        <ElTableColumn label="职位" min-width="120">
          <template #default="{ row }">{{ display(row.position) }}</template>
        </ElTableColumn>
        <ElTableColumn label="手机号" width="140">
          <template #default="{ row }">{{ display(row.phone) }}</template>
        </ElTableColumn>
        <ElTableColumn label="邮箱" min-width="180">
          <template #default="{ row }">{{ display(row.email) }}</template>
        </ElTableColumn>
        <ElTableColumn label="入职日期" width="120">
          <template #default="{ row }">{{ dateText(row.hireDate) }}</template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <ElButton link type="primary" data-testid="employee-edit" @click="openEdit(row)">编辑</ElButton>
          </template>
        </ElTableColumn>
      </ElTable>

      <div class="table-footer">
        <ElPagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @current-change="handleCurrentChange"
          @size-change="handleSizeChange"
        />
      </div>
    </section>

    <EmployeeFormDialog ref="dialogRef" @saved="handleSaved" />
  </main>
</template>

<style scoped>
.employee-page {
  width: min(1200px, 100%);
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 16px;
}

.back-link {
  color: var(--app-primary);
  font-size: 14px;
  text-decoration: none;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.keyword {
  width: 260px;
}

.table-footer {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
