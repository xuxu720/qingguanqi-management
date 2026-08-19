package com.qingguanqi.agent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * DeepSeek Chat Completions request/response DTOs (OpenAI-compatible format)
 */
public class DeepSeekDTO {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatRequest {
        private String model = "deepseek-chat";
        private List<Message> messages;
        private double temperature = 0.3;
        private Integer maxTokens;
        private ResponseFormat responseFormat;

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Message {
            private String role;
            private String content;
        }

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class ResponseFormat {
            private String type;
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ChatResponse {
        private String id;
        private List<Choice> choices;

        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Choice {
            private int index;
            private ResponseMessage message;
            private String finishReason;
        }

        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class ResponseMessage {
            private String role;
            private String content;
        }

        public String getContent() {
            if (choices != null && !choices.isEmpty()) {
                return choices.get(0).message.getContent();
            }
            return null;
        }
    }
}
