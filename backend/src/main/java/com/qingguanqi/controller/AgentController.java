package com.qingguanqi.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qingguanqi.agent.AgentService;
import com.qingguanqi.dto.AgentReply;
import com.qingguanqi.dto.AgentRequest;
import com.qingguanqi.dto.Result;
import com.qingguanqi.entity.Conversation;
import com.qingguanqi.entity.Message;
import com.qingguanqi.mapper.ConversationMapper;
import com.qingguanqi.mapper.MessageMapper;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final AgentService agentService;
    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;

    public AgentController(AgentService agentService,
                           ConversationMapper conversationMapper,
                           MessageMapper messageMapper) {
        this.agentService = agentService;
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
    }

    @PostMapping("/chat")
    public Result<AgentReply> chat(@RequestBody AgentRequest request,
                                   @RequestHeader(value = "X-API-Key", required = false) String apiKey,
                                   @RequestHeader(value = "X-API-Base-URL", required = false) String apiBaseUrl) {
        if (apiKey == null || apiKey.isBlank()) {
            return Result.fail(400, "请先配置 DeepSeek API Key（在智能助手页面点击右上角设置按钮）");
        }
        try {
            AgentReply reply = agentService.chat(request.getMessage(), request.getConversationId(), apiKey, apiBaseUrl);
            return Result.ok(reply);
        } catch (Exception e) {
            return Result.fail(500, "对话处理失败：" + e.getMessage());
        }
    }

    @GetMapping("/conversations")
    public Result<List<Conversation>> listConversations() {
        List<Conversation> list = conversationMapper.selectList(
                new LambdaQueryWrapper<Conversation>().orderByDesc(Conversation::getCreateTime));
        return Result.ok(list);
    }

    @GetMapping("/conversations/{id}/messages")
    public Result<List<Message>> getMessages(@PathVariable Long id) {
        List<Message> list = messageMapper.selectList(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getConversationId, id)
                        .orderByAsc(Message::getCreateTime));
        return Result.ok(list);
    }

    @DeleteMapping("/conversations/{id}")
    public Result<Void> deleteConversation(@PathVariable Long id) {
        messageMapper.delete(new LambdaQueryWrapper<Message>().eq(Message::getConversationId, id));
        conversationMapper.deleteById(id);
        return Result.ok();
    }
}
