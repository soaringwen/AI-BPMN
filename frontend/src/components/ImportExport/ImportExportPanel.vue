<script setup lang="ts">
import { ref } from 'vue';
import { message as antMessage } from 'ant-design-vue';
import { readTextFile } from '@/services/workspaceFileService';

const emit = defineEmits<{
  importBpmn: [xml: string, filename: string];
  importWorkspace: [content: string, filename: string];
}>();

const maxSize = Number(import.meta.env.VITE_MAX_UPLOAD_SIZE || 10485760);
const uploading = ref(false);

async function onFileChange(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  input.value = '';
  if (!file) {
    return;
  }
  const lower = file.name.toLowerCase();
  if (!lower.endsWith('.bpmn') && !lower.endsWith('.xml') && !lower.endsWith('.json')) {
    antMessage.error('仅支持 .bpmn / .xml / .json 文件');
    return;
  }
  if (file.size > maxSize) {
    antMessage.error('文件大小超过10MB限制');
    return;
  }
  uploading.value = true;
  try {
    const content = await readTextFile(file);
    if (lower.endsWith('.json')) {
      emit('importWorkspace', content, file.name);
    } else {
      emit('importBpmn', content, file.name);
    }
  } catch (error) {
    antMessage.error(error instanceof Error ? error.message : '文件读取失败');
  } finally {
    uploading.value = false;
  }
}
</script>

<template>
  <label class="import-trigger">
    <input type="file" accept=".bpmn,.xml,.json" style="display: none" @change="onFileChange" />
    <a-button size="small" :loading="uploading">导入</a-button>
  </label>
</template>

<style scoped>
.import-trigger {
  display: inline-flex;
  cursor: pointer;
}
</style>
