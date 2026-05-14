package ink.icoding.llm.core.model.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ink.icoding.llm.core.entity.Message;
import ink.icoding.llm.core.entity.MessageAttachment;
import ink.icoding.llm.core.model.LLMModel;
import ink.icoding.llm.core.model.LLMResult;
import ink.icoding.llm.core.model.ResultHandler;
import ink.icoding.llm.core.tool.Tool;
import ink.icoding.llm.core.tool.ToolDescriptor;
import ink.icoding.llm.core.tool.ToolExecutor;
import okhttp3.*;
import okhttp3.internal.http2.ErrorCode;
import okhttp3.internal.http2.StreamResetException;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import okhttp3.sse.EventSources;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Anthropic Messages API模型实现.
 * <p>通过SSE流式调用 {@code /v1/messages} 接口与Anthropic Claude模型交互.
 * 支持文本生成、思考(thinking)和工具调用(tool_use).
 * 内置Agent循环: 自动处理工具调用并将结果反馈给LLM, 直到返回文本响应.</p>
 *
 * @author gsk
 */
public class AnthropicModel implements LLMModel {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final String baseUrl;
    private final String modelName;
    private final String apiKey;
    private final OkHttpClient client;
    private final Map<String, Tool> toolMap = new ConcurrentHashMap<>();

    /**
     * 构造Anthropic模型实例.
     *
     * @param baseUrl   API基础地址, 如 https://api.anthropic.com
     * @param modelName 模型名称, 如 claude-sonnet-4-20250514
     * @param apiKey    API密钥
     */
    public AnthropicModel(String baseUrl, String modelName, String apiKey) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.modelName = modelName;
        this.apiKey = apiKey;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(300, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build();
    }

    /** {@inheritDoc} */
    @Override
    public LLMResult ask(Message message) {
        return ask(List.of(message));
    }

    /** {@inheritDoc} */
    @Override
    public LLMResult ask(List<Message> messages) {
        return ask(messages, List.of());
    }

    /** {@inheritDoc} */
    @Override
    public LLMResult ask(List<Message> messages, List<Tool> tools) {
        return ask(messages, tools, (toolName, paramJson, descriptor, handler) -> {
            Tool tool = toolMap.get(toolName);
            if (tool == null) throw new RuntimeException("Tool not found: " + toolName);
            return ToolExecutor.defaultExecute(tool, paramJson, descriptor, handler);
        });
    }

    /** {@inheritDoc} */
    @Override
    public LLMResult ask(List<Message> messages, List<Tool> tools, ToolExecutor toolExecutor) {
        List<ToolDescriptor> descriptors = new ArrayList<>();
        for (Tool tool : tools) {
            ToolDescriptor desc = ToolDescriptor.fromTool(tool);
            descriptors.add(desc);
            toolMap.put(desc.getName(), tool);
        }

        return new LLMResult(r -> executeAgentLoop(r, new ArrayList<>(messages), descriptors, toolExecutor));
    }

