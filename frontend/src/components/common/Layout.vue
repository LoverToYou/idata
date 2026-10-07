<template>
  <!-- ================= 顶部导航模式 ================= -->
  <div v-if="layoutMode === 'top'" class="layout-root top">
    <header class="top-header">
      <div class="top-bar">
        <span class="brand">IDATA</span>
        <nav class="top-nav">
          <div class="top-item" :class="{ active: isHome }" @click="go('/dashboard')">工作台</div>
          <div
            v-for="item in MENU_TOP_ITEMS"
            :key="item.path"
            class="top-item"
            :class="{ active: isChildActive(item.path) }"
            @click="go(item.path)"
          >
            {{ item.title }}
          </div>
          <div
            v-for="group in MENU_GROUPS"
            :key="group.key"
            class="top-item"
            :class="{ active: activeGroupKey === group.key }"
            @click="go(group.children[0].path)"
          >
            {{ group.title }}
          </div>
        </nav>
        <div class="top-right">
          <el-tooltip content="切换到左侧菜单" placement="bottom">
            <el-icon class="toggle-icon" @click="switchLayout('side')"><Expand /></el-icon>
          </el-tooltip>
        </div>
      </div>

      <div class="sub-bar">
        <template v-if="activeGroup">
          <span class="sub-group-label">{{ activeGroup.title }}</span>
          <div
            v-for="child in activeGroup.children"
            :key="child.path"
            class="sub-item"
            :class="{ active: isChildActive(child.path) }"
            @click="go(child.path)"
          >
            {{ child.title }}
          </div>
        </template>
        <template v-else>
          <span class="sub-group-label">概览</span>
          <span class="sub-hint">{{ totalFeatures }} 个功能</span>
        </template>
      </div>
    </header>

    <main class="main-content top-main">
      <slot />
    </main>
  </div>

  <!-- ================= 左侧导航模式 ================= -->
  <div v-else class="layout-root">
    <aside class="sidebar">
      <div class="logo">
        <span class="logo-text">IDATA</span>
      </div>

      <div class="menu-scroll" ref="menuScrollEl">
        <el-menu
          :default-active="activeRoute"
          :default-openeds="defaultOpeneds"
          router
          class="side-menu"
        >
          <el-menu-item index="/dashboard">
            <el-icon><Odometer /></el-icon>
            <span>工作台</span>
          </el-menu-item>

          <el-menu-item v-for="item in MENU_TOP_ITEMS" :key="item.path" :index="item.path">
            <el-icon><component :is="item.icon" /></el-icon>
            <span>{{ item.title }}</span>
          </el-menu-item>

          <el-sub-menu v-for="group in MENU_GROUPS" :key="group.key" :index="group.key">
            <template #title>
              <el-icon><component :is="group.icon" /></el-icon>
              <span>{{ group.title }}</span>
            </template>
            <el-menu-item v-for="child in group.children" :key="child.path" :index="child.path">
              <span>{{ child.title }}</span>
            </el-menu-item>
          </el-sub-menu>
        </el-menu>
      </div>

      <div class="sidebar-footer">
        <span>v1.0</span>
        <el-tooltip content="切换到顶部菜单" placement="top">
          <el-icon class="toggle-icon" @click="switchLayout('top')"><Fold /></el-icon>
        </el-tooltip>
      </div>
    </aside>

    <div class="content-area">
      <header class="header">
        <span class="page-title">{{ pageTitle }}</span>
        <span v-if="groupTitle" class="page-group">{{ groupTitle }}</span>
      </header>
      <main class="main-content">
        <slot />
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useLocalStorage } from '@vueuse/core'
import { useRoute, useRouter } from 'vue-router'
import { Expand, Fold, Odometer } from '@element-plus/icons-vue'
import { MENU_GROUPS, MENU_TOP_ITEMS, groupKeyOfPath } from '@/config/menu'

const route = useRoute()
const router = useRouter()

