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
        bypass: (req) => {
          if (!req.url) return;
          // 只看路径部分，避免 query 参数里的扩展名（如 jarFileName=xxx.jar）误判为静态资源
          const pathname = req.url.split('?')[0];
          // Vite 自己的资源文件（HMR、源码、node_modules）不应被 proxy
          if (pathname.startsWith('/api/@vite/') ||
              pathname.startsWith('/api/src/') ||
              pathname.startsWith('/api/node_modules/') ||
              pathname.startsWith('/api/@id/')) {
            return req.url;
          }
          // 静态资源请求（路径带文件扩展名）不应被 proxy
          if (/\.[a-z0-9]+$/i.test(pathname)) {
            return req.url;
          }
          // 浏览器导航（Accept: text/html）→ SPA 路由，由 Vite 处理
          if (req.headers?.accept?.includes('text/html')) {
            return req.url;
          }
        },
      },
    },
  },
})
