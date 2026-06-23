package ink.icoding.llm.core.model.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ink.icoding.llm.core.entity.Message;
import okhttp3.internal.http2.ErrorCode;
import okhttp3.internal.http2.StreamResetException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MiMoReasoningHistoryTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void anthropicAssistantHistoryIncludesThinkingForMiMo() throws Exception {
        AnthropicModel model = new AnthropicModel("https://example.com", "MiMo-v2.5-pro", "test-key");
        Method method = AnthropicModel.class.getDeclaredMethod("buildAssistantMessage", String.class, String.class, String.class, List.class);
        method.setAccessible(true);

        String json = (String) method.invoke(model, "done", "step-by-step", null, List.of());
        JsonNode assistant = MAPPER.readTree(json);

        assertEquals("thinking", assistant.get("content").get(0).get("type").asText());
        assertEquals("step-by-step", assistant.get("content").get(0).get("thinking").asText());
        assertEquals("text", assistant.get("content").get(1).get("type").asText());
    }

    @Test
    void anthropicAssistantHistorySkipsThinkingForNonMiMo() throws Exception {
        AnthropicModel model = new AnthropicModel("https://example.com", "claude-sonnet-4", "test-key");
        Method method = AnthropicModel.class.getDeclaredMethod("buildAssistantMessage", String.class, String.class, String.class, List.class);
        method.setAccessible(true);

        String json = (String) method.invoke(model, "done", "step-by-step", null, List.of());
        JsonNode assistant = MAPPER.readTree(json);

        assertEquals(1, assistant.get("content").size());
        assertEquals("text", assistant.get("content").get(0).get("type").asText());
    }

    @Test
    void anthropicAssistantHistoryIncludesSignatureForNonMiMo() throws Exception {
        AnthropicModel model = new AnthropicModel("https://example.com", "claude-sonnet-4", "test-key");
        Method method = AnthropicModel.class.getDeclaredMethod("buildAssistantMessage", String.class, String.class, String.class, List.class);
        method.setAccessible(true);

        String json = (String) method.invoke(model, "done", "step-by-step", "sig-123", List.of());
        JsonNode assistant = MAPPER.readTree(json);

        assertEquals("thinking", assistant.get("content").get(0).get("type").asText());
        assertEquals("step-by-step", assistant.get("content").get(0).get("thinking").asText());
        assertEquals("sig-123", assistant.get("content").get(0).get("signature").asText());
        assertEquals("text", assistant.get("content").get(1).get("type").asText());
    }

    @Test
    void anthropicAssistantHistoryCanReplaySignatureWithoutMiMoThinkingGate() throws Exception {
        AnthropicModel model = new AnthropicModel("https://example.com", "claude-sonnet-4", "test-key");
        Method method = AnthropicModel.class.getDeclaredMethod("buildAssistantMessage", String.class, String.class, String.class, List.class);
        method.setAccessible(true);

        String json = (String) method.invoke(model, "done", null, "sig-123", List.of());
        JsonNode assistant = MAPPER.readTree(json);

        assertEquals("thinking", assistant.get("content").get(0).get("type").asText());
        assertEquals("sig-123", assistant.get("content").get(0).get("signature").asText());
        assertFalse(assistant.get("content").get(0).has("thinking"));
        assertEquals("text", assistant.get("content").get(1).get("type").asText());
    }

    @Test
    void anthropicStreamResetCancelIsTreatedAsClientCancellation() throws Exception {
        Method method = AnthropicModel.class.getDeclaredMethod("isClientCancelledStream", Throwable.class);
        method.setAccessible(true);

        assertTrue((Boolean) method.invoke(null, new StreamResetException(ErrorCode.CANCEL)));
        assertFalse((Boolean) method.invoke(null, new StreamResetException(ErrorCode.INTERNAL_ERROR)));
    }

    @Test
    void anthropicToolUseStopReasonStillRequiresContinuation() throws Exception {
        Method method = AnthropicModel.class.getDeclaredMethod("shouldContinueWithToolCalls", String.class, List.class);
        method.setAccessible(true);

        assertTrue((Boolean) method.invoke(null, "tool_use", List.of(new Object())));
        assertFalse((Boolean) method.invoke(null, "end_turn", List.of(new Object())));
        assertFalse((Boolean) method.invoke(null, "tool_use", List.of()));
    }

    @Test
    void openAIChatRequestIncludesAssistantThinkingForMiMo() throws Exception {
        OpenAIChatModel model = new OpenAIChatModel("https://example.com", "mimo-v2.5-pro", "test-key");
        Method method = OpenAIChatModel.class.getDeclaredMethod("buildRequestBody", List.class, List.class);
        method.setAccessible(true);

        Message assistantMessage = Message.fromAssistant("done").appendThink("step-by-step");
        ObjectNode body = (ObjectNode) method.invoke(model, List.of(assistantMessage), List.of());
        JsonNode assistant = body.get("messages").get(0);

        assertEquals("assistant", assistant.get("role").asText());
        assertEquals("step-by-step", assistant.get("reasoning_content").asText());
    }

    @Test
    void openAIResponseAssistantHistoryIncludesThinkingForMiMo() throws Exception {
        OpenAIResponseModel model = new OpenAIResponseModel("https://example.com", "MiMo-v2.5-pro", "test-key");
        Method method = OpenAIResponseModel.class.getDeclaredMethod("buildTextItem", Message.class);
        method.setAccessible(true);

        Message assistantMessage = Message.fromAssistant("done").appendThink("step-by-step");
        ObjectNode item = (ObjectNode) method.invoke(model, assistantMessage);

        assertEquals("assistant", item.get("role").asText());
        assertEquals("step-by-step", item.get("reasoning_content").asText());
        assertEquals("output_text", item.get("content").get(0).get("type").asText());
    }

    @Test
    void openAIResponseAssistantHistorySkipsThinkingForNonMiMo() throws Exception {
        OpenAIResponseModel model = new OpenAIResponseModel("https://example.com", "gpt-4.1", "test-key");
        Method method = OpenAIResponseModel.class.getDeclaredMethod("buildTextItem", Message.class);
        method.setAccessible(true);

        Message assistantMessage = Message.fromAssistant("done").appendThink("step-by-step");
        ObjectNode item = (ObjectNode) method.invoke(model, assistantMessage);

        assertFalse(item.has("reasoning_content"));
        assertEquals("output_text", item.get("content").get(0).get("type").asText());
    }

    @Test
    void openAIResponseStreamResetCancelIsTreatedAsClientCancellation() throws Exception {
        Method method = OpenAIResponseModel.class.getDeclaredMethod("isClientCancelledStream", Throwable.class);
        method.setAccessible(true);

        assertTrue((Boolean) method.invoke(null, new StreamResetException(ErrorCode.CANCEL)));
        assertFalse((Boolean) method.invoke(null, new StreamResetException(ErrorCode.REFUSED_STREAM)));
    }

    @Test
    void openAIResponseDropsLegacyChatToolMessages() throws Exception {
        OpenAIResponseModel model = new OpenAIResponseModel("https://example.com", "gpt-4.1", "test-key");
        Method method = OpenAIResponseModel.class.getDeclaredMethod("buildRequestBody", List.class, List.class);
        method.setAccessible(true);

        Message assistantToolCall = Message.fromAssistant("""
                {"role":"assistant","content":null,"tool_calls":[{"id":"call_1","type":"function","function":{"name":"search","arguments":"{}"}}]}
                """.trim());
        Message toolResult = Message.fromTool("""
                {"role":"tool","tool_call_id":"call_1","content":"result"}
                """.trim());

        ObjectNode body = (ObjectNode) method.invoke(model, List.of(assistantToolCall, toolResult), List.of());
        JsonNode input = body.get("input");

        assertEquals(0, input.size());
    }

    @Test
    void neutralToolHistoryCanBuildAllProtocols() throws Exception {
        Message assistant = Message.fromAssistant("checking")
                .appendToolCall("call_1", "search", "{\"q\":\"java\"}");
        Message tool = Message.fromTool().withToolResult("call_1", "result");

        Method chatMethod = OpenAIChatModel.class.getDeclaredMethod("buildRequestBody", List.class, List.class);
        chatMethod.setAccessible(true);
        ObjectNode chatBody = (ObjectNode) chatMethod.invoke(
                new OpenAIChatModel("https://example.com", "gpt-4.1", "test-key"),
                List.of(assistant, tool), List.of());
        assertTrue(chatBody.get("messages").get(0).has("tool_calls"));
        assertEquals("tool", chatBody.get("messages").get(1).get("role").asText());

        Method responseMethod = OpenAIResponseModel.class.getDeclaredMethod("buildRequestBody", List.class, List.class);
        responseMethod.setAccessible(true);
        ObjectNode responseBody = (ObjectNode) responseMethod.invoke(
                new OpenAIResponseModel("https://example.com", "gpt-4.1", "test-key"),
                List.of(assistant, tool), List.of());
        assertEquals("output_text", responseBody.get("input").get(0).get("content").get(0).get("type").asText());
        assertEquals("function_call", responseBody.get("input").get(1).get("type").asText());
        assertEquals("function_call_output", responseBody.get("input").get(2).get("type").asText());

        Method anthropicMethod = AnthropicModel.class.getDeclaredMethod("buildRequestBody", List.class, List.class);
        anthropicMethod.setAccessible(true);
        ObjectNode anthropicBody = (ObjectNode) anthropicMethod.invoke(
                new AnthropicModel("https://example.com", "claude-sonnet-4", "test-key"),
                List.of(assistant, tool), List.of());
        assertEquals("tool_use", anthropicBody.get("messages").get(0).get("content").get(1).get("type").asText());
        assertEquals("tool_result", anthropicBody.get("messages").get(1).get("content").get(0).get("type").asText());
    }
}
