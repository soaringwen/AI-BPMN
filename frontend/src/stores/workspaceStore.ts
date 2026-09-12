import { computed, ref } from 'vue';
import { defineStore } from 'pinia';
import type { ProcessModel, ValidationResult } from '@/types/processModel';
import type { ChangePreview, ConversationMessage, WorkspaceSnapshot } from '@/types/workspace';

const MAX_HISTORY = Number(import.meta.env.VITE_MAX_HISTORY_SIZE || 20);

/** ProcessModel为纯数据结构，使用JSON深拷贝以避免响应式Proxy无法structuredClone的问题 */
function deepClone<T>(value: T): T {
  return JSON.parse(JSON.stringify(value)) as T;
}

export const useWorkspaceStore = defineStore('workspace', () => {
  const workspaceId = ref<string>(crypto.randomUUID());
  const name = ref('未命名流程');
  const revision = ref(0);

  const processModel = ref<ProcessModel | null>(null);
  const bpmnXml = ref<string | null>(null);
  const validation = ref<ValidationResult | null>(null);
  const conversation = ref<ConversationMessage[]>([]);
  const pendingChange = ref<ChangePreview | null>(null);

  const history = ref<WorkspaceSnapshot[]>([]);
  const historyIndex = ref(-1);
  const dirty = ref(false);
  const lastModifiedAt = ref(new Date().toISOString());

  const canUndo = computed(() => historyIndex.value > 0);
  const canRedo = computed(() => historyIndex.value < history.value.length - 1);
  const hasModel = computed(() => processModel.value !== null && bpmnXml.value !== null);

  function pushMessage(role: ConversationMessage['role'], content: string): void {
    conversation.value.push({
      id: crypto.randomUUID(),
      role,
      content,
      createdAt: new Date().toISOString()
    });
  }

  function createSnapshot(source: WorkspaceSnapshot['source'], summary: string): void {
    if (!processModel.value || !bpmnXml.value) {
      return;
    }
    const nextRevision = revision.value + 1;
    const snapshot: WorkspaceSnapshot = {
      revision: nextRevision,
      source,
      summary,
      processModel: deepClone(processModel.value),
      bpmnXml: bpmnXml.value,
      createdAt: new Date().toISOString()
    };

    history.value = history.value.slice(0, historyIndex.value + 1);
    history.value.push(snapshot);

    if (history.value.length > MAX_HISTORY) {
      history.value.shift();
    }

    historyIndex.value = history.value.length - 1;
    revision.value = nextRevision;
    dirty.value = true;
    lastModifiedAt.value = new Date().toISOString();
  }

  function applySnapshot(snapshot: WorkspaceSnapshot): void {
    processModel.value = deepClone(snapshot.processModel);
    bpmnXml.value = snapshot.bpmnXml;
    revision.value = snapshot.revision;
    pendingChange.value = null;
    lastModifiedAt.value = new Date().toISOString();
  }

  function undo(): void {
    if (!canUndo.value) {
      return;
    }
    historyIndex.value -= 1;
    applySnapshot(history.value[historyIndex.value]);
  }

  function redo(): void {
    if (!canRedo.value) {
      return;
    }
    historyIndex.value += 1;
    applySnapshot(history.value[historyIndex.value]);
  }

  function reset(): void {
    workspaceId.value = crypto.randomUUID();
    name.value = '未命名流程';
    revision.value = 0;
    processModel.value = null;
    bpmnXml.value = null;
    validation.value = null;
    conversation.value = [];
    pendingChange.value = null;
    history.value = [];
    historyIndex.value = -1;
    dirty.value = false;
  }

  return {
    workspaceId,
    name,
    revision,
    processModel,
    bpmnXml,
    validation,
    conversation,
    pendingChange,
    history,
    historyIndex,
    dirty,
    lastModifiedAt,
    canUndo,
    canRedo,
    hasModel,
    pushMessage,
    createSnapshot,
    applySnapshot,
    undo,
    redo,
    reset
  };
});
