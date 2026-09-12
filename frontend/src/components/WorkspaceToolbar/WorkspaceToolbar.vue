<script setup lang="ts">
import { ref } from 'vue';
import { message as antMessage } from 'ant-design-vue';
import { useWorkspaceStore } from '@/stores/workspaceStore';
import { parseXml } from '@/api/bpmnApi';
import { toErrorMessage } from '@/api/client';
import ImportExportPanel from '@/components/ImportExport/ImportExportPanel.vue';
import {
  buildWorkspaceFile,
  downloadFile,
  parseWorkspaceFile,
  safeFileName
} from '@/services/workspaceFileService';

const store = useWorkspaceStore();

const emit = defineEmits<{
  exportBpmn: [];
  versionHistory: [];
}>();

const editingName = ref(false);

async function onImportBpmn(xml: string): Promise<void> {
  try {
    const result = await parseXml(xml);
    store.processModel = result.processModel;
    store.bpmnXml = xml;
    store.validation = result.validation;
    store.createSnapshot('IMPORT', '导入BPMN文件');
    antMessage.success('BPMN导入成功');
  } catch (error) {
    antMessage.error(`导入失败：${toErrorMessage(error)}`);
  }
}

async function onImportWorkspace(content: string): Promise<void> {
  try {
    const workspace = parseWorkspaceFile(content);
    store.reset();
    store.name = workspace.name;
    store.processModel = workspace.processModel;
    store.bpmnXml = workspace.bpmnXml;
    store.validation = workspace.validation;
    store.history = workspace.history ?? [];
    store.historyIndex = workspace.historyIndex ?? -1;
    store.conversation = workspace.conversation ?? [];
    store.revision = workspace.revision ?? 0;
    antMessage.success('工作区导入成功');
  } catch (error) {
    antMessage.error(`工作区导入失败：${toErrorMessage(error)}`);
  }
}

async function exportWorkspace(): Promise<void> {
  const file = buildWorkspaceFile({
    name: store.name,
    revision: store.revision,
    processModel: store.processModel,
    bpmnXml: store.bpmnXml,
    conversation: store.conversation,
    history: store.history,
    historyIndex: store.historyIndex,
    validation: store.validation
  });
  downloadFile(`${safeFileName(store.name)}-workspace.json`, JSON.stringify(file, null, 2), 'application/json');
  antMessage.success('工作区JSON已导出');
}

function undo(): void {
  store.undo();
}

function redo(): void {
  store.redo();
}

defineExpose({ exportWorkspace });
</script>

<template>
  <div class="workspace-toolbar">
    <a-input
      v-if="editingName"
      v-model:value="store.name"
      size="small"
      style="width: 220px"
      @blur="editingName = false"
      @pressEnter="editingName = false"
    />
    <span v-else class="workspace-toolbar__name" @click="editingName = true">{{ store.name }}</span>

    <a-divider type="vertical" />

    <a-button size="small" :disabled="!store.canUndo" @click="undo">撤销</a-button>
    <a-button size="small" :disabled="!store.canRedo" @click="redo">恢复</a-button>

    <a-divider type="vertical" />

    <ImportExportPanel @import-bpmn="onImportBpmn" @import-workspace="onImportWorkspace" />
    <a-button size="small" @click="emit('versionHistory')">版本历史</a-button>
    <a-button size="small" @click="emit('exportBpmn')">导出BPMN</a-button>
    <a-button size="small" @click="exportWorkspace">导出工作区</a-button>

    <a-divider type="vertical" />
    <span class="workspace-toolbar__meta">R{{ store.revision }}{{ store.dirty ? ' · 未保存' : '' }}</span>
  </div>
</template>

<style scoped>
.workspace-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  border-bottom: 1px solid #f0f0f0;
  background: #fff;
}

.workspace-toolbar__name {
  font-weight: 600;
  cursor: pointer;
  padding: 2px 8px;
  border-radius: 4px;
}

.workspace-toolbar__name:hover {
  background: #f5f5f5;
}

.workspace-toolbar__meta {
  color: #999;
  font-size: 12px;
}
</style>
