package com.flowstack.server.controller;

import com.flowstack.server.model.api.global.FlowResponse;
import com.flowstack.server.model.api.info.FlowInfoDTO;
import com.flowstack.server.service.FlowMsgPersistService;
import com.flowstack.server.service.FlowScheduler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Slf4j
@CrossOrigin(originPatterns = "*")
@RequestMapping("/flow-info")
@RequiredArgsConstructor
public class FlowInfoController {
    private final FlowMsgPersistService flowMsgPersistService;
    private final FlowScheduler flowScheduler;

    @GetMapping("/get-all-flow-info")
    public FlowResponse<List<FlowInfoDTO>> getAllFlowInfo() {
        return FlowResponse.success(flowMsgPersistService.getAllFlowInfo());
    }

    @PostMapping("/enable-flow")
    public FlowResponse<Void> enableFlow(@RequestBody FlowInfoDTO flowInfoDTO) {
        this.flowScheduler.enableSchedule(flowInfoDTO.getFlowDefinitionId());
        return FlowResponse.success();
    }

    @PostMapping("/disable-flow")
    public FlowResponse<Void> disableFlow(@RequestBody FlowInfoDTO flowInfoDTO) {
        this.flowScheduler.stopSchedule(flowInfoDTO.getFlowDefinitionId());
        return FlowResponse.success();
    }

    @PostMapping("/delete-flow")
    public FlowResponse<Void> deleteFlow(@RequestBody FlowInfoDTO flowInfoDTO) {
        this.flowScheduler.deleteFlow(flowInfoDTO.getFlowDefinitionId());
        return FlowResponse.success();
    }
}