    /**
     * 执行Agent循环: LLM -> 工具调用 -> 反馈结果 -> LLM, 直到返回文本响应.
     */
    private void executeAgentLoop(LLMResult result, List<Message> messages,
                                   List<ToolDescriptor> tools, ToolExecutor toolExecutor) {
        try {
            ObjectNode body = buildRequestBody(messages, tools);
            Request request = new Request.Builder()
                    .url(baseUrl + "/v1/messages")
                    .post(RequestBody.create(body.toString(), MediaType.parse("application/json")))
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .build();

            EventSource.Factory factory = EventSources.createFactory(client);
            factory.newEventSource(request, new EventSourceListener() {
                private String currentBlockType;
                private String currentToolId;
                private String currentToolName;
                private final StringBuilder toolInputBuffer = new StringBuilder();
                private final StringBuilder contentBuffer = new StringBuilder();
                private final StringBuilder thinkBuffer = new StringBuilder();
                private final StringBuilder thinkSignatureBuffer = new StringBuilder();
                private final List<ToolCallEntry> toolCalls = new ArrayList<>();
                private String stopReason;
                private final AtomicBoolean cancelRequested = new AtomicBoolean(false);
                private final AtomicBoolean turnHandled = new AtomicBoolean(false);

                private void finishCurrentTurn() {
                    if (!turnHandled.compareAndSet(false, true)) {
                        return;
                    }
                    if (shouldContinueWithToolCalls(stopReason, toolCalls)) {
                        handleToolCallsAndContinue(result, messages, tools, toolExecutor,
                                contentBuffer.toString(), thinkBuffer.toString(),
                                thinkSignatureBuffer.toString(), new ArrayList<>(toolCalls));
                    } else {
                        result.complete(contentBuffer.toString());
                    }
                }

                @Override
                public void onEvent(EventSource eventSource, String id, String type, String data) {
                    try {
                        JsonNode json = MAPPER.readTree(data);
                        String eventType = json.has("type") ? json.get("type").asText() : type;

                        switch (eventType) {
                            case "content_block_start" -> {
                                JsonNode contentBlock = json.get("content_block");
                                if (contentBlock != null) {
                                    currentBlockType = contentBlock.get("type").asText();
                                    if ("tool_use".equals(currentBlockType)) {
                                        currentToolId = contentBlock.get("id").asText();
                                        currentToolName = contentBlock.get("name").asText();
                                        toolInputBuffer.setLength(0);
                                    } else if ("thinking".equals(currentBlockType) && contentBlock.has("signature")) {
                                        thinkSignatureBuffer.append(contentBlock.get("signature").asText());
                                    }
                                }
                            }
                            case "content_block_delta" -> {
                                JsonNode delta = json.get("delta");
                                if (delta != null) {
                                    String deltaType = delta.get("type").asText();
                                    ResultHandler handler = result.getHandler();
                                    switch (deltaType) {
                                        case "thinking_delta" -> {
                                            String thinking = delta.get("thinking").asText();
                                            thinkBuffer.append(thinking);
                                            if (handler != null) handler.onThink(thinking);
                                        }
                                        case "signature_delta" -> {
                                            if (delta.has("signature")) {
                                                thinkSignatureBuffer.append(delta.get("signature").asText());
                                            }
                                        }
                                        case "text_delta" -> {
                                            String text = delta.get("text").asText();
                                            contentBuffer.append(text);
                                            if (handler != null) handler.onMessage(text);
                                        }
                                        case "input_json_delta" -> {
                                            toolInputBuffer.append(delta.get("partial_json").asText());
                                        }
                                    }
                                }
                            }
                            case "content_block_stop" -> {
                                if ("tool_use".equals(currentBlockType)) {
                                    ToolCallEntry entry = new ToolCallEntry();
                                    entry.callId = currentToolId;
                                    entry.toolName = currentToolName;
                                    entry.argsJson = toolInputBuffer.toString();
                                    toolCalls.add(entry);
                                }
                                currentBlockType = null;
                            }
                            case "message_delta" -> {
                                JsonNode delta = json.get("delta");
                                if (delta != null && delta.has("stop_reason")) {
                                    stopReason = delta.get("stop_reason").asText();
                                }
                            }
                            case "message_stop" -> {
                                cancelRequested.set(true);
                                eventSource.cancel();
                                finishCurrentTurn();
                            }
                            case "error" -> {
                                String errorMsg = json.has("message") ? json.get("message").asText() : "Unknown error";
                                handleError(result, new RuntimeException("Anthropic API error: " + errorMsg));
                            }
                        }
                    } catch (Exception e) {
                        handleError(result, e);
                    }
                }

                @Override
                public void onFailure(EventSource eventSource, Throwable t, Response response) {
                    if (t instanceof StreamResetException) {
                        if (isClientCancelledStream(t) && cancelRequested.get()) {
                            return;
                        }
                        finishCurrentTurn();
                        return;
                    }
                    String errMsg = "SSE connection failed";
                    if (response != null) {
                        errMsg = "HTTP " + response.code();
                        try {
                            String errBody = response.body() != null ? response.body().string() : "";
                            if (!errBody.isEmpty()) errMsg += ": " + errBody;
                        } catch (Exception ignored) {}
                    } else if (t != null) {
                        errMsg = t.getMessage();
                    }
                    handleError(result, new RuntimeException(errMsg, t));
                }

                @Override
                public void onClosed(EventSource eventSource) {}
            });
        } catch (Exception e) {
            handleError(result, e);
        }
    }

