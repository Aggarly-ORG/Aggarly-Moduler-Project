package com.luna.aggarly.aiagent.mapper;

import com.luna.aggarly.aiagent.dto.ToolCallLogResponse;
import com.luna.aggarly.aiagent.entity.AiToolInvocation;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AiMessageMapper {

    ToolCallLogResponse toLogResponse(AiToolInvocation invocation);
}
