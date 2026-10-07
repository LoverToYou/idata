<template>
  <div class="folder-tree">
    <div class="tree-header">
      <span class="tree-title">{{ title }}</span>
      <el-tooltip content="新建文件夹" placement="top">
        <el-icon class="header-action" @click="createFolderNode(null)"><FolderAdd /></el-icon>
      </el-tooltip>
    </div>

    <div class="tree-node" :class="{ active: modelValue === 'none' }" @click="select('none')">
      <el-icon><FolderOpened /></el-icon>
      <span class="node-label">未分组</span>
      <span class="node-count">{{ noneCount }}</span>
      <span class="node-actions">
        <el-tooltip :content="`新建${itemLabel}`" placement="top">
          <el-icon @click.stop="createItem(null)"><Plus /></el-icon>
        </el-tooltip>
      </span>
    </div>

    <el-tree
      :data="treeData"
      node-key="id"
      :props="{ label: 'name', children: 'children' }"
      :expand-on-click-node="false"
      default-expand-all
      :current-node-key="typeof modelValue === 'number' ? modelValue : undefined"
      highlight-current
      @node-click="(data: any) => select(data.id)"
    >
      <template #default="{ data }">
        <span class="tree-node-content">
          <span class="node-label" :title="data.name">{{ data.name }}</span>
          <span class="node-count">{{ data.count }}</span>
          <span class="node-actions">
            <el-tooltip :content="`新建${itemLabel}`" placement="top">
              <el-icon @click.stop="createItem(data.id)"><Plus /></el-icon>
            </el-tooltip>
            <el-tooltip content="新建子文件夹" placement="top">
              <el-icon @click.stop="createFolderNode(data.id)"><FolderAdd /></el-icon>
            </el-tooltip>
            <el-icon title="重命名" @click.stop="renameFolderNode(data)"><EditPen /></el-icon>
            <el-icon title="删除" @click.stop="removeFolderNode(data)"><Delete /></el-icon>
          </span>
        </span>
      </template>
    </el-tree>

    <div v-if="!folders.length" class="tree-empty">暂无文件夹</div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, EditPen, FolderAdd, FolderOpened, Plus } from '@element-plus/icons-vue'
import {
  createFolder,
  deleteFolder,
  descendantFolderIds,
  updateFolder,
  type BizType,
  type FolderItem,
  type FolderSelection,
} from '@/api/folder'

const props = withDefaults(
  defineProps<{
    title?: string
    /** 目录内条目的名称，用于「新建xx」提示，如 报表 / 看板 */
    itemLabel?: string
    bizType: BizType
    modelValue: FolderSelection
    folders: FolderItem[]
    items: Array<{ folderId?: number | null }>
  }>(),
  { itemLabel: '报表' },
)

const emit = defineEmits<{
  (e: 'update:modelValue', v: FolderSelection): void
  (e: 'changed'): void
  /** 在指定目录（null = 未分组）下新建条目，由父组件负责跳转创建页 */
  (e: 'create-item', folderId: number | null): void
}>()

function createItem(folderId: number | null) {
  emit('create-item', folderId)
}

const noneCount = computed(() => props.items.filter((i) => !i.folderId).length)

/** 每个文件夹（含子孙）下的条目数 */
function countOf(folderId: number): number {
  const ids = descendantFolderIds(props.folders, folderId)
  return props.items.filter((i) => i.folderId && ids.includes(i.folderId)).length
}

const treeData = computed(() => {
  const childrenOf = (parentId: number | null) =>
    props.folders
      .filter((f) => (f.parentId ?? null) === parentId)
      .map((f) => ({
        id: f.id,
        name: f.name,
        count: countOf(f.id),
        children: childrenOf(f.id),
      }))
  return childrenOf(null)
})

function select(v: FolderSelection) {
  // 再次点击同一节点 = 取消筛选（显示全部）
  emit('update:modelValue', props.modelValue === v ? 'all' : v)
}

async function createFolderNode(parentId: number | null) {
  try {
    const { value } = await ElMessageBox.prompt('文件夹名称', parentId ? '新建子文件夹' : '新建文件夹', {
      inputPlaceholder: '请输入文件夹名称',
      inputValidator: (v: string) => (v && v.trim() ? true : '名称不能为空'),
    })
    await createFolder({ name: value.trim(), parentId, bizType: props.bizType })
    ElMessage.success('文件夹已创建')
    emit('changed')
  } catch {
    /* cancelled */
  }
}

async function renameFolderNode(node: { id: number; name: string }) {
  try {
    const { value } = await ElMessageBox.prompt('文件夹名称', '重命名', {
      inputValue: node.name,
      inputValidator: (v: string) => (v && v.trim() ? true : '名称不能为空'),
    })
    await updateFolder({ id: node.id, name: value.trim(), bizType: props.bizType })
    ElMessage.success('已重命名')
    emit('changed')
  } catch {
    /* cancelled */
  }
}

async function removeFolderNode(node: { id: number; name: string }) {
  try {
    await ElMessageBox.confirm(
      `删除文件夹「${node.name}」？其中的内容会移动到上级目录，不会被删除。`,
      '确认删除',
      { type: 'warning' },
    )
    await deleteFolder(node.id)
    ElMessage.success('文件夹已删除')
    if (props.modelValue === node.id) select(node.id)
    emit('changed')
  } catch {
    /* cancelled */
  }
}
</script>

<style scoped>
.folder-tree {
  width: 100%;
  font-size: var(--fs-sm);
}
.tree-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}
.tree-title {
  font-size: var(--fs-sm);
  font-weight: 600;
  color: var(--text-sub);
  letter-spacing: 0.5px;
}
.tree-tip {
  font-size: var(--fs-xs);
  color: var(--text-faint);
}
.header-action {
  cursor: pointer;
  color: var(--text-sub);
  font-size: var(--fs-base);
}
.header-action:hover {
  color: var(--primary);
}
.tree-node {
  display: flex;
  align-items: center;
  gap: 5px;
  height: 28px;
  padding: 0 6px;
  border-radius: 4px;
  cursor: pointer;
  font-size: var(--fs-sm);
  color: var(--text-title);
}
.tree-node:hover {
  background: var(--bg-muted);
}
.tree-node.active {
  background: var(--primary-light);
  color: var(--primary);
}
.tree-node-content {
  display: flex;
  align-items: center;
  gap: 5px;
  width: 100%;
  padding-right: 4px;
  font-size: var(--fs-sm);
}
.node-label {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.node-count {
  font-size: var(--fs-xs);
  color: var(--text-faint);
}
.node-actions {
  display: none;
  align-items: center;
  gap: 4px;
  color: var(--text-sub);
  font-size: var(--fs-sm);
}
.node-actions .el-icon:hover {
  color: var(--primary);
}
.tree-node-content:hover .node-actions,
.tree-node:hover .node-actions {
  display: inline-flex;
}
.tree-empty {
  font-size: var(--fs-xs);
  color: var(--text-faint);
  padding: 6px 4px;
}
:deep(.el-tree-node__content) {
  height: 28px;
}
:deep(.el-tree-node__label) {
  font-size: var(--fs-sm);
}
</style>
