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
      // 样式统一由 main.ts 引入的 element-plus/dist/index.css 提供，
      // 关闭按组件注入，避免重复 CSS 在 theme.css 之后加载而覆盖设计令牌
      resolvers: [ElementPlusResolver({ importStyle: false })],
    }),
    Components({
      resolvers: [ElementPlusResolver({ importStyle: false })],
    }),
    monacoPlugin({
      languageWorkers: ['json', 'editorWorkerService'],
      // 默认输出目录会多拼一层 base（dist/api/monacoeditorwork），与注入的
      // 绝对地址 /api/monacoeditorwork 不一致，这里固定输出到 dist/monacoeditorwork
      customDistPath: (root: string, outDir: string) => resolve(root, outDir, 'monacoeditorwork'),
    }),
  ],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
      // monaco 内置 SQL 词法定义（用于补充 Hive 关键字）
      'monaco-sql-builtin': resolve(
        __dirname,
        'node_modules/monaco-editor/esm/vs/basic-languages/sql/sql.js',
      ),
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
        target: 'http://localhost:18088',
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
