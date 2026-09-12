import type { ProcessModel, ValidationResult } from './processModel';
import type { ModelDiff } from './operation';

export interface ConversationMessage {
  id: string;
  role: 'USER' | 'ASSISTANT' | 'SYSTEM';
  content: string;
  createdAt: string;
}

export interface WorkspaceSnapshot {
  revision: number;
  source: 'AI_CREATE' | 'AI_MODIFY' | 'MANUAL' | 'IMPORT' | 'RESTORE';
  summary: string;
  processModel: ProcessModel;
  bpmnXml: string;
  createdAt: string;
}

export interface ChangePreview {
  baseRevision: number;
  summary: string;
  proposedModel: ProcessModel;
  proposedBpmnXml: string;
  changes: ModelDiff;
  validation: ValidationResult;
}

export interface WorkspaceState {
  workspaceId: string;
  name: string;
  revision: number;
  processModel: ProcessModel | null;
  bpmnXml: string | null;
  conversation: ConversationMessage[];
  pendingChange: ChangePreview | null;
  history: WorkspaceSnapshot[];
  historyIndex: number;
  dirty: boolean;
  lastModifiedAt: string;
}

export interface ClarificationQuestion {
  questionId: string;
  question: string;
  type: 'SINGLE_SELECT' | 'TEXT' | string;
  options: string[];
  required: boolean;
}
