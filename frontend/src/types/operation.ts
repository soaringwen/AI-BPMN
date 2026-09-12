export type OperationType =
  | 'ADD_NODE'
  | 'UPDATE_NODE'
  | 'DELETE_NODE'
  | 'MOVE_NODE'
  | 'ADD_FLOW'
  | 'UPDATE_FLOW'
  | 'DELETE_FLOW'
  | 'ADD_POOL'
  | 'UPDATE_POOL'
  | 'DELETE_POOL'
  | 'ADD_LANE'
  | 'UPDATE_LANE'
  | 'DELETE_LANE'
  | 'ADD_BRANCH'
  | 'ADD_PARALLEL_BRANCH';

export interface ModelOperation {
  operationId: string;
  type: OperationType;
  targetId: string | null;
  payload: Record<string, unknown>;
}

export interface ElementChange {
  elementId: string;
  elementType: string | null;
  name: string | null;
}

export interface ModelDiff {
  added: ElementChange[];
  updated: ElementChange[];
  deleted: ElementChange[];
  flowsAdded: ElementChange[];
  flowsUpdated: ElementChange[];
  flowsDeleted: ElementChange[];
}
