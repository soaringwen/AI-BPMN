export type NodeType =
  | 'START_EVENT'
  | 'END_EVENT'
  | 'USER_TASK'
  | 'SERVICE_TASK'
  | 'MANUAL_TASK'
  | 'BUSINESS_RULE_TASK'
  | 'EXCLUSIVE_GATEWAY'
  | 'PARALLEL_GATEWAY'
  | 'INCLUSIVE_GATEWAY'
  | 'SUB_PROCESS'
  | 'INTERMEDIATE_CATCH_EVENT'
  | 'INTERMEDIATE_THROW_EVENT';

export interface ProcessInfo {
  id: string;
  name: string;
  description: string | null;
  executable: boolean;
}

export interface Pool {
  id: string;
  name: string;
  processRef: string;
}

export interface Lane {
  id: string;
  name: string;
  poolId: string | null;
  nodeRefs: string[];
}

export interface FlowNode {
  id: string;
  type: NodeType;
  name: string | null;
  laneId: string | null;
  documentation: string | null;
  properties: Record<string, unknown>;
}

export interface SequenceFlow {
  id: string;
  sourceId: string;
  targetId: string;
  name: string | null;
  condition: string | null;
  defaultFlow: boolean;
}

export interface MessageFlow {
  id: string;
  sourceId: string;
  targetId: string;
  name: string | null;
}

export interface Point {
  x: number;
  y: number;
}

export interface Bounds {
  x: number;
  y: number;
  width: number;
  height: number;
}

export interface Layout {
  nodes: Record<string, Bounds>;
  flows: Record<string, Point[]>;
}

export interface ModelMetadata {
  createdBy: string | null;
  generationPrompt: string | null;
}

export interface ProcessModel {
  schemaVersion: string;
  modelId: string;
  process: ProcessInfo;
  pools: Pool[];
  lanes: Lane[];
  nodes: FlowNode[];
  sequenceFlows: SequenceFlow[];
  messageFlows: MessageFlow[];
  layout: Layout;
  metadata: ModelMetadata;
}

export interface ValidationIssue {
  code: string;
  severity: 'ERROR' | 'WARNING';
  message: string;
  elementId: string | null;
}

export interface ValidationResult {
  valid: boolean;
  errors: ValidationIssue[];
  warnings: ValidationIssue[];
}
