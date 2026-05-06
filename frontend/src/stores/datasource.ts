import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { DatasourceConfig } from '@/types'

export const useDatasourceStore = defineStore('datasource', () => {
  const datasources = ref<DatasourceConfig[]>([])

  function setDatasources(list: DatasourceConfig[]) {
    datasources.value = list
  }

  return { datasources, setDatasources }
})
