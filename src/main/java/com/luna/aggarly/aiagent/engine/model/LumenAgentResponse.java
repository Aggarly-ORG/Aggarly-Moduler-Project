package com.luna.aggarly.aiagent.engine.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LumenAgentResponse {

    @Builder.Default
    private String version = "1";

    @Builder.Default
    private List<LumenResponseBlock> blocks = new ArrayList<>();

    public static LumenAgentResponse singleTextBlock(String text) {
        return LumenAgentResponse.builder()
                .version("1")
                .blocks(List.of(LumenResponseBlock.text(text)))
                .build();
    }

    public static LumenAgentResponse of(List<LumenResponseBlock> blocks) {
        return LumenAgentResponse.builder()
                .version("1")
                .blocks(blocks != null ? blocks : new ArrayList<>())
                .build();
    }
}
