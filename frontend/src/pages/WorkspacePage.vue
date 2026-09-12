<script setup lang="ts">
import { ref } from 'vue';
import { message as antMessage } from 'ant-design-vue';
import BpmnEditor from '@/components/BpmnEditor/BpmnEditor.vue';
import BpmnToolbar from '@/components/BpmnEditor/BpmnToolbar.vue';
import AiChatPanel from '@/components/AiChatPanel/AiChatPanel.vue';
import ValidationPanel from '@/components/ValidationPanel/ValidationPanel.vue';
import WorkspaceToolbar from '@/components/WorkspaceToolbar/WorkspaceToolbar.vue';
import VersionHistory from '@/components/VersionHistory/VersionHistory.vue';
import { useWorkspaceStore } from '@/stores/workspaceStore';
import { downloadFile, safeFileName } from '@/services/workspaceFileService';
import { fullLayout, parseXml, validateModel } from '@/api/bpmnApi';
import { toErrorMessage } from '@/api/client';
import type { WorkspaceSnapshot } from '@/types/workspace';

const store = useWorkspaceStore();

const editorRef = ref<InstanceType<typeof BpmnEditor> | null>(null);
const workspaceToolbarRef = ref<InstanceType<typeof WorkspaceToolbar> | null>(null);
const showVersionHistory = ref(false);

let syncTimer: ReturnType<typeof setTimeout> | undefined;

function onEditorChanged(xml: string): void {
  store.bpmnXml = xml;
  store.dirty = true;
  // 手工编辑同步（DESIGN.md 14）：防抖解析为ProcessModel，失败时保留上一个有效模型
  if (syncTimer) {
    clearTimeout(syncTimer);
  }
  syncTimer = setTimeout(async () => {
    try {
      const result = await parseXml(xml);
      store.processModel = result.processModel;
      store.validation = result.validation;
    } catch (error) {
      antMessage.warning(`编辑同步失败：${toErrorMessage(error)}`);
    }
  }, 600);
}

function onEditorError(errorMessage: string): void {
  antMessage.error(errorMessage);
}

async function runValidation(): Promise<void> {
  if (!store.processModel) {
    antMessage.info('当前没有流程模型');
    return;
  }
  try {
    store.validation = await validateModel(store.processModel);
  } catch (error) {
    antMessage.error(toErrorMessage(error));
  }
}

async function onFullLayout(): Promise<void> {
  if (!store.processModel) {
    return;
  }
  try {
    const xml = await fullLayout(store.processModel);
    store.bpmnXml = xml;
    editorRef.value?.importXml(xml);
    store.createSnapshot('MANUAL', '全图自动布局');
  } catch (error) {
    antMessage.error(`自动布局失败：${toErrorMessage(error)}`);
  }
}

async function onExportBpmn(): Promise<void> {
  try {
    const xml = await editorRef.value!.exportXml();
    downloadFile(`${safeFileName(store.name)}.bpmn`, xml, 'application/xml');
    antMessage.success('BPMN已导出');
  } catch (error) {
    antMessage.error(toErrorMessage(error));
  }
}

function onExportSvg(): void {
  editorRef.value
    ?.exportSvg()
    .then(svg => downloadFile(`${safeFileName(store.name)}.svg`, svg, 'image/svg+xml'))
    .catch(error => antMessage.error(toErrorMessage(error)));
}

function restoreSnapshot(snapshot: WorkspaceSnapshot): void {
  store.applySnapshot(snapshot);
  store.createSnapshot('RESTORE', `恢复到版本R${snapshot.revision}`);
  showVersionHistory.value = false;
  antMessage.success(`已恢复到版本R${snapshot.revision}`);
}

function locateElement(elementId: string): void {
  // 通过导入刷新后无法定位；这里使用bpmn-js canvas跳转
  // 简化实现：放大画布并提示元素
  editorRef.value?.fitViewport();
  antMessage.info(`定位元素：${elementId}`);
}
</script>

<template>
  <div class="workspace-page">
    <WorkspaceToolbar
      ref="workspaceToolbarRef"
      @export-bpmn="onExportBpmn"
      @version-history="showVersionHistory = !showVersionHistory"
    />

    <div class="workspace-page__body">
      <div class="workspace-page__canvas-area">
        <div class="workspace-page__canvas-toolbar">
          <BpmnToolbar
            @zoom-in="editorRef?.zoomIn()"
            @zoom-out="editorRef?.zoomOut()"
            @fit="editorRef?.fitViewport()"
            @layout="onFullLayout"
          />
          <a-button size="small" @click="runValidation">校验</a-button>
          <a-button size="small" @click="onExportSvg">导出SVG</a-button>
        </div>
        <BpmnEditor
          ref="editorRef"
          :xml="store.bpmnXml"
          @changed="onEditorChanged"
          @error="onEditorError"
        />
      </div>

      <AiChatPanel class="workspace-page__chat" />

      <div v-if="showVersionHistory" class="workspace-page__history">
        <div class="workspace-page__history-header">
          <span>版本历史（最近20个快照）</span>
          <a-button size="small" type="text" @click="showVersionHistory = false">关闭</a-button>
        </div>
        <VersionHistory @restore="restoreSnapshot" />
      </div>
    </div>

    <ValidationPanel :validation="store.validation" @locate="locateElement" />
  </div>
</template>

<style scoped>
.workspace-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
}

.workspace-page__body {
  position: relative;
  display: flex;
  flex: 1;
  min-height: 0;
}

.workspace-page__canvas-area {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
}

.workspace-page__canvas-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 12px;
}

.workspace-page__chat {
  width: 380px;
  flex-shrink: 0;
}

.workspace-page__history {
  position: absolute;
  top: 8px;
  right: 396px;
  width: 320px;
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
  z-index: 10;
}

.workspace-page__history-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  font-weight: 600;
  border-bottom: 1px solid #f0f0f0;
}
</style>
