import type { ProcessModel, ValidationResult } from '@/types/processModel';
import type { ConversationMessage, WorkspaceSnapshot } from '@/types/workspace';

/**
 * 工作区JSON导入导出：完整恢复对话、快照与模型。
 */
export interface WorkspaceFile {
  version: string;
  exportedAt: string;
  name: string;
  revision: number;
  processModel: ProcessModel | null;
  bpmnXml: string | null;
  conversation: ConversationMessage[];
  history: WorkspaceSnapshot[];
  historyIndex: number;
  validation: ValidationResult | null;
}

export function buildWorkspaceFile(input: {
  name: string;
  revision: number;
  processModel: ProcessModel | null;
  bpmnXml: string | null;
  conversation: ConversationMessage[];
  history: WorkspaceSnapshot[];
  historyIndex: number;
  validation: ValidationResult | null;
}): WorkspaceFile {
  return {
    version: '1.0',
    exportedAt: new Date().toISOString(),
    ...input
  };
}

export function downloadFile(filename: string, content: string, mime: string): void {
  const blob = new Blob([content], { type: mime });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = filename;
  anchor.click();
  URL.revokeObjectURL(url);
}

export function safeFileName(name: string): string {
  const cleaned = (name || '流程').replace(/[\\/:*?"<>|]/g, '_');
  return cleaned;
}

export function readTextFile(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result ?? ''));
    reader.onerror = () => reject(new Error('文件读取失败'));
    reader.readAsText(file, 'utf-8');
  });
}

export function parseWorkspaceFile(content: string): WorkspaceFile {
  const parsed = JSON.parse(content) as WorkspaceFile;
  if (!parsed || parsed.version !== '1.0') {
    throw new Error('不支持的工作区文件版本');
  }
  return parsed;
}
