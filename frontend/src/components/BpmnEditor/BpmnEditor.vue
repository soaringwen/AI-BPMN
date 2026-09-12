<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue';
import BpmnModeler from 'bpmn-js/lib/Modeler';
import 'bpmn-js/dist/assets/diagram-js.css';
import 'bpmn-js/dist/assets/bpmn-js.css';
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn-embedded.css';

const props = defineProps<{
  xml: string | null;
}>();

const emit = defineEmits<{
  changed: [xml: string];
  imported: [];
  error: [message: string];
  selectionChanged: [elementIds: string[]];
}>();

const canvasContainer = ref<HTMLDivElement | null>(null);

let modeler: BpmnModeler | null = null;
let importingXml = false;
/** 导入进行中到达的新XML，导入完成后串行执行，避免并发导入竞态 */
let pendingXml: string | null = null;
/** 编辑器当前XML（含编辑器自身导出），用于防止store更新触发重复导入循环保存 */
let lastXml: string | null = null;

/** 空白流程图：保证空工作区下也能从调色板拖拽创建节点 */
const EMPTY_DIAGRAM_XML = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                  xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
                  xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
                  xmlns:di="http://www.omg.org/spec/DD/20100524/DI"
                  id="Definitions_blank" targetNamespace="urn:bank:ai-bpmn">
  <bpmn:process id="Process_blank" isExecutable="false" />
  <bpmndi:BPMNDiagram id="BPMNDiagram_blank">
    <bpmndi:BPMNPlane id="BPMNPlane_blank" bpmnElement="Process_blank" />
  </bpmndi:BPMNDiagram>
</bpmn:definitions>`;

onMounted(async () => {
  modeler = new BpmnModeler({
    container: canvasContainer.value!
  });

  modeler.on('commandStack.changed', async () => {
    if (!modeler || importingXml) {
      return;
    }
    try {
      const result = await modeler.saveXML({ format: true });
      if (result.xml) {
        lastXml = result.xml;
        emit('changed', result.xml);
      }
    } catch (error) {
      emit('error', error instanceof Error ? error.message : 'BPMN保存失败');
    }
  });

  const eventBus = modeler.get<any>('eventBus');
  eventBus.on('selection.changed', (event: any) => {
    const ids: string[] = (event.newSelection ?? []).map((el: any) => el.id);
    emit('selectionChanged', ids);
  });

  await importXml(props.xml ?? EMPTY_DIAGRAM_XML);
});

// 外部XML变化（AI生成/导入/撤销恢复）时重新导入；与编辑器自身导出相同的XML不重复导入
watch(
  () => props.xml,
  async newXml => {
    if (!modeler || !newXml || newXml === lastXml) {
      return;
    }
    await importXml(newXml);
  }
);

async function importXml(xml: string): Promise<void> {
  if (!modeler) {
    return;
  }
  // 串行化：导入中到达的XML排队等待，避免并发导入竞态
  if (importingXml) {
    pendingXml = xml;
    return;
  }
  importingXml = true;
  try {
    await modeler.importXML(xml);
    lastXml = xml;
    const canvas = modeler.get<any>('canvas');
    canvas.zoom('fit-viewport');
    emit('imported');
  } catch (error) {
    emit('error', error instanceof Error ? error.message : 'BPMN XML导入失败');
  } finally {
    importingXml = false;
    if (pendingXml && pendingXml !== lastXml) {
      const next = pendingXml;
      pendingXml = null;
      await importXml(next);
    } else {
      pendingXml = null;
    }
  }
}

async function exportXml(): Promise<string> {
  if (!modeler) {
    throw new Error('BPMN编辑器尚未初始化');
  }
  const result = await modeler.saveXML({ format: true });
  return result.xml ?? '';
}

async function exportSvg(): Promise<string> {
  if (!modeler) {
    throw new Error('BPMN编辑器尚未初始化');
  }
  const result = await modeler.saveSVG();
  return result.svg;
}

function zoomIn(): void {
  modeler?.get<any>('canvas').zoom(1.2);
}

function zoomOut(): void {
  modeler?.get<any>('canvas').zoom(0.8);
}

function fitViewport(): void {
  modeler?.get<any>('canvas').zoom('fit-viewport');
}

function undo(): void {
  modeler?.get<any>('commandStack').undo();
}

function redo(): void {
  modeler?.get<any>('commandStack').redo();
}

/** 高亮AI差异元素：新增绿框、修改橙框、待删除红虚线 */
function highlightElements(addedIds: string[], updatedIds: string[], deletedIds: string[]): void {
  if (!modeler) {
    return;
  }
  const canvas = modeler.get<any>('canvas');
  const all = [...addedIds, ...updatedIds, ...deletedIds];
  all.forEach(id => {
    try {
      canvas.removeMarker(id, 'ai-added');
      canvas.removeMarker(id, 'ai-updated');
      canvas.removeMarker(id, 'ai-deleted');
    } catch {
      /* 元素可能不存在 */
    }
  });
  addedIds.forEach(id => safeAddMarker(canvas, id, 'ai-added'));
  updatedIds.forEach(id => safeAddMarker(canvas, id, 'ai-updated'));
  deletedIds.forEach(id => safeAddMarker(canvas, id, 'ai-deleted'));
}

function clearHighlights(): void {
  if (!modeler) {
    return;
  }
  const canvas = modeler.get<any>('canvas');
  const registry = modeler.get<any>('elementRegistry');
  registry.getAll().forEach((el: any) => {
    canvas.removeMarker(el.id, 'ai-added');
    canvas.removeMarker(el.id, 'ai-updated');
    canvas.removeMarker(el.id, 'ai-deleted');
  });
}

function safeAddMarker(canvas: any, id: string, marker: string): void {
  try {
    canvas.addMarker(id, marker);
  } catch {
    /* 忽略不存在元素 */
  }
}

defineExpose({
  importXml,
  exportXml,
  exportSvg,
  zoomIn,
  zoomOut,
  fitViewport,
  undo,
  redo,
  highlightElements,
  clearHighlights
});

onBeforeUnmount(() => {
  modeler?.destroy();
  modeler = null;
});
</script>

<template>
  <div ref="canvasContainer" class="bpmn-canvas" />
</template>

<style scoped>
.bpmn-canvas {
  width: 100%;
  height: 100%;
  min-height: 480px;
}
</style>

<style>
/* AI差异高亮（全局样式，作用于diagram-js内部SVG） */
.ai-added .djs-visual > :nth-child(1) {
  stroke: #52c41a !important;
  stroke-width: 3px !important;
}

.ai-updated .djs-visual > :nth-child(1) {
  stroke: #fa8c16 !important;
  stroke-width: 3px !important;
}

.ai-deleted .djs-visual > :nth-child(1) {
  stroke: #ff4d4f !important;
  stroke-dasharray: 4 3 !important;
  stroke-width: 2px !important;
}
</style>
