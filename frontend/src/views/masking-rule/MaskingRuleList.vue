<template>
  <Layout>
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>脱敏规则管理</span>
          <el-button type="primary" @click="openCreateDialog">
            <el-icon><Plus /></el-icon> 新建规则
          </el-button>
        </div>
      </template>

      <el-table :data="rules" stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="name" label="规则名称" min-width="160" />
        <el-table-column label="规则类型" width="140">
          <template #default="{ row }">
            <el-tag :type="getRuleTypeTag(row.type)" size="small" effect="light">
              {{ getRuleTypeLabel(row.type) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
        <el-table-column label="更新时间" width="180">
          <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openEditDialog(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- Create/Edit Dialog -->
    <el-dialog v-model="dialogVisible" :title="isEditing ? '编辑规则' : '新建规则'" width="600px">
      <el-form :model="form" label-position="top" ref="formRef" :rules="rules">
        <el-form-item label="规则名称" prop="name">
          <el-input v-model="form.name" placeholder="输入规则名称" />
        </el-form-item>
        <el-form-item label="规则类型" prop="type">
          <el-select v-model="form.type" placeholder="选择类型" style="width: 100%">
            <el-option label="掩码 (MASK)" value="MASK" />
            <el-option label="哈希 (HASH)" value="HASH" />
            <el-option label="替换 (REPLACE)" value="REPLACE" />
            <el-option label="截断 (TRUNCATE)" value="TRUNCATE" />
            <el-option label="置空 (NULLIFY)" value="NULLIFY" />
          </el-select>
        </el-form-item>
        <el-form-item label="配置 (JSON)">
          <el-input v-model="form.config" type="textarea" :rows="4" placeholder='{"start": 1, "end": -1, "maskChar": "*"}' />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="规则描述" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave" :loading="saving">保存</el-button>
      </template>
    </el-dialog>
  </Layout>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import Layout from '@/components/common/Layout.vue'
import {
  listMaskingRules,
  createMaskingRule,
  updateMaskingRule,
  deleteMaskingRule,
  type MaskingRule,
  type MaskingRuleRequest,
} from '@/api/masking-rule'

const rules = ref<MaskingRule[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const isEditing = ref(false)
const saving = ref(false)
const formRef = ref()
const currentId = ref<number | null>(null)

const form = ref<MaskingRuleRequest>({
  name: '',
  type: 'MASK',
  config: '',
  description: '',
})

const ruleFormRules = {
  name: [{ required: true, message: '请输入规则名称', trigger: 'blur' }],
  type: [{ required: true, message: '请选择规则类型', trigger: 'change' }],
}

function formatTime(t: string) {
  if (!t) return ''
  return t.slice(0, 16).replace('T', ' ')
}

function getRuleTypeLabel(type: string): string {
  const labels: Record<string, string> = {
    MASK: '掩码',
    HASH: '哈希',
    REPLACE: '替换',
    TRUNCATE: '截断',
    NULLIFY: '置空',
  }
  return labels[type] || type
}

function getRuleTypeTag(type: string): string {
  const tags: Record<string, string> = {
    MASK: 'info',
    HASH: 'primary',
    REPLACE: 'warning',
    TRUNCATE: '',
    NULLIFY: 'danger',
  }
  return tags[type] || 'info'
}

async function loadRules() {
  loading.value = true
  try {
    const res = await listMaskingRules()
    rules.value = res.data
  } catch { /* ignore */ }
  finally { loading.value = false }
}

function openCreateDialog() {
  isEditing.value = false
  currentId.value = null
  form.value = { name: '', type: 'MASK', config: '', description: '' }
  dialogVisible.value = true
}

function openEditDialog(rule: MaskingRule) {
  isEditing.value = true
  currentId.value = rule.id
  form.value = {
    name: rule.name,
    type: rule.type,
    config: rule.config || '',
    description: rule.description || '',
  }
  dialogVisible.value = true
}

async function handleSave() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }

  saving.value = true
  try {
    if (isEditing.value && currentId.value) {
      await updateMaskingRule({ ...form.value, id: currentId.value })
      ElMessage.success('规则已更新')
    } else {
      await createMaskingRule(form.value)
      ElMessage.success('规则已创建')
    }
    dialogVisible.value = false
    await loadRules()
  } catch (e: any) {
    ElMessage.error(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function handleDelete(rule: MaskingRule) {
  try {
    await ElMessageBox.confirm(`确定删除规则「${rule.name}」？`, '确认删除', { type: 'warning' })
    await deleteMaskingRule(rule.id)
    ElMessage.success('已删除')
    await loadRules()
  } catch { /* cancelled */ }
}

onMounted(loadRules)
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
