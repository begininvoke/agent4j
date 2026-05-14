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
import ink.icoding.llm.core.tool.ToolParam;
import ink.icoding.llm.core.tool.ToolStatus;
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
 * OpenAI Responses API模型实现.
 * <p>通过SSE流式调用 {@code /v1/responses} 接口与OpenAI交互.
 * 支持文本生成、推理(reasoning_text)和工具调用(function_call).
 * 内置Agent循环: 自动处理工具调用并将结果反馈给LLM, 直到返回文本响应.</p>
 *
 * @author gsk
 */
public class OpenAIResponseModel implements LLMModel {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final String baseUrl;
    private final String modelName;
    private final String apiKey;
    private final OkHttpClient client;
    private final Map<String, Tool> toolMap = new ConcurrentHashMap<>();

    /**
     * 构造OpenAI Responses模型实例.
     *
     * @param baseUrl   API基础地址, 如 https://api.openai.com
     * @param modelName 模型名称
     * @param apiKey    API密钥
     */
    public OpenAIResponseModel(String baseUrl, String modelName, String apiKey) {
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
                    .url(baseUrl + "/v1/responses")
                    .post(RequestBody.create(body.toString(), MediaType.parse("application/json")))
                    .header("Authorization", "Bearer " + apiKey)
                    .build();

            EventSource.Factory factory = EventSources.createFactory(client);
            factory.newEventSource(request, new EventSourceListener() {
                private String currentCallId;
                private String currentToolName;
                private final StringBuilder toolArgsBuffer = new StringBuilder();
                private final StringBuilder contentBuffer = new StringBuilder();
                private final StringBuilder thinkBuffer = new StringBuilder();
                private final List<ToolCallEntry> toolCalls = new ArrayList<>();
                private final AtomicBoolean cancelRequested = new AtomicBoolean(false);
                private final AtomicBoolean turnHandled = new AtomicBoolean(false);

                private void finishCurrentTurn() {
                    if (!turnHandled.compareAndSet(false, true)) {
                        return;
                    }
                    if (!toolCalls.isEmpty()) {
                        handleToolCallsAndContinue(result, messages, tools, toolExecutor,
                                contentBuffer.toString(), thinkBuffer.toString(), new ArrayList<>(toolCalls));
                    } else {
                        result.complete(contentBuffer.toString());
                    }
                }

                @Override
                public void onEvent(EventSource eventSource, String id, String type, String data) {
                    try {
                        JsonNode json = MAPPER.readTree(data);
                        String eventType = json.has("type") ? json.get("type").asText() : "";

                        switch (eventType) {
                            case "response.output_text.delta" -> {
                                String text = json.get("delta").asText();
                                contentBuffer.append(text);
                                ResultHandler handler = result.getHandler();
                                if (handler != null) handler.onMessage(text);
                            }
                            case "response.output_text.done" -> {}
                            case "response.reasoning_text.delta", "response.reasoning_content.delta", "response.reasoning.delta" -> {
                                thinkBuffer.append(json.get("delta").asText());
                                ResultHandler handler = result.getHandler();
                                if (handler != null) handler.onThink(json.get("delta").asText());
                            }
                            case "response.function_call.start" -> {
                                currentCallId = json.get("call_id").asText();
                                currentToolName = json.get("name").asText();
                                toolArgsBuffer.setLength(0);
                            }
                            case "response.function_call_arguments.delta" -> {
                                toolArgsBuffer.append(json.get("delta").asText());
                            }
                            case "response.function_call_arguments.done" -> {
                                ToolCallEntry entry = new ToolCallEntry();
                                entry.callId = currentCallId;
                                entry.toolName = currentToolName;
                                entry.argsJson = toolArgsBuffer.toString();
                                toolCalls.add(entry);
                            }
                            case "response.completed" -> {
                                cancelRequested.set(true);
                                eventSource.cancel();
                                finishCurrentTurn();
                            }
                            case "error" -> {
                                String errorMsg = json.has("message") ? json.get("message").asText() : "Unknown error";
                                handleError(result, new RuntimeException("OpenAI Response API error: " + errorMsg));
                            }
                        }
                    } catch (Exception e) {
                        handleError(result, e);
                    }
                }

                @Override
                public void onFailure(EventSource eventSource, Throwable t, Response response) {
                    if (t instanceof StreamResetException){
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
                    handleError(result, new RuntimeException(errMsg));
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
                                             String content, String think, List<ToolCallEntry> toolCalls) {
        try {
            if ((content != null && !content.isEmpty()) || (think != null && !think.isEmpty())) {
                Message assistantMessage = Message.fromAssistant();
                if (content != null && !content.isEmpty()) {
                    assistantMessage.appendContent(content);
                }
                if (think != null && !think.isEmpty()) {
                    assistantMessage.appendThink(think);
                }
                messages.add(assistantMessage);
            }

            // 执行每个工具调用并添加function_call_output
            for (ToolCallEntry entry : toolCalls) {
                ToolDescriptor descriptor = ToolDescriptor.fromTool(toolMap.get(entry.toolName));
                descriptor.setCallId(entry.callId);
                descriptor.setInputParams(entry.argsJson);

                String toolResult = toolExecutor.execute(entry.toolName, entry.argsJson, descriptor, result.getHandler());

                // Responses API使用function_call_output格式
                ObjectNode outputItem = MAPPER.createObjectNode();
                outputItem.put("type", "function_call_output");
                outputItem.put("call_id", entry.callId);
                outputItem.put("output", toolResult);
                messages.add(Message.fromTool(outputItem.toString()));
            }

            // 继续Agent循环
            executeAgentLoop(result, messages, tools, toolExecutor);
        } catch (Exception e) {
            handleError(result, e);
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
     * 构建OpenAI Responses请求体.
     */
    private ObjectNode buildRequestBody(List<Message> messages, List<ToolDescriptor> tools) {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("model", modelName);
        body.put("stream", true);

        ArrayNode inputArray = MAPPER.createArrayNode();
        for (Message msg : messages) {
            if (msg.getContent() != null && msg.getContent().startsWith("{")) {
                try {
                    JsonNode parsed = MAPPER.readTree(msg.getContent());
                    if (parsed.has("role") || parsed.has("type")) {
                        inputArray.add(parsed);
                        continue;
                    }
                } catch (Exception ignored) {
                }
            }
            inputArray.add(buildTextItem(msg));
        }
        body.set("input", inputArray);

        if (!tools.isEmpty()) {
            ArrayNode toolsArray = MAPPER.createArrayNode();
            for (ToolDescriptor desc : tools) {
                ObjectNode toolNode = MAPPER.createObjectNode();
                toolNode.put("type", "function");
                toolNode.put("name", desc.getName());
                toolNode.put("description", desc.getDescription());
                JsonNode openaiSchema = MAPPER.valueToTree(desc.toLLMContent(ink.icoding.llm.core.entity.ModelType.OpenAIResponse));
                toolNode.set("parameters", openaiSchema.get("function").get("parameters"));
                toolsArray.add(toolNode);
            }
            body.set("tools", toolsArray);
        }

        return body;
    }

    /**
     * 构建文本输入项.
     */
    private ObjectNode buildTextItem(Message msg) {
        ObjectNode item = MAPPER.createObjectNode();
        item.put("role", msg.getRole().name());
        if (isMiMoModel() && msg.getRole() == Message.Role.assistant && msg.getThink() != null && !msg.getThink().isEmpty()) {
            item.put("reasoning_content", msg.getThink());
        }
        String textType = msg.getRole() == Message.Role.assistant ? "output_text" : "input_text";

        if (msg.getAttachments() != null && !msg.getAttachments().isEmpty()) {
            ArrayNode contentArray = MAPPER.createArrayNode();
            if (msg.getContent() != null) {
                ObjectNode textPart = MAPPER.createObjectNode();
                textPart.put("type", textType);
                textPart.put("text", msg.getContent());
                contentArray.add(textPart);
            }
            for (MessageAttachment attachment : msg.getAttachments()) {
                ObjectNode imagePart = MAPPER.createObjectNode();
                imagePart.put("type", "input_image");
                imagePart.put("image_url", "data:" + attachment.getContentType() + ";base64," +
                        Base64.getEncoder().encodeToString(attachment.getData()));
                contentArray.add(imagePart);
            }
            item.set("content", contentArray);
        } else {
            ArrayNode contentArray = MAPPER.createArrayNode();
            if (msg.getContent() != null) {
                ObjectNode textPart = MAPPER.createObjectNode();
                textPart.put("type", textType);
                textPart.put("text", msg.getContent());
                contentArray.add(textPart);
            }
            item.set("content", contentArray);
        }
        return item;
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

    private static boolean isClientCancelledStream(Throwable t) {
        return t instanceof StreamResetException
                && ((StreamResetException) t).errorCode == ErrorCode.CANCEL;
    }

    private boolean isMiMoModel() {
        return modelName != null && modelName.regionMatches(true, 0, "MiMo", 0, 4);
    }
}
