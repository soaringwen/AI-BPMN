<script setup lang="ts">
import { ConfigProvider, theme } from 'ant-design-vue';
import zhCN from 'ant-design-vue/es/locale/zh_CN';
</script>

<template>
  <ConfigProvider :locale="zhCN" :theme="{ algorithm: theme.defaultAlgorithm }">
    <RouterView />
  </ConfigProvider>
</template>
