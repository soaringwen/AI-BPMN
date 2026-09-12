import { beforeEach, describe, expect, it } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { useWorkspaceStore } from '@/stores/workspaceStore';
import type { ProcessModel } from '@/types/processModel';

function fakeModel(): ProcessModel {
  return {
    schemaVersion: '1.0',
    modelId: 'model_test',
    process: { id: 'process_test', name: '测试流程', description: null, executable: false },
    pools: [],
    lanes: [],
    nodes: [],
    sequenceFlows: [],
    messageFlows: [],
    layout: { nodes: {}, flows: {} },
    metadata: { createdBy: null, generationPrompt: null }
  };
}

describe('workspaceStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  it('创建快照推进revision并支持撤销恢复', () => {
    const store = useWorkspaceStore();
    store.processModel = fakeModel();
    store.bpmnXml = '<definitions/>';

    store.createSnapshot('AI_CREATE', '创建');
    expect(store.revision).toBe(1);
    expect(store.canUndo).toBe(false);
    expect(store.canRedo).toBe(false);

    store.bpmnXml = '<definitions v="2"/>';
    store.createSnapshot('MANUAL', '手工修改');
    expect(store.revision).toBe(2);

    store.undo();
    expect(store.bpmnXml).toBe('<definitions/>');
    expect(store.canRedo).toBe(true);

    store.redo();
    expect(store.bpmnXml).toBe('<definitions v="2"/>');
  });

  it('撤销后创建新快照会丢弃重做分支', () => {
    const store = useWorkspaceStore();
    store.processModel = fakeModel();
    store.bpmnXml = 'a';
    store.createSnapshot('AI_CREATE', 'r1');
    store.bpmnXml = 'b';
    store.createSnapshot('MANUAL', 'r2');
    store.undo();
    expect(store.history.length).toBe(2);
    store.bpmnXml = 'c';
    store.createSnapshot('MANUAL', 'r3');
    expect(store.history.length).toBe(2);
    expect(store.canRedo).toBe(false);
  });

  it('快照上限为20个', () => {
    const store = useWorkspaceStore();
    store.processModel = fakeModel();
    for (let i = 0; i < 25; i++) {
      store.bpmnXml = `xml-${i}`;
      store.createSnapshot('MANUAL', `rev-${i}`);
    }
    expect(store.history.length).toBe(20);
    expect(store.revision).toBe(25);
  });

  it('applySnapshot清除待确认修改', () => {
    const store = useWorkspaceStore();
    store.processModel = fakeModel();
    store.bpmnXml = 'x';
    store.createSnapshot('AI_CREATE', 'r1');
    store.bpmnXml = 'y';
    store.createSnapshot('MANUAL', 'r2');
    store.pendingChange = {
      baseRevision: 1,
      summary: 's',
      proposedModel: fakeModel(),
      proposedBpmnXml: 'y',
      changes: {
        added: [],
        updated: [],
        deleted: [],
        flowsAdded: [],
        flowsUpdated: [],
        flowsDeleted: []
      },
      validation: { valid: true, errors: [], warnings: [] }
    };
    store.undo();
    expect(store.bpmnXml).toBe('x');
    expect(store.pendingChange).toBeNull();
  });
});
