<script setup lang="ts">
import type { ValidationResult } from '@/types/processModel';

defineProps<{
  validation: ValidationResult | null;
}>();

const emit = defineEmits<{
  locate: [elementId: string];
}>();
</script>

<template>
  <div class="validation-panel">
    <template v-if="validation && (validation.errors.length > 0 || validation.warnings.length > 0)">
      <a-tag v-if="validation.errors.length" color="error">错误 {{ validation.errors.length }}</a-tag>
      <a-tag v-if="validation.warnings.length" color="warning">警告 {{ validation.warnings.length }}</a-tag>
      <span v-for="issue in validation.errors" :key="issue.code + issue.message" class="issue issue--error">
        <a-tag color="red">{{ issue.code }}</a-tag>
        {{ issue.message }}
        <a v-if="issue.elementId" @click="emit('locate', issue.elementId)">定位</a>
      </span>
      <span v-for="issue in validation.warnings" :key="issue.code + issue.message" class="issue issue--warning">
        <a-tag color="orange">{{ issue.code }}</a-tag>
        {{ issue.message }}
        <a v-if="issue.elementId" @click="emit('locate', issue.elementId)">定位</a>
      </span>
    </template>
    <span v-else-if="validation" class="issue issue--ok">校验通过：无错误、无警告</span>
    <span v-else class="issue issue--none">尚无校验结果</span>
  </div>
</template>

<style scoped>
.validation-panel {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 4px 12px;
  min-height: 32px;
  font-size: 12px;
  overflow-x: auto;
  white-space: nowrap;
  border-top: 1px solid #f0f0f0;
  background: #fafafa;
}

.issue--error {
  color: #cf1322;
}

.issue--warning {
  color: #d46b08;
}

.issue--ok {
  color: #389e0d;
}

.issue--none {
  color: #999;
}
</style>