const activeRoute = computed(() => route.path)
const pageTitle = computed(() => (route.meta.title as string) || 'IDATA')
const activeGroupKey = computed(() => groupKeyOfPath(route.path))
const activeGroup = computed(() => MENU_GROUPS.find((g) => g.key === activeGroupKey.value))
const groupTitle = computed(() => activeGroup.value?.title || '')
const isHome = computed(() => route.path === '/dashboard')
const totalFeatures = computed(
  () => MENU_GROUPS.reduce((n, g) => n + g.children.length, 0) + MENU_TOP_ITEMS.length,
)

// ---- 导航位置（顶部 / 左侧），VueUse 持久化 ----
const layoutMode = useLocalStorage<'side' | 'top'>('idata.layoutMode', 'side')

function switchLayout(mode: 'side' | 'top') {
  layoutMode.value = mode
}

function go(path: string) {
  router.push(path)
}

function isChildActive(path: string) {
  return route.path === path || route.path.startsWith(path + '/')
}

// ---- 左侧模式：默认展开全部分组，菜单按上下级层级展示 ----
const defaultOpeneds = MENU_GROUPS.map((g) => g.key)

// ---- 左侧模式：菜单滚动位置记忆 ----
const menuScrollEl = ref<HTMLElement>()
const SCROLL_KEY = 'idata.sidebar.scrollTop'

function saveScroll() {
  if (menuScrollEl.value) {
    sessionStorage.setItem(SCROLL_KEY, String(menuScrollEl.value.scrollTop))
  }
}

onMounted(() => {
  const el = menuScrollEl.value
  if (el) {
    const saved = Number(sessionStorage.getItem(SCROLL_KEY) || '0')
    if (saved > 0) {
      el.scrollTop = Math.min(saved, Math.max(0, el.scrollHeight - el.clientHeight))
    } else {
      const active = el.querySelector('.el-menu-item.is-active') as HTMLElement | null
      active?.scrollIntoView({ block: 'center' })
    }
    el.addEventListener('scroll', saveScroll, { passive: true })
  }
  autoFillSingleCard()
})

onBeforeUnmount(() => {
  saveScroll()
  menuScrollEl.value?.removeEventListener('scroll', saveScroll)
})

/**
 * 单卡片页面自动铺满内容区高度，避免底部留白（弹窗等非卡片元素不计入）
 */
function autoFillSingleCard() {
  const container = document.querySelector('.main-content, .top-main')
  if (!container) return
  const children = Array.from(container.children)
  children.forEach((c) => c.classList.remove('auto-fill'))
  const cards = children.filter((el) => el.classList.contains('el-card'))
  const others = children.filter(
    (el) => !el.classList.contains('el-card') && !el.classList.contains('el-overlay'),
  )
  if (cards.length === 1 && others.length === 0) {
    cards[0].classList.add('auto-fill')
    window.dispatchEvent(new Event('resize'))
  }
}
</script>

<style scoped>
.layout-root {
  display: flex;
  height: 100vh;
  width: 100vw;
  overflow: hidden;
  background: var(--bg-app);
}

.layout-root.top {
  flex-direction: column;
}

/* ================= 顶部导航 ================= */
.top-header {
  flex-shrink: 0;
  background: #fff;
  border-bottom: 1px solid var(--border);
}

.top-bar {
  height: var(--nav-h);
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 0 16px;
}

.brand {
  font-size: 16px;
  font-weight: 700;
  color: var(--text-title);
  letter-spacing: 1px;
}

.top-nav {
  display: flex;
  align-items: center;
  gap: 2px;
  flex: 1;
  min-width: 0;
  overflow-x: auto;
}

.top-item {
  padding: 5px 12px;
  border-radius: var(--radius);
  font-size: var(--fs-base);
  color: var(--text-body);
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.15s;
}

.top-item:hover {
  background: #f5f6f8;
  color: var(--text-title);
}

.top-item.active {
  background: var(--primary-light);
  color: var(--primary);
  font-weight: 500;
}

.top-right {
  display: flex;
  align-items: center;
}

.toggle-icon {
  font-size: 14px;
  color: var(--text-sub);
  cursor: pointer;
}

.toggle-icon:hover {
  color: var(--primary);
}

