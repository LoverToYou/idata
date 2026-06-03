/// <reference types="vitest" />
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
// @ts-ignore
import monacoEditorPlugin from 'vite-plugin-monaco-editor'

const monacoPlugin = typeof monacoEditorPlugin === 'function'
  ? monacoEditorPlugin
  : (monacoEditorPlugin as any).default || monacoEditorPlugin
import { resolve } from 'path'

export default defineConfig({
  base: '/api/',
  plugins: [
    vue(),
    AutoImport({
      resolvers: [ElementPlusResolver()],
    }),
    Components({
      resolvers: [ElementPlusResolver()],
    }),
    monacoPlugin({ languageWorkers: ['json', 'editorWorkerService'] }),
  ],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
      'monaco-editor': resolve(__dirname, 'node_modules/monaco-editor/esm/vs/editor/editor.main.js'),
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    css: true,
    server: {
      deps: {
        inline: ['element-plus', /element-plus/, 'monaco-editor'],
      },
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8088',
        changeOrigin: true,
      },
    },
  },
})
