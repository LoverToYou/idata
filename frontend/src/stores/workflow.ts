import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { WorkflowDefinition } from '@/types'
import * as workflowApi from '@/api/workflow'

export interface SqlTaskConfig {
  type: 'sql_task'
  sqlTaskId: number | null
}

export interface DataxConfig {
  type: 'datax'
  dataxTaskId: number | null
}

export type NodeConfig = SqlTaskConfig | DataxConfig

export interface DagNodeData {
  nodeType: 'sql_task' | 'datax'
  config: NodeConfig
}

/** Map from DAG JSON node type → Vue Flow component type */
function dagTypeToFlowType(dagType: string): string {
  if (dagType === 'sql_task') return 'sqlTaskNode'
  return 'dataxNode' // 'datax', 'source', 'sink' all map to dataxNode
}

/** Map from Vue Flow component type → DAG JSON node type */
function flowTypeToDagType(vfType: string): string {
  if (vfType === 'sqlTaskNode') return 'sql_task'
  return 'datax'
}

function createDefaultConfig(nodeType: string): NodeConfig {
  if (nodeType === 'sql_task') {
    return { type: 'sql_task', sqlTaskId: null }
  }
  return {
    type: 'datax',
    dataxTaskId: null,
  }
}

export const useWorkflowStore = defineStore('workflow', () => {
  const currentWorkflow = ref<WorkflowDefinition | null>(null)
  const workflowList = ref<WorkflowDefinition[]>([])
  const isDirty = ref(false)
  const nodes = ref<any[]>([])
  const edges = ref<any[]>([])

  function parseDagJson(dagJson: string) {
    try {
      const data = JSON.parse(dagJson)
      if (data.nodes && Array.isArray(data.nodes)) {
        nodes.value = data.nodes.map((n: any) => {
          const dagType = n.type || 'datax'
          const flowType = dagTypeToFlowType(dagType)
          const defaultConfig = createDefaultConfig(dagType)

          return {
            id: n.id,
            type: flowType,
            position: n.position,
            label: n.label,
            data: {
              nodeType: dagType,
              config: { ...defaultConfig, ...(n.config || {}) },
            },
          }
        })
      } else {
        nodes.value = []
      }
      if (data.edges && Array.isArray(data.edges)) {
        edges.value = data.edges.map((e: any) => ({
          id: e.id,
          source: e.source,
          target: e.target,
          ...(e.sourceHandle ? { sourceHandle: e.sourceHandle } : {}),
          ...(e.targetHandle ? { targetHandle: e.targetHandle } : {}),
        }))
      } else {
        edges.value = []
      }
    } catch {
      nodes.value = []
      edges.value = []
    }
  }

  function toDagJson(): string {
    const outNodes: any[] = (nodes.value as any[]).map((n: any) => ({
      id: n.id,
      type: flowTypeToDagType(n.type),
      label: n.label ?? '',
      config: n.data?.config ?? createDefaultConfig('datax'),
      position: n.position,
    }))
    const outEdges: any[] = (edges.value as any[]).map((e: any) => ({
      id: e.id,
      source: e.source,
      target: e.target,
    }))
    return JSON.stringify({ nodes: outNodes, edges: outEdges })
  }

  async function fetchList() {
    const res = await workflowApi.listWorkflows()
    workflowList.value = res.data
  }

  async function fetchWorkflow(id: number) {
    const res = await workflowApi.getWorkflow(id)
    currentWorkflow.value = res.data
    parseDagJson(res.data.dagJson)
    isDirty.value = false
  }

  async function saveWorkflow(data?: { name?: string; description?: string }) {
    const dagJsonStr = toDagJson()

    if (currentWorkflow.value?.id) {
      const res = await workflowApi.updateWorkflow({
        id: currentWorkflow.value.id,
        name: data?.name ?? currentWorkflow.value.name,
        description: data?.description ?? currentWorkflow.value.description,
        dagJson: dagJsonStr,
      })
      currentWorkflow.value = res.data
    } else {
      const res = await workflowApi.createWorkflow({
        name: data?.name ?? '未命名工作流',
        description: data?.description ?? '',
        dagJson: dagJsonStr,
      })
      currentWorkflow.value = res.data
    }
    isDirty.value = false
  }

  async function deleteWorkflow(id: number) {
    await workflowApi.deleteWorkflow(id)
    workflowList.value = workflowList.value.filter((w) => w.id !== id)
  }

  function addNode(
    template: { nodeType: string; label: string },
    position: { x: number; y: number },
  ) {
    const id = `node_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
    const dagType = template.nodeType === 'sql_task' ? 'sql_task' : 'datax'
    const flowType = dagTypeToFlowType(dagType)
    const newNode = {
      id,
      type: flowType,
      position,
      label: template.label,
      data: {
        nodeType: dagType as 'sql_task' | 'datax',
        config: createDefaultConfig(dagType),
      },
    }
    nodes.value.push(newNode)
    isDirty.value = true
    return newNode
  }

  function removeNode(id: string) {
    nodes.value = nodes.value.filter((n) => n.id !== id)
    edges.value = edges.value.filter((e) => e.source !== id && e.target !== id)
    isDirty.value = true
  }

  function updateNodeConfig(id: string, config: Partial<NodeConfig>) {
    const node = nodes.value.find((n) => n.id === id)
    if (node && node.data) {
      Object.assign(node.data.config, config)
      isDirty.value = true
    }
  }

  function addEdge(connection: { source: string; target: string; sourceHandle?: string | null; targetHandle?: string | null }) {
    const id = `edge_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
    edges.value.push({
      id,
      source: connection.source,
      target: connection.target,
      ...(connection.sourceHandle ? { sourceHandle: connection.sourceHandle } : {}),
      ...(connection.targetHandle ? { targetHandle: connection.targetHandle } : {}),
    })
    isDirty.value = true
  }

  function removeEdge(id: string) {
    edges.value = edges.value.filter((e) => e.id !== id)
    isDirty.value = true
  }

  function updateDagJson() {
    const dagJsonStr = toDagJson()
    if (currentWorkflow.value) {
      currentWorkflow.value.dagJson = dagJsonStr
    }
    isDirty.value = true
  }

  function resetDag() {
    nodes.value = []
    edges.value = []
    isDirty.value = false
  }

  return {
    currentWorkflow,
    workflowList,
    isDirty,
    nodes,
    edges,
    fetchList,
    fetchWorkflow,
    saveWorkflow,
    deleteWorkflow,
    addNode,
    removeNode,
    updateNodeConfig,
    addEdge,
    removeEdge,
    updateDagJson,
    resetDag,
    parseDagJson,
    toDagJson,
  }
})