    /**
     * 处理工具调用并继续Agent循环.
     */
    private void handleToolCallsAndContinue(LLMResult result, List<Message> messages,
                                             List<ToolDescriptor> tools, ToolExecutor toolExecutor,
                                             String content, String think, String thinkSignature,
                                             List<ToolCallEntry> toolCalls) {
        try {
            // 添加assistant消息
            messages.add(Message.fromAssistant(buildAssistantMessage(content, think, thinkSignature, toolCalls)));

            // 执行每个工具调用
            List<String> toolResults = new ArrayList<>();
            for (ToolCallEntry entry : toolCalls) {
                ToolDescriptor descriptor = ToolDescriptor.fromTool(toolMap.get(entry.toolName));
                descriptor.setCallId(entry.callId);
                descriptor.setInputParams(entry.argsJson);

                String toolResult = toolExecutor.execute(entry.toolName, entry.argsJson, descriptor, result.getHandler());
                toolResults.add(toolResult);
            }

            // Anthropic: 工具结果作为user消息中的tool_result内容块
            messages.add(Message.fromTool(buildToolResultMessage(toolCalls, toolResults)));

            // 继续Agent循环
            executeAgentLoop(result, messages, tools, toolExecutor);
        } catch (Exception e) {
            handleError(result, e);
        }
    }

    /**
     * 构建assistant消息JSON.
     */
    private String buildAssistantMessage(String content, String think, String thinkSignature, List<ToolCallEntry> toolCalls) {
        ObjectNode msg = MAPPER.createObjectNode();
        msg.put("role", "assistant");
        ArrayNode contentArray = MAPPER.createArrayNode();
        if ((isMiMoModel() && think != null && !think.isEmpty()) || (thinkSignature != null && !thinkSignature.isEmpty())) {
            ObjectNode thinkBlock = MAPPER.createObjectNode();
            thinkBlock.put("type", "thinking");
            if (think != null && !think.isEmpty()) {
                thinkBlock.put("thinking", think);
            }
            if (thinkSignature != null && !thinkSignature.isEmpty()) {
                thinkBlock.put("signature", thinkSignature);
            }
            contentArray.add(thinkBlock);
        }
        if (content != null && !content.isEmpty()) {
            ObjectNode textBlock = MAPPER.createObjectNode();
            textBlock.put("type", "text");
            textBlock.put("text", content);
            contentArray.add(textBlock);
        }
        for (ToolCallEntry entry : toolCalls) {
            ObjectNode toolBlock = MAPPER.createObjectNode();
            toolBlock.put("type", "tool_use");
            toolBlock.put("id", entry.callId);
            toolBlock.put("name", entry.toolName);
            toolBlock.set("input", MAPPER.valueToTree(parseJsonSafe(entry.argsJson)));
            contentArray.add(toolBlock);
        }
        msg.set("content", contentArray);
        return msg.toString();
    }

    /**
     * 构建工具结果消息JSON.
     */
    private String buildToolResultMessage(List<ToolCallEntry> toolCalls, List<String> results) {
        ObjectNode msg = MAPPER.createObjectNode();
        msg.put("role", "user");
        ArrayNode contentArray = MAPPER.createArrayNode();
        for (int i = 0; i < toolCalls.size(); i++) {
            ObjectNode resultBlock = MAPPER.createObjectNode();
            resultBlock.put("type", "tool_result");
            resultBlock.put("tool_use_id", toolCalls.get(i).callId);
            resultBlock.put("content", results.get(i));
            contentArray.add(resultBlock);
        }
        msg.set("content", contentArray);
        return msg.toString();
    }

