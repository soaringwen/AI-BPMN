package com.bank.aibpmn.domain.operation;

/**
 * AI增量修改白名单操作类型（DESIGN.md 3.3 / 7.3）。
 */
public enum OperationType {
    ADD_NODE,
    UPDATE_NODE,
    DELETE_NODE,
    MOVE_NODE,
    ADD_FLOW,
    UPDATE_FLOW,
    DELETE_FLOW,
    ADD_POOL,
    UPDATE_POOL,
    DELETE_POOL,
    ADD_LANE,
    UPDATE_LANE,
    DELETE_LANE,
    ADD_BRANCH,
    ADD_PARALLEL_BRANCH
}
