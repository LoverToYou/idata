<template>
  <Layout>
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>Hive 元数据浏览 - {{ datasourceName }}</span>
          <el-button @click="$router.push('/datasource')">返回</el-button>
        </div>
      </template>

      <el-row :gutter="20">
        <el-col :span="6">
          <el-input
            v-model="databaseFilter"
            placeholder="搜索数据库..."
            size="small"
            clearable
          />
          <el-tree
            :data="databaseTree"
            :props="{ label: 'name' }"
            :filter-node-method="filterNode"
            node-key="name"
            default-expand-all
            highlight-current
            @node-click="onDatabaseClick"
            ref="treeRef"
          />
        </el-col>

        <el-col :span="18">
          <div v-if="selectedDatabase">
            <h3>{{ selectedDatabase }}</h3>
            <el-table :data="tables" stripe v-loading="loadingTables">
              <el-table-column prop="name" label="表名" min-width="200" />
              <el-table-column label="操作" width="200">
                <template #default="{ row }">
                  <el-button size="small" @click="showSchema(row.name)">
                    查看结构
                  </el-button>
                  <el-button size="small" @click="showPartitions(row.name)">
                    分区信息
                  </el-button>
                </template>
              </el-table-column>
            </el-table>

            <el-dialog
              v-model="schemaVisible"
              :title="`表结构 - ${schemaTableName}`"
              width="800px"
            >
              <el-table :data="schemaData" stripe>
                <el-table-column prop="name" label="字段名" width="200" />
                <el-table-column prop="type" label="类型" width="150" />
                <el-table-column prop="comment" label="注释" />
              </el-table>
            </el-dialog>

            <el-dialog
              v-model="partitionVisible"
              :title="`分区信息 - ${partitionTableName}`"
              width="600px"
            >
              <el-table :data="partitionData" stripe>
                <el-table-column prop="partition" label="分区" />
              </el-table>
            </el-dialog>
          </div>
          <el-empty v-else description="请从左侧选择一个数据库" />
        </el-col>
      </el-row>
    </el-card>
  </Layout>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import Layout from '@/components/common/Layout.vue'
import {
  getDatasource,
  listHiveDatabases,
  listHiveTables,
  getHiveTableSchema,
  getHiveTablePartitions,
} from '@/api/datasource'

const route = useRoute()
const datasourceId = Number(route.params.id)
const datasourceName = ref('')

const databaseFilter = ref('')
const treeRef = ref()
const databaseTree = ref<any[]>([])
const selectedDatabase = ref('')
const tables = ref<any[]>([])
const loadingTables = ref(false)

const schemaVisible = ref(false)
const schemaTableName = ref('')
const schemaData = ref<any[]>([])

const partitionVisible = ref(false)
const partitionTableName = ref('')
const partitionData = ref<any[]>([])

watch(databaseFilter, (val) => {
  treeRef.value?.filter(val)
})

onMounted(async () => {
  const ds = await getDatasource(datasourceId)
  datasourceName.value = ds.data.name

  const dbs = await listHiveDatabases(datasourceId)
  databaseTree.value = dbs.data.map((name: string) => ({
    name,
    children: [],
  }))
})

function filterNode(value: string, data: any) {
  if (!value) return true
  return data.name.includes(value)
}

async function onDatabaseClick(data: any) {
  selectedDatabase.value = data.name
  loadingTables.value = true
  try {
    const res = await listHiveTables(datasourceId, data.name)
    tables.value = res.data.map((name: string) => ({ name }))
  } finally {
    loadingTables.value = false
  }
}

async function showSchema(tableName: string) {
  schemaTableName.value = tableName
  const res = await getHiveTableSchema(datasourceId, selectedDatabase.value, tableName)
  schemaData.value = res.data
  schemaVisible.value = true
}

async function showPartitions(tableName: string) {
  partitionTableName.value = tableName
  const res = await getHiveTablePartitions(datasourceId, selectedDatabase.value, tableName)
  partitionData.value = res.data
  partitionVisible.value = true
}
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
