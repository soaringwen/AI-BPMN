package com.bank.aibpmn.infrastructure.dialect;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 方言适配器注册中心（DESIGN.md 17.3）。
 */
@Component
public class BpmnDialectAdapterRegistry {

    private final Map<BpmnDialect, BpmnDialectAdapter> adapters;

    public BpmnDialectAdapterRegistry(List<BpmnDialectAdapter> adapterList) {
        this.adapters = adapterList.stream()
            .collect(Collectors.toUnmodifiableMap(BpmnDialectAdapter::targetDialect, Function.identity()));
    }

    public Optional<BpmnDialectAdapter> find(BpmnDialect dialect) {
        return Optional.ofNullable(adapters.get(dialect));
    }
}
