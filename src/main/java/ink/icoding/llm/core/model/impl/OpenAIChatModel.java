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

/**
 * OpenAI Chat Completions API模型实现.
 * <p>通过SSE流式调用 {@code /v1/chat/completions} 接口与OpenAI兼容的LLM交互.
 * 支持文本生成、思考推理(reasoning_content)和工具调用(function_call).
 * 内置Agent循环: 自动处理工具调用并将结果反馈给LLM, 直到返回文本响应.</p>
 *
 * @author gsk
 */
public class OpenAIChatModel implements LLMModel {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final String baseUrl;
    private final String modelName;
    private final String apiKey;
    private final OkHttpClient client;
    private final Map<String, Tool> toolMap = new ConcurrentHashMap<>();

    /**
     * 构造OpenAI Chat模型实例.
     *
     * @param baseUrl   API基础地址, 如 https://api.openai.com
     * @param modelName 模型名称, 如 gpt-4o
     * @param apiKey    API密钥
     */
    public OpenAIChatModel(String baseUrl, String modelName, String apiKey) {
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
     *
     * @param result       结果对象
     * @param messages     消息列表(会被修改)
     * @param tools        工具描述列表
     * @param toolExecutor 工具执行器
     */
    private void executeAgentLoop(LLMResult result, List<Message> messages, List<ToolDescriptor> tools, ToolExecutor toolExecutor) {
        try {
            ObjectNode body = buildRequestBody(messages, tools);
            Request request = new Request.Builder()
                    .url(baseUrl + "/v1/chat/completions")
                    .post(RequestBody.create(body.toString(), MediaType.parse("application/json")))
                    .header("Authorization", "Bearer " + apiKey)
                    .build();

            EventSource.Factory factory = EventSources.createFactory(client);
            factory.newEventSource(request, new EventSourceListener() {
                private final StringBuilder contentBuffer = new StringBuilder();
                private final StringBuilder thinkBuffer = new StringBuilder();
                private final List<ToolCallEntry> toolCalls = new ArrayList<>();
                private int currentToolCallIndex = -1;

                @Override
                public void onEvent(EventSource eventSource, String id, String type, String data) {
                    if ("[DONE]".equals(data)) return;
                    try {
                        JsonNode json = MAPPER.readTree(data);
                        JsonNode choices = json.get("choices");
                        if (choices == null || choices.isEmpty()) return;

                        JsonNode delta = choices.get(0).get("delta");
                        if (delta == null) return;

                        // 处理思考/推理内容
                        if (delta.has("reasoning_content")) {
                            JsonNode reasoning = delta.get("reasoning_content");
                            if (reasoning != null && !reasoning.isNull()) {
                                thinkBuffer.append(reasoning.asText());
                                ResultHandler handler = result.getHandler();
                                if (handler != null) handler.onThink(reasoning.asText());
                            }
                        }else if (delta.has("reasoning")) {
                            JsonNode reasoning = delta.get("reasoning");
                            if (reasoning != null && !reasoning.isNull()) {
                                thinkBuffer.append(reasoning.asText());
                                ResultHandler handler = result.getHandler();
                                if (handler != null) handler.onThink(reasoning.asText());
                            }
                        }else if (delta.has("thinking")) {
                            JsonNode reasoning = delta.get("thinking");
                            if (reasoning != null && !reasoning.isNull()) {
                                thinkBuffer.append(reasoning.asText());
                                ResultHandler handler = result.getHandler();
                                if (handler != null) handler.onThink(reasoning.asText());
                            }
                        }else if (delta.has("thinking_content")) {
                            JsonNode reasoning = delta.get("thinking_content");
                            if (reasoning != null && !reasoning.isNull()) {
                                thinkBuffer.append(reasoning.asText());
                                ResultHandler handler = result.getHandler();
                                if (handler != null) handler.onThink(reasoning.asText());
                            }
                        }

                        // 处理文本内容
                        JsonNode content = delta.get("content");
                        if (content != null && !content.isNull()) {
                            contentBuffer.append(content.asText());
                            ResultHandler handler = result.getHandler();
                            if (handler != null) handler.onMessage(content.asText());
                        }

                        // 处理工具调用
                        JsonNode toolCallsNode = delta.get("tool_calls");
                        if (toolCallsNode != null) {
                            for (JsonNode toolCall : toolCallsNode) {
                                JsonNode indexNode = toolCall.get("index");
                                if (indexNode != null) {
                                    int idx = indexNode.asInt();
                                    while (toolCalls.size() <= idx) {
                                        toolCalls.add(new ToolCallEntry());
                                    }
                                    currentToolCallIndex = idx;
                                    ToolCallEntry entry = toolCalls.get(idx);
                                    JsonNode idNode = toolCall.get("id");
                                    if (idNode != null) entry.callId = idNode.asText();
                                    JsonNode function = toolCall.get("function");
                                    if (function != null) {
                                        JsonNode nameNode = function.get("name");
                                        if (nameNode != null) entry.toolName = nameNode.asText();
                                        JsonNode args = function.get("arguments");
                                        if (args != null) entry.argsBuffer.append(args.asText());
                                    }
                                }
                            }
                        }

                        // 检查完成原因
                        JsonNode finishReason = choices.get(0).get("finish_reason");
                        if (finishReason != null) {
                            String reason = finishReason.asText();
                            if ("tool_calls".equals(reason)) {
                                eventSource.cancel();
                                handleToolCallsAndContinue(result, messages, tools, toolExecutor,
                                        contentBuffer.toString(), thinkBuffer.toString(), toolCalls);
                            } else if ("stop".equals(reason)) {
                                eventSource.cancel();
                                result.complete(contentBuffer.toString());
                            }
                        }
                    } catch (Exception e) {
                        handleError(result, e);
                    }
                }

                @Override
                public void onFailure(EventSource eventSource, Throwable t, Response response) {
                    String errMsg = "SSE connection failed: ";
                    if (response != null) {
                        errMsg += "HTTP " + response.code();
                        try {
                            String errBody = response.body() != null ? response.body().string() : "";
                            if (!errBody.isEmpty()) errMsg += ": " + errBody;
                        } catch (Exception ignored) {}
                    }
                    if (t != null) {
                        if (t instanceof StreamResetException){
                            if (((StreamResetException) t).errorCode == ErrorCode.CANCEL) {
                                // 连接被正常关闭, 不视为错误
                                return;
                            }
                        }
                        errMsg += ", " + t.getMessage();
                        handleError(result, new RuntimeException(errMsg, t));
                    }else{
                        handleError(result, new RuntimeException(errMsg));
                    }
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
            // 添加assistant消息(含工具调用)
            ObjectNode assistantMsg = MAPPER.createObjectNode();
            assistantMsg.put("role", "assistant");
            if (content != null && !content.isEmpty()) {
                assistantMsg.put("content", content);
            } else {
                assistantMsg.putNull("content");
            }
            ArrayNode toolCallsArray = MAPPER.createArrayNode();
            for (int i = 0; i < toolCalls.size(); i++) {
                ToolCallEntry entry = toolCalls.get(i);
                ObjectNode tc = MAPPER.createObjectNode();
                tc.put("id", entry.callId);
                tc.put("type", "function");
                ObjectNode fn = MAPPER.createObjectNode();
                fn.put("name", entry.toolName);
                fn.put("arguments", entry.argsBuffer.toString());
                tc.set("function", fn);
                toolCallsArray.add(tc);
            }
            assistantMsg.set("tool_calls", toolCallsArray);
            messages.add(Message.fromAssistant(assistantMsg.toString()));

            // 执行每个工具调用并添加结果
            for (ToolCallEntry entry : toolCalls) {
                ToolDescriptor descriptor = ToolDescriptor.fromTool(toolMap.get(entry.toolName));
                descriptor.setCallId(entry.callId);
                descriptor.setInputParams(entry.argsBuffer.toString());

                String toolResult = toolExecutor.execute(entry.toolName, entry.argsBuffer.toString(), descriptor, result.getHandler());

                ObjectNode toolMsg = MAPPER.createObjectNode();
                toolMsg.put("role", "tool");
                toolMsg.put("tool_call_id", entry.callId);
                toolMsg.put("content", toolResult);
                messages.add(Message.fromTool(toolMsg.toString()));
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
        final StringBuilder argsBuffer = new StringBuilder();
    }

    /**
     * 构建OpenAI Chat Completions请求体.
     *
     * @param messages 消息列表
     * @param tools    工具描述列表
     * @return 请求体JSON节点
     */
    private ObjectNode buildRequestBody(List<Message> messages, List<ToolDescriptor> tools) {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("model", modelName);
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
            msgNode.put("role", msg.getRole().name());

            if (msg.getAttachments() != null && !msg.getAttachments().isEmpty()) {
                ArrayNode contentArray = MAPPER.createArrayNode();
                if (msg.getContent() != null) {
                    ObjectNode textPart = MAPPER.createObjectNode();
                    textPart.put("type", "text");
                    textPart.put("text", msg.getContent());
                    contentArray.add(textPart);
                }
                for (MessageAttachment attachment : msg.getAttachments()) {
                    ObjectNode imagePart = MAPPER.createObjectNode();
                    imagePart.put("type", "image_url");
                    ObjectNode imageUrl = MAPPER.createObjectNode();
                    imageUrl.put("url", "data:" + attachment.getContentType() + ";base64," +
                            Base64.getEncoder().encodeToString(attachment.getData()));
                    imagePart.set("image_url", imageUrl);
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
                toolsArray.add(MAPPER.valueToTree(desc.toLLMContent(ink.icoding.llm.core.entity.ModelType.OpenAI)));
            }
            body.set("tools", toolsArray);
        }

        return body;
    }

    /**
     * 处理错误, 将异常传递给错误回调处理器.
     *
     * @param result 结果对象
     * @param t      异常
     */
    private void handleError(LLMResult result, Throwable t) {
        result.completeExceptionally(t);
        java.util.function.Consumer<Exception> errorHandler = result.getErrorHandler();
        if (errorHandler != null) {
            errorHandler.accept(t instanceof Exception ? (Exception) t : new RuntimeException(t));
        }
    }
}
