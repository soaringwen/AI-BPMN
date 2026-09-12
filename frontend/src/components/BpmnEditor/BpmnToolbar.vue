<script setup lang="ts">
const emit = defineEmits<{
  zoomIn: [];
  zoomOut: [];
  fit: [];
  layout: [];
}>();
</script>

<template>
  <a-space size="small">
    <a-button size="small" @click="emit('zoomIn')">放大</a-button>
    <a-button size="small" @click="emit('zoomOut')">缩小</a-button>
    <a-button size="small" @click="emit('fit')">适应画布</a-button>
    <a-button size="small" @click="emit('layout')">全图自动布局</a-button>
  </a-space>
</template>
