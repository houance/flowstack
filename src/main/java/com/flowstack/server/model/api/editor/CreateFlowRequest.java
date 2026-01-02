package com.flowstack.server.model.api.editor;

import com.flowstack.server.core.model.definition.FlowNode;

import java.util.List;

public record CreateFlowRequest(
        String flowName,
        String cron,
        String description,
        List<FlowNode> nodeList,
        List<FieldSchemaDTO> fieldSchemaDTOList) {
}
