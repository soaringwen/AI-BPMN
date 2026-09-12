<script setup lang="ts">
import { nextTick, ref, watch } from 'vue';
import { message as antMessage } from 'ant-design-vue';
import ChatMessage from './ChatMessage.vue';
import ChangePreview from '@/components/ChangePreview/ChangePreview.vue';
import { useWorkspaceStore } from '@/stores/workspaceStore';
import {
  clarifyProcess,
  explainProcess,
  generateProcess,
  modifyPreview,
  optimizeProcess,
  semanticCheck
} from '@/api/aiBpmnApi';
import { toErrorMessage } from '@/api/client';
import type { ClarificationQuestion } from '@/types/workspace';

const store = useWorkspaceStore();

const input = ref('');
const loading = ref(false);
const clarification = ref<{
  originalDescription: string;
  answers: Record<string, string>;
  questions: ClarificationQuestion[];
} | null>(null);

const messagesContainer = ref<HTMLDivElement | null>(null);

watch(
  () => store.conversation.length,
  async () => {
    await nextTick();
    messagesContainer.value?.scrollTo({ top: messagesContainer.value.scrollHeight });
  }
);

async function send(): Promise<void> {
  const text = input.value.trim();
  if (!text || loading.value) {
    return;
  }

  store.pushMessage('USER', text);
  input.value = '';
  loading.value = true;

  try {
    if (shouldCreateNew(text)) {
      await handleCreate(text);
    } else if (!store.hasModel) {
      await handleCreate(text);
    } else {
      await handleModify(text);
    }
  } catch (error) {
    antMessage.error(toErrorMessage(error));
    store.pushMessage('SYSTEM', `请求失败：${toErrorMessage(error)}`);
  } finally {
    loading.value = false;
  }
}

/**
 * 意图路由：明确要求"生成/创建/新建流程"，且当前模型实质为空（无任何连线，
 * 例如只手工拖了孤立节点）时，走新建流程而非修改。
 */
function shouldCreateNew(text: string): boolean {
  const wantsCreate = /生成|创建|新建|画一个|做一个/.test(text) && /流程/.test(text);
  if (!wantsCreate || !store.processModel) {
    return !store.hasModel && wantsCreate;
  }
  const modelIsEmpty = store.processModel.sequenceFlows.length === 0;
  return modelIsEmpty;
}

async function handleCreate(description: string): Promise<void> {
  const response = await generateProcess({ description, conversationContext: [] });

  if (response.status === 'CLARIFICATION_REQUIRED') {
    clarification.value = {
      originalDescription: response.clarificationContext?.originalDescription ?? description,
      answers: {},
      questions: response.questions
    };
    store.pushMessage('ASSISTANT', `请先回答以下澄清问题（共${response.questions.length}个）：`);
    response.questions.forEach(q => store.pushMessage('ASSISTANT', `· ${q.question}`));
    return;
  }

  store.processModel = response.processModel;
  store.bpmnXml = response.bpmnXml;
  store.validation = response.validation;
  store.createSnapshot('AI_CREATE', response.summary ?? 'AI创建流程');
  store.pushMessage('ASSISTANT', response.summary ?? '已生成流程。');
}

async function handleModify(instruction: string): Promise<void> {
  const lower = instruction.toLowerCase();
  const consultIntent =
    lower.startsWith('解释') || lower.startsWith('说明')
      ? 'EXPLAIN'
      : lower.startsWith('优化') || lower.startsWith('建议')
        ? 'OPTIMIZE'
        : lower.startsWith('检查') || lower.startsWith('校验')
          ? 'SEMANTIC_CHECK'
          : null;

  if (consultIntent && store.processModel) {
    const consult =
      consultIntent === 'EXPLAIN'
        ? explainProcess(store.processModel, instruction, store.conversation)
        : consultIntent === 'OPTIMIZE'
          ? optimizeProcess(store.processModel, instruction, store.conversation)
          : semanticCheck(store.processModel, instruction, store.conversation);
    const result = await consult;
    store.pushMessage('ASSISTANT', result.answer);
    result.suggestions.forEach(s => store.pushMessage('ASSISTANT', `建议：${s}`));
    return;
  }

  const response = await modifyPreview({
    baseRevision: store.revision,
    instruction,
    currentModel: store.processModel!,
    selectedElementIds: selectedElementIds.value,
    conversationContext: []
  });

  if (response.clarificationRequired) {
    store.pushMessage('ASSISTANT', `需要澄清：${response.questions.map(q => q.question).join('；')}`);
    return;
  }

  store.pendingChange = {
    baseRevision: response.baseRevision,
    summary: response.summary,
    proposedModel: response.proposedModel!,
    proposedBpmnXml: response.proposedBpmnXml!,
    changes: response.changes,
    validation: response.validation
  };
  store.pushMessage('ASSISTANT', `已生成修改预览：${response.summary}，请在下方确认。`);
}

const selectedElementIds = ref<string[]>([]);

