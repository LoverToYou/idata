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
        label-width="110px"
        style="max-width: 680px"
      >
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入数据源名称" />
        </el-form-item>

        <el-form-item label="连接方式">
          <el-radio-group v-model="connMode">
            <el-radio-button value="fields">主机 / 端口</el-radio-button>
            <el-radio-button value="url">JDBC URL</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="类型" prop="type">
          <el-select v-model="form.type" placeholder="请选择类型" style="width: 240px" @change="onTypeChange">
            <el-option label="MySQL" value="MYSQL" />
            <el-option label="Hive" value="HIVE" />
          </el-select>
        </el-form-item>

        <template v-if="connMode === 'fields'">
          <el-form-item label="主机地址" prop="host">
            <el-input v-model="form.host" placeholder="请输入主机地址，如 127.0.0.1" />
          </el-form-item>

          <el-form-item label="端口" prop="port">
            <el-input-number v-model="form.port" :min="1" :max="65535" style="width: 240px" />
          </el-form-item>

          <el-form-item label="数据库名" prop="databaseName">
            <el-input v-model="form.databaseName" placeholder="请输入数据库名" />
          </el-form-item>
        </template>

        <template v-else>
          <el-form-item label="JDBC URL" prop="jdbcUrl">
            <el-input
              v-model="form.jdbcUrl"
              type="textarea"
              :rows="3"
              placeholder="jdbc:mysql://127.0.0.1:3306/idata?useSSL=false&serverTimezone=Asia/Shanghai"
            />
            <div class="hint-block">
              示例：<code>jdbc:mysql://host:3306/db?useSSL=false&serverTimezone=Asia/Shanghai</code><br />
              &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<code>jdbc:hive2://host:10000/default</code><br />
              类型会根据 URL 自动识别（可手动调整），主机/端口方式下拼装的参数不再生效。
            </div>
          </el-form-item>
        </template>

        <template v-if="form.type === 'HIVE'">
          <el-form-item label="默认引擎">
            <el-radio-group v-model="form.engine">
              <el-radio-button value="HIVE">Hive</el-radio-button>
              <el-radio-button value="SPARK">Spark</el-radio-button>
            </el-radio-group>
            <span class="hint-inline">任务未指定时默认用这个引擎</span>
          </el-form-item>
          <el-form-item label="Spark 地址">
            <el-input
              v-model="form.sparkJdbcUrl"
              placeholder="jdbc:hive2://127.0.0.1:10005/default（Spark Thrift Server）"
            />
            <div class="hint-block">
              Spark 引擎入口是 <b>Spark Thrift Server</b>（而非 Spark Master 8080 / Worker 8081 的 Web UI 端口）；
              与 Hive 共用元数据，所以能看到同样的库表。
            </div>
          </el-form-item>
        </template>

        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            :placeholder="isEdit ? '不修改请留空' : '请输入密码'"
          />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="submitting" @click="handleSubmit">
            {{ isEdit ? '保存' : '创建' }}
          </el-button>
          <el-button :loading="testing" @click="handleTest()">测试连接</el-button>
          <el-button
            v-if="form.type === 'HIVE'"
            :loading="testing"
            @click="handleTest(form.engine === 'SPARK' ? 'HIVE' : 'SPARK')"
          >
            测试 {{ form.engine === 'SPARK' ? 'Hive' : 'Spark' }} 引擎
          </el-button>
          <el-button @click="$router.push('/datasource')">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </Layout>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import Layout from '@/components/common/Layout.vue'
import { createDatasource, getDatasource, testConnection, updateDatasource } from '@/api/datasource'
import type { DatasourceRequest } from '@/types'

const route = useRoute()
const router = useRouter()
const formRef = ref()
const submitting = ref(false)
const testing = ref(false)

const isEdit = computed(() => !!route.params.id)
/** 连接方式：主机端口 / JDBC URL */
const connMode = ref<'fields' | 'url'>('fields')

const form = ref<DatasourceRequest>({
  name: '',
  type: 'MYSQL',
  host: '',
  port: 3306,
  databaseName: '',
  jdbcUrl: '',
  engine: 'HIVE',
  sparkJdbcUrl: '',
  username: '',
  password: '',
})

