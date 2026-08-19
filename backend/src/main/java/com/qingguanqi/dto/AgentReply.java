package com.qingguanqi.dto;

import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentReply {
    private String reply;
    private String intent;
    private Map<String, Object> data;
    private boolean needFollowUp;
    private Long conversationId;
    private List<Widget> widgets;
}