function onSelectionChanged(ids: string[]): void {
  selectedElementIds.value = ids;
}

defineExpose({ onSelectionChanged });

async function submitClarification(): Promise<void> {
  if (!clarification.value) {
    return;
  }
  loading.value = true;
  try {
    const answers = clarification.value.questions.map(q => ({
      questionId: q.questionId,
      question: q.question,
      answer: clarification.value!.answers[q.questionId] ?? ''
    }));
    const response = await clarifyProcess({
      originalDescription: clarification.value.originalDescription,
      answers,
      conversationContext: []
    });

    if (response.status === 'CLARIFICATION_REQUIRED') {
      clarification.value = {
        originalDescription: response.clarificationContext?.originalDescription
          ?? clarification.value.originalDescription,
        answers: {},
        questions: response.questions
      };
      store.pushMessage('ASSISTANT', '仍需要补充信息：');
      response.questions.forEach(q => store.pushMessage('ASSISTANT', `· ${q.question}`));
      return;
    }

    clarification.value = null;
    store.processModel = response.processModel;
    store.bpmnXml = response.bpmnXml;
    store.validation = response.validation;
    store.createSnapshot('AI_CREATE', response.summary ?? 'AI创建流程');
    store.pushMessage('ASSISTANT', response.summary ?? '已生成流程。');
  } catch (error) {
    antMessage.error(toErrorMessage(error));
  } finally {
    loading.value = false;
  }
}

async function applyPendingChange(): Promise<void> {
  const pending = store.pendingChange;
  if (!pending) {
    return;
  }
  // 过期revision保护（DESIGN.md 20）
  if (pending.baseRevision !== store.revision) {
    antMessage.warning('AI响应已过期（revision变化），已丢弃该修改');
    store.pendingChange = null;
    return;
  }
  store.bpmnXml = pending.proposedBpmnXml;
  store.processModel = pending.proposedModel;
  store.createSnapshot('AI_MODIFY', pending.summary);
  store.pendingChange = null;
  store.pushMessage('ASSISTANT', `已应用修改：${pending.summary}`);
}

function discardPendingChange(): void {
  store.pendingChange = null;
  store.pushMessage('SYSTEM', '已放弃本次AI修改，当前流程保持不变。');
}
</script>

<template>
  <div class="ai-chat-panel">
    <div class="ai-chat-panel__header">AI 流程助手</div>

    <div ref="messagesContainer" class="ai-chat-panel__messages">
      <template v-if="store.conversation.length === 0">
        <a-empty description="输入流程描述开始建模，例如：架构师准备材料，提交Design Lead确认，通过后归档" />
      </template>
      <ChatMessage v-for="msg in store.conversation" :key="msg.id" :message="msg" />
    </div>

    <div v-if="clarification" class="ai-chat-panel__clarification">
      <a-alert type="info" show-icon message="请回答澄清问题" />
      <div v-for="q in clarification.questions" :key="q.questionId" class="clarification-item">
        <div class="clarification-item__question">{{ q.question }}</div>
        <a-select
          v-if="q.options.length > 0"
          v-model:value="clarification.answers[q.questionId]"
          :options="q.options.map(o => ({ label: o, value: o }))"
          placeholder="请选择"
          style="width: 100%"
        />
        <a-input v-else v-model:value="clarification.answers[q.questionId]" placeholder="请输入答案" />
      </div>
      <a-button type="primary" size="small" :loading="loading" @click="submitClarification">提交答案并生成</a-button>
    </div>

    <ChangePreview
      v-if="store.pendingChange"
      :preview="store.pendingChange"
      @apply="applyPendingChange"
      @discard="discardPendingChange"
    />

    <div class="ai-chat-panel__input">
      <a-textarea
        v-model:value="input"
        :rows="2"
        :disabled="loading"
        :placeholder="store.hasModel ? '描述修改需求，例如：审批不通过时退回申请人' : '描述一个流程，例如：员工提交报销单，主管审批，通过后财务打款'"
        @keydown.enter.exact.prevent="send"
      />
      <a-button type="primary" :loading="loading" @click="send">发送</a-button>
    </div>
  </div>
</template>

<style scoped>
.ai-chat-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  border-left: 1px solid #f0f0f0;
  background: #fff;
}

.ai-chat-panel__header {
  padding: 10px 12px;
  font-weight: 600;
  border-bottom: 1px solid #f0f0f0;
}

.ai-chat-panel__messages {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
}

.ai-chat-panel__clarification {
  padding: 10px 12px;
  border-top: 1px solid #f0f0f0;
  background: #fafafa;
}

.clarification-item {
  margin: 8px 0;
}

.clarification-item__question {
  font-size: 13px;
  margin-bottom: 4px;
}

.ai-chat-panel__input {
  display: flex;
  gap: 8px;
  padding: 10px 12px;
  border-top: 1px solid #f0f0f0;
  align-items: flex-end;
}
</style>
