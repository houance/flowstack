package com.flowstack.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.flowstack.server.model.db.FlowDefinitionEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FlowDefinitionMapper extends BaseMapper<FlowDefinitionEntity> {
}
