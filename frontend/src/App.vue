<template>
  <div id="app-container">
    <router-view :key="$route.fullPath" />
  </div>
</template>

<script setup lang="ts"></script>

<style>
/* 只保留最基础的重置，主题与组件样式见 @/styles/theme.css */
#app,
#app-container {
  height: 100%;
}
</style>
