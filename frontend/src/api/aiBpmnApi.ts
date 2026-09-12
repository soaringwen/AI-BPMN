import { http } from './client';
import type { ProcessModel, ValidationResult } from '@/types/processModel';
import type { ChangePreview, ClarificationQuestion, ConversationMessage } from '@/types/workspace';
import type { ModelOperation } from '@/types/operation';

export interface ConversationContextMessage {
  role: 'USER' | 'ASSISTANT';
  content: string;
}

export interface GenerateRequest {
  name?: string;
  description: string;
  conversationContext: ConversationContextMessage[];
  generationOptions?: {
    includePool?: boolean;
    includeLanes?: boolean;
    askClarification?: boolean;
    maxClarificationQuestions?: number;
    targetDialect?: string;
  };
}

export interface ClarifyRequest {
  originalDescription: string;
  answers: { questionId: string; question: string; answer: string }[];
  conversationContext: ConversationContextMessage[];
  generationOptions?: GenerateRequest['generationOptions'];
}

export interface GenerateResponse {
  status: 'COMPLETED' | 'CLARIFICATION_REQUIRED';
  processModel: ProcessModel | null;
  bpmnXml: string | null;
  validation: ValidationResult;
  questions: ClarificationQuestion[];
  clarificationContext: {
    originalDescription: string;
    normalizedFacts: unknown[];
  } | null;
  summary: string | null;
}

export interface ModifyPreviewRequest {
  baseRevision: number;
  instruction: string;
  currentModel: ProcessModel;
  selectedElementIds: string[];
  conversationContext: ConversationContextMessage[];
}

export interface ModifyPreviewResponse {
  baseRevision: number;
  summary: string;
  clarificationRequired: boolean;
  questions: ClarificationQuestion[];
  proposedModel: ProcessModel | null;
  proposedBpmnXml: string | null;
  operations: ModelOperation[];
  changes: ChangePreview['changes'];
  validation: ValidationResult;
}

export interface ConsultResponse {
  answer: string;
  suggestions: string[];
}

function toContext(messages: ConversationMessage[], limit = 10): ConversationContextMessage[] {
  return messages.slice(-limit).map(m => ({ role: m.role === 'ASSISTANT' ? 'ASSISTANT' : 'USER', content: m.content }));
}

export async function generateProcess(request: GenerateRequest): Promise<GenerateResponse> {
  const { data } = await http.post<GenerateResponse>('/api/v1/ai/bpmn/generate', request);
  return data;
}

export async function clarifyProcess(request: ClarifyRequest): Promise<GenerateResponse> {
  const { data } = await http.post<GenerateResponse>('/api/v1/ai/bpmn/clarify', request);
  return data;
}

export async function modifyPreview(request: ModifyPreviewRequest): Promise<ModifyPreviewResponse> {
  const { data } = await http.post<ModifyPreviewResponse>('/api/v1/ai/bpmn/modify-preview', request);
  return data;
}

export async function explainProcess(
  currentModel: ProcessModel,
  instruction: string,
  conversation: ConversationMessage[]
): Promise<ConsultResponse> {
  const { data } = await http.post<ConsultResponse>('/api/v1/ai/bpmn/explain', {
    mode: 'EXPLAIN',
    instruction,
    currentModel,
    selectedElementIds: [],
    conversationContext: toContext(conversation)
  });
  return data;
}

export async function optimizeProcess(
  currentModel: ProcessModel,
  instruction: string,
  conversation: ConversationMessage[]
): Promise<ConsultResponse> {
  const { data } = await http.post<ConsultResponse>('/api/v1/ai/bpmn/optimize', {
    mode: 'OPTIMIZE',
    instruction,
    currentModel,
    selectedElementIds: [],
    conversationContext: toContext(conversation)
  });
  return data;
}

export async function semanticCheck(
  currentModel: ProcessModel,
  instruction: string,
  conversation: ConversationMessage[]
): Promise<ConsultResponse> {
  const { data } = await http.post<ConsultResponse>('/api/v1/ai/bpmn/semantic-check', {
    mode: 'SEMANTIC_CHECK',
    instruction,
    currentModel,
    selectedElementIds: [],
    conversationContext: toContext(conversation)
  });
  return data;
}
