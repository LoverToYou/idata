<template>
  <Layout>
    <el-card shadow="hover">
      <template #header>
        <span>{{ isEdit ? '编辑数据源' : '新建数据源' }}</span>
      </template>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="120px"
        style="max-width: 600px"
      >
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入数据源名称" />
        </el-form-item>

        <el-form-item label="类型" prop="type">
          <el-select v-model="form.type" placeholder="请选择类型" style="width: 100%">
            <el-option label="MySQL" value="MYSQL" />
            <el-option label="Hive" value="HIVE" />
          </el-select>
        </el-form-item>

        <el-form-item label="主机地址" prop="host">
          <el-input v-model="form.host" placeholder="请输入主机地址" />
        </el-form-item>

        <el-form-item label="端口" prop="port">
          <el-input-number v-model="form.port" :min="1" :max="65535" style="width: 100%" />
        </el-form-item>

        <el-form-item label="数据库名" prop="databaseName">
          <el-input v-model="form.databaseName" placeholder="请输入数据库名" />
        </el-form-item>

        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            placeholder="请输入密码"
          />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="submitting" @click="handleSubmit">
            {{ isEdit ? '保存' : '创建' }}
          </el-button>
          <el-button @click="handleTest" :loading="testing">
            测试连接
          </el-button>
          <el-button @click="$router.push('/datasource')">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </Layout>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import Layout from '@/components/common/Layout.vue'
import { createDatasource, updateDatasource, getDatasource, testConnection } from '@/api/datasource'
import type { DatasourceRequest } from '@/types'

const route = useRoute()
const router = useRouter()
const formRef = ref()
const submitting = ref(false)
const testing = ref(false)

const isEdit = computed(() => !!route.params.id)

const form = ref<DatasourceRequest>({
  name: '',
  type: 'MYSQL',
  host: '',
  port: 3306,
  databaseName: '',
  username: '',
  password: '',
})

const rules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  type: [{ required: true, message: '请选择类型', trigger: 'change' }],
  host: [{ required: true, message: '请输入主机地址', trigger: 'blur' }],
  port: [{ required: true, message: '请输入端口', trigger: 'blur' }],
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

onMounted(async () => {
  if (isEdit.value) {
    const id = Number(route.params.id)
    const res = await getDatasource(id)
    form.value = {
      id: res.data.id,
      name: res.data.name,
      type: res.data.type,
      host: res.data.host,
      port: res.data.port,
      databaseName: res.data.databaseName,
      username: res.data.username,
      password: '',
    }
  }
})

async function handleSubmit() {
  const valid = await formRef.value.validate()
  if (!valid) return

  submitting.value = true
  try {
    if (isEdit.value) {
      await updateDatasource(form.value)
      ElMessage.success('保存成功')
    } else {
      await createDatasource(form.value)
      ElMessage.success('创建成功')
    }
    router.push('/datasource')
  } finally {
    submitting.value = false
  }
}

async function handleTest() {
  testing.value = true
  try {
    await testConnection({
      type: form.value.type,
      host: form.value.host,
      port: form.value.port,
      databaseName: form.value.databaseName,
      username: form.value.username,
      password: form.value.password,
    })
    ElMessage.success('连接成功')
  } catch (e: any) {
    ElMessage.error(e.message || '连接失败')
  } finally {
    testing.value = false
  }
}
</script>