    private Object parseJsonSafe(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            return MAPPER.createObjectNode();
        }
    }

    /**
     * 工具调用条目.
     */
    private static class ToolCallEntry {
        String callId;
        String toolName;
        String argsJson;
    }

    /**
     * 构建Anthropic Messages请求体.
     */
    private ObjectNode buildRequestBody(List<Message> messages, List<ToolDescriptor> tools) {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("model", modelName);
        body.put("max_tokens", 4096);
        body.put("stream", true);

        ArrayNode messagesArray = MAPPER.createArrayNode();
        for (Message msg : messages) {
            // 检查是否是结构化JSON消息(来自Agent循环的工具调用)
            if (msg.getContent() != null && msg.getContent().startsWith("{")) {
                try {
                    JsonNode parsed = MAPPER.readTree(msg.getContent());
                    if (parsed.has("role")) {
                        messagesArray.add(parsed);
                        continue;
                    }
                } catch (Exception ignored) {}
            }

            ObjectNode msgNode = MAPPER.createObjectNode();
            msgNode.put("role", msg.getRole() == Message.Role.tool ? "user" : msg.getRole().name());

            boolean includeThinking = isMiMoModel() && msg.getRole() == Message.Role.assistant
                    && msg.getThink() != null && !msg.getThink().isEmpty();
            if (includeThinking || (msg.getAttachments() != null && !msg.getAttachments().isEmpty())) {
                ArrayNode contentArray = MAPPER.createArrayNode();
                if (includeThinking) {
                    ObjectNode thinkPart = MAPPER.createObjectNode();
                    thinkPart.put("type", "thinking");
                    thinkPart.put("thinking", msg.getThink());
                    contentArray.add(thinkPart);
                }
                if (msg.getContent() != null) {
                    ObjectNode textPart = MAPPER.createObjectNode();
                    textPart.put("type", "text");
                    textPart.put("text", msg.getContent());
                    contentArray.add(textPart);
                }
                for (MessageAttachment attachment : msg.getAttachments()) {
                    ObjectNode imagePart = MAPPER.createObjectNode();
                    imagePart.put("type", "image");
                    ObjectNode source = MAPPER.createObjectNode();
                    source.put("type", "base64");
                    source.put("media_type", attachment.getContentType());
                    source.put("data", Base64.getEncoder().encodeToString(attachment.getData()));
                    imagePart.set("source", source);
                    contentArray.add(imagePart);
                }
                msgNode.set("content", contentArray);
            } else {
                msgNode.put("content", msg.getContent());
            }
            messagesArray.add(msgNode);
        }
        body.set("messages", messagesArray);

        if (!tools.isEmpty()) {
            ArrayNode toolsArray = MAPPER.createArrayNode();
            for (ToolDescriptor desc : tools) {
                toolsArray.add(MAPPER.valueToTree(desc.toLLMContent(ink.icoding.llm.core.entity.ModelType.Anthropic)));
            }
            body.set("tools", toolsArray);
        }

        return body;
    }

    /**
     * 处理错误, 将异常传递给错误回调处理器.
     */
    private void handleError(LLMResult result, Throwable t) {
        result.completeExceptionally(t);
        java.util.function.Consumer<Exception> errorHandler = result.getErrorHandler();
        if (errorHandler != null) {
            errorHandler.accept(t instanceof Exception ? (Exception) t : new RuntimeException(t));
        }
    }

    private static boolean shouldContinueWithToolCalls(String stopReason, List<ToolCallEntry> toolCalls) {
        return "tool_use".equals(stopReason) && toolCalls != null && !toolCalls.isEmpty();
    }

    private static boolean isClientCancelledStream(Throwable t) {
        return t instanceof StreamResetException
                && ((StreamResetException) t).errorCode == ErrorCode.CANCEL;
    }

    private boolean isMiMoModel() {
        return modelName != null && modelName.regionMatches(true, 0, "MiMo", 0, 4);
    }
}