const rules = computed(() => ({
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  type: [{ required: true, message: '请选择类型', trigger: 'change' }],
  host:
    connMode.value === 'fields'
      ? [{ required: true, message: '请输入主机地址', trigger: 'blur' }]
      : [],
  port:
    connMode.value === 'fields'
      ? [{ required: true, message: '请输入端口', trigger: 'blur' }]
      : [],
  jdbcUrl:
    connMode.value === 'url'
      ? [{ required: true, message: '请输入 JDBC URL', trigger: 'blur' }]
      : [],
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: isEdit.value
    ? []
    : [{ required: true, message: '请输入密码', trigger: 'blur' }],
}))

/** 按 JDBC URL 推断类型 */
function inferType(url?: string): string | undefined {
  if (!url) return undefined
  const u = url.toLowerCase()
  if (u.startsWith('jdbc:mysql')) return 'MYSQL'
  if (u.startsWith('jdbc:hive2')) return 'HIVE'
  return undefined
}

watch(
  () => form.value.jdbcUrl,
  (url) => {
    if (connMode.value !== 'url') return
    const t = inferType(url)
    if (t && t !== form.value.type) form.value.type = t
  },
)

function onTypeChange(type: string) {
  if (connMode.value === 'fields') {
    form.value.port = type === 'HIVE' ? 10000 : 3306
  }
}

onMounted(async () => {
  if (!isEdit.value) return
  const res = await getDatasource(Number(route.params.id))
  const d = res.data
  connMode.value = d.jdbcUrl ? 'url' : 'fields'
  form.value = {
    id: d.id,
    name: d.name,
    type: d.type,
    host: d.host,
    port: d.port,
    databaseName: d.databaseName,
    jdbcUrl: d.jdbcUrl || '',
    engine: d.engine || 'HIVE',
    sparkJdbcUrl: d.sparkJdbcUrl || '',
    username: d.username,
    password: '',
  }
})

async function handleSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  const payload: DatasourceRequest = {
    ...form.value,
    jdbcUrl: connMode.value === 'url' ? form.value.jdbcUrl?.trim() : '',
  }
  if (connMode.value === 'url') {
    // URL 方式下不再提交主机/端口信息
    payload.host = ''
    payload.port = undefined as unknown as number
    payload.databaseName = ''
  }
  if (payload.type !== 'HIVE') {
    payload.engine = undefined
    payload.sparkJdbcUrl = ''
  }

  submitting.value = true
  try {
    if (isEdit.value) {
      await updateDatasource(payload)
      ElMessage.success('保存成功')
    } else {
      await createDatasource(payload)
      ElMessage.success('创建成功')
    }
    router.push('/datasource')
  } catch (e: any) {
    ElMessage.error(e.message || '保存失败')
  } finally {
    submitting.value = false
  }
}

async function handleTest(engineOverride?: string) {
  const engine = engineOverride || form.value.engine || 'HIVE'
  const needJdbcUrl = engine !== 'SPARK' && connMode.value === 'url'
  if (needJdbcUrl && !form.value.jdbcUrl?.trim()) {
    ElMessage.warning('请先填写 JDBC URL')
    return
  }
  if (engine !== 'SPARK' && connMode.value === 'fields' && !form.value.host) {
    ElMessage.warning('请先填写主机地址')
    return
  }
  if (engine === 'SPARK' && !form.value.sparkJdbcUrl?.trim()) {
    ElMessage.warning('请先填写 Spark 引擎地址（Spark Thrift Server）')
    return
  }
  testing.value = true
  try {
    await testConnection({
      type: form.value.type,
      host: form.value.host,
      port: form.value.port,
      databaseName: form.value.databaseName,
      jdbcUrl: connMode.value === 'url' ? form.value.jdbcUrl?.trim() : undefined,
      sparkJdbcUrl: form.value.sparkJdbcUrl?.trim() || undefined,
      engine,
      username: form.value.username,
      password: form.value.password,
    })
    ElMessage.success(`${engine === 'SPARK' ? 'Spark' : 'Hive'} 引擎连接成功`)
  } catch (e: any) {
    ElMessage.error(e.message || '连接失败')
  } finally {
    testing.value = false
  }
}
</script>

<style scoped>
.hint-block {
  font-size: var(--fs-sm);
  color: var(--text-sub);
  line-height: 1.8;
  margin-top: 4px;
  width: 100%;
}

.hint-block code {
  background: var(--bg-muted);
  border: 1px solid var(--border-light);
  padding: 1px 5px;
  border-radius: var(--radius-sm);
  color: var(--text-body);
  font-size: 12px;
}
</style>