.sub-bar {
  height: 34px;
  display: flex;
  align-items: center;
  gap: 2px;
  padding: 0 16px;
  overflow-x: auto;
  background: var(--bg-muted);
  border-top: 1px solid var(--border-light);
}

.sub-group-label {
  font-size: var(--fs-sm);
  color: var(--text-sub);
  margin-right: 8px;
  white-space: nowrap;
}

.sub-hint {
  font-size: var(--fs-sm);
  color: var(--text-faint);
}

.sub-item {
  padding: 3px 10px;
  border-radius: var(--radius-sm);
  font-size: var(--fs-sm);
  color: var(--text-body);
  cursor: pointer;
  white-space: nowrap;
}

.sub-item:hover {
  background: #eceff3;
  color: var(--text-title);
}

.sub-item.active {
  background: var(--primary-light);
  color: var(--primary);
  font-weight: 500;
}

.top-main {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 16px;
}

/* ================= 左侧导航 ================= */
.sidebar {
  width: 208px;
  flex: 0 0 208px;
  height: 100vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: #fff;
  border-right: 1px solid var(--border);
}

.logo {
  height: var(--nav-h);
  flex-shrink: 0;
  display: flex;
  align-items: center;
  padding: 0 16px;
}

.logo-text {
  font-size: 16px;
  font-weight: 700;
  color: var(--text-title);
  letter-spacing: 1px;
}

.menu-scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 4px 8px 8px;
}

.sidebar-footer {
  flex-shrink: 0;
  height: 34px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 14px;
  font-size: var(--fs-xs);
  color: var(--text-faint);
  border-top: 1px solid var(--border-light);
}

.side-menu {
  border-right: none;
  background: transparent;
}

.side-menu .el-menu-item {
  height: 36px;
  line-height: 36px;
  font-size: var(--fs-nav);
  color: var(--text-body);
  border-radius: var(--radius);
  margin-bottom: 1px;
}

.side-menu .el-menu-item:hover {
  background: #f5f6f8;
  color: var(--text-title);
}

.side-menu .el-menu-item.is-active {
  background: var(--primary-light);
  color: var(--primary);
  font-weight: 500;
}

.side-menu :deep(.el-sub-menu__title) {
  height: 38px;
  line-height: 38px;
  font-size: var(--fs-nav);
  color: var(--text-sub);
  border-radius: var(--radius);
  /* 分组固定全展开，不再提供折叠交互 */
  pointer-events: none;
  cursor: default;
}

/* 分组默认全展开，去掉折叠箭头标识 */
.side-menu :deep(.el-sub-menu__icon-arrow) {
  display: none;
}

.side-menu :deep(.el-sub-menu .el-menu) {
  background: transparent;
  margin-left: 20px;
  margin-top: -3px;
  margin-bottom: -4px;
  padding-left: 0;
  border-left: 1px solid var(--border-light);
}

.side-menu :deep(.el-sub-menu .el-menu-item) {
  min-width: 0;
  padding-left: 34px !important;
  height: 28px;
  line-height: 28px;
  margin-bottom: 0;
  font-size: var(--fs-nav-sub);
}

.side-menu :deep(.el-sub-menu.is-active .el-sub-menu__title) {
  color: var(--text-title);
}

/* 菜单图标按文字比例缩小（Element Plus 默认写死 18px，相对 12px 文字偏大） */
.side-menu :deep(.el-menu-item [class^='el-icon']),
.side-menu :deep(.el-sub-menu .el-icon) {
  font-size: 14px;
}

/* ================= 右侧功能区 ================= */
.content-area {
  flex: 1;
  min-width: 0;
  height: 100vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.header {
  height: var(--nav-h);
  flex-shrink: 0;
  background: #fff;
  border-bottom: 1px solid var(--border);
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 16px;
}

.page-title {
  font-size: var(--fs-title);
  font-weight: 600;
  color: var(--text-title);
}

.page-group {
  font-size: var(--fs-sm);
  color: var(--text-faint);
}

.main-content {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 16px;
}
</style>
