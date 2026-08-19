package com.qingguanqi.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeepSeekClient {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper;

    private static final String DEFAULT_BASE_URL = "https://api.deepseek.com/v1";
    private static final String CHAT_PATH = "/chat/completions";

    public String chat(String systemPrompt, String userMessage, List<DeepSeekDTO.ChatRequest.Message> history,
                       String apiKey, String apiBaseUrl) {
        String baseUrl = apiBaseUrl != null && !apiBaseUrl.isBlank() ? apiBaseUrl : DEFAULT_BASE_URL;
        String url = baseUrl.endsWith("/") ? baseUrl + "chat/completions" : baseUrl + CHAT_PATH;

        DeepSeekDTO.ChatRequest request = new DeepSeekDTO.ChatRequest();
        request.setModel("deepseek-chat");
        request.setTemperature(0.3);
        request.setMaxTokens(2000);

        DeepSeekDTO.ChatRequest.ResponseFormat rf = new DeepSeekDTO.ChatRequest.ResponseFormat();
        rf.setType("json_object");
        request.setResponseFormat(rf);

        List<DeepSeekDTO.ChatRequest.Message> messages = new java.util.ArrayList<>();
        messages.add(new DeepSeekDTO.ChatRequest.Message("system", systemPrompt));
        if (history != null) {
            messages.addAll(history);
        }
        messages.add(new DeepSeekDTO.ChatRequest.Message("user", userMessage));
        request.setMessages(messages);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        try {
            String body = objectMapper.writeValueAsString(request);
            HttpEntity<String> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                DeepSeekDTO.ChatResponse chatResponse = objectMapper.readValue(response.getBody(), DeepSeekDTO.ChatResponse.class);
                String content = chatResponse.getContent();
                if (content != null) {
                    // Strip markdown code fences if present
                    content = content.trim();
                    if (content.startsWith("```")) {
                        int start = content.indexOf('\n');
                        if (start > 0) {
                            int end = content.lastIndexOf("```");
                            content = end > start ? content.substring(start, end).trim() : content.substring(start).trim();
                        }
                    }
                }
                return content;
            }
            log.error("DeepSeek API error: status={}, body={}", response.getStatusCode(), response.getBody());
            return null;
        } catch (RestClientException e) {
            log.error("DeepSeek API call failed: {}", e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("Unexpected error calling DeepSeek", e);
            return null;
        }
    }
}
