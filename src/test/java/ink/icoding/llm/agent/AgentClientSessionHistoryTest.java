package ink.icoding.llm.agent;

import ink.icoding.llm.core.entity.Message;
import ink.icoding.llm.core.model.ContextCompressionStatus;
import ink.icoding.llm.core.model.LLMModel;
import ink.icoding.llm.core.model.LLMResult;
import ink.icoding.llm.core.model.TokenUsage;
import ink.icoding.llm.core.tool.Tool;
import ink.icoding.llm.core.tool.ToolExecutor;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AgentClientSessionHistoryTest {

    @Test
    void laterTurnsIncludePreviousAssistantReplyAndThink() {
        RecordingModel model = new RecordingModel(
                new TurnResponse("第一轮回复", "第一轮思考"),
                new TurnResponse("第二轮回复", "第二轮思考")
        );

        AgentClient agent = new AgentClient();
        agent.setName("TestAgent");
        agent.setModel(model);

        AgentClientSession session = agent.createSession();

        session.command("第一轮提问").execute();
        session.command("第二轮提问").execute();

        List<Message> secondTurnMessages = model.requests().get(1);
        Message previousAssistant = secondTurnMessages.stream()
                .filter(msg -> msg.getRole() == Message.Role.assistant)
                .findFirst()
                .orElse(null);

        assertNotNull(previousAssistant);
        assertEquals("第一轮回复", previousAssistant.getContent());
        assertEquals("第一轮思考", previousAssistant.getThink());

        List<Message> history = session.getHistory();
        assertEquals(4, history.size());
        assertEquals(Message.Role.user, history.get(0).getRole());
        assertEquals(Message.Role.assistant, history.get(1).getRole());
        assertEquals("第一轮回复", history.get(1).getContent());
        assertEquals("第一轮思考", history.get(1).getThink());
        assertEquals(Message.Role.user, history.get(2).getRole());
        assertEquals(Message.Role.assistant, history.get(3).getRole());
        assertEquals("第二轮回复", history.get(3).getContent());
        assertEquals("第二轮思考", history.get(3).getThink());
    }

    @Test
    void toolLoopMessagesArePreservedAsRealHistory() {
        ToolTraceModel model = new ToolTraceModel();

        AgentClient agent = new AgentClient();
        agent.setName("TestAgent");
        agent.setModel(model);

        AgentClientSession session = agent.createSession();

        session.command("第一轮提问").execute();
        session.command("第二轮提问").execute();

        List<Message> history = session.getHistory();
        assertEquals(8, history.size());
        assertEquals(Message.Role.user, history.get(0).getRole());
        assertEquals(Message.Role.assistant, history.get(1).getRole());
        assertEquals(Message.Role.tool, history.get(2).getRole());
        assertEquals(Message.Role.assistant, history.get(3).getRole());

        List<Message> secondTurnMessages = model.requests().get(1);
        long toolMessages = secondTurnMessages.stream()
                .filter(msg -> msg.getRole() == Message.Role.tool)
                .count();
        assertEquals(1, toolMessages);
    }

    @Test
    void tokenUsageIsAvailableFromCallbackAndSessionResult() {
        UsageModel model = new UsageModel(321);

        AgentClient agent = new AgentClient();
        agent.setName("TestAgent");
        agent.setModel(model);

        AgentClientSession session = agent.createSession();
        AtomicReference<TokenUsage> callbackUsage = new AtomicReference<>();

        AgentSessionResult result = session.command("统计Token")
                .then(new AgentResultHandler() {
                    @Override
                    public void onUsage(TokenUsage usage) {
                        callbackUsage.set(usage);
                    }
                });
        result.execute();

        assertNotNull(callbackUsage.get());
        assertEquals(321, callbackUsage.get().getInputTokens());
        assertEquals(123, callbackUsage.get().getCachedTokens());
        assertNotNull(result.getLastUsage());
        assertEquals(321, result.getLastUsage().getInputTokens());
        assertEquals(123, result.getLastUsage().getCachedTokens());
        assertEquals(321, session.getLastContextTokens());
    }

    @Test
    void historyIsSummarizedIntoMemoryWhenContextTokensReachThreshold() {
        SummaryTriggerModel model = new SummaryTriggerModel();

        AgentClient agent = new AgentClient();
        agent.setName("TestAgent");
        agent.setModel(model);

        AgentClientSession session = agent.createSession();
        List<CompressionEvent> events = new ArrayList<>();

        session.command("请处理一个很长的任务")
                .then(new AgentResultHandler() {
                    @Override
                    public void onContextCompression(ContextCompressionStatus status, int beforeTokens, int afterTokens) {
                        events.add(new CompressionEvent(status, beforeTokens, afterTokens));
                    }
                })
                .execute();

        assertEquals(0, session.getHistory().size());
        assertEquals("重要记忆", session.getMemorySummary());
        assertEquals(0, session.getLastContextTokens());
        assertEquals(2, events.size());
        assertEquals(ContextCompressionStatus.STARTED, events.get(0).status());
        assertEquals(100_001, events.get(0).beforeTokens());
        assertEquals(0, events.get(0).afterTokens());
        assertEquals(ContextCompressionStatus.COMPLETED, events.get(1).status());
        assertEquals(100_001, events.get(1).beforeTokens());
        assertEquals(19, events.get(1).afterTokens());
    }

    @Test
    void historyIsSummarizedWhenRoundLimitIsReached() {
        RoundSummaryModel model = new RoundSummaryModel();

        AgentClient agent = new AgentClient();
        agent.setName("TestAgent");
        agent.setModel(model);

        AgentClientSession session = agent.createSession()
                .setContextSummaryTriggerTokens(0)
                .setContextSummaryTriggerRounds(2);

        session.command("第一轮").execute();
        assertEquals(2, session.getHistory().size());

        session.command("第二轮").execute();

        assertEquals(0, session.getHistory().size());
        assertEquals("轮数记忆", session.getMemorySummary());
        assertEquals(0, session.getLastContextTokens());
    }

    @Test
    void contextSummaryLimitsAreSerializedWithSession() {
        AgentClient agent = new AgentClient();
        agent.setName("TestAgent");
        agent.setModel(new UsageModel(10));

        AgentClientSession session = agent.createSession()
                .setContextSummaryTriggerTokens(0)
                .setContextSummaryTriggerRounds(3)
                .disableThinking()
                .setTemperature(0.35);

        AgentClientSession restored = AgentClientSession.fromSerialization(session.serialization(), agent);

        assertEquals(0, restored.getContextSummaryTriggerTokens());
        assertEquals(3, restored.getContextSummaryTriggerRounds());
        assertEquals(false, restored.getThinkingEnabled());
        assertEquals(0.35, restored.getTemperature());
    }

    @Test
    void agentClientPropagatesRequestDebugFlagToModel() {
        UsageModel model = new UsageModel(10);
        AgentClient agent = new AgentClient();

        agent.setLlmRequestDebugEnabled(true);
        agent.setModel(model);

        assertEquals(true, model.isRequestDebugEnabled());

        agent.setLlmRequestDebugEnabled(false);

        assertEquals(false, model.isRequestDebugEnabled());
    }

    @Test
    void agentClientPropagatesThinkingFlagToModel() {
        UsageModel model = new UsageModel(10);
        AgentClient agent = new AgentClient();

        agent.disableThinking();
        agent.setModel(model);

        assertEquals(false, model.getThinkingEnabled());

        agent.enableThinking();

        assertEquals(true, model.getThinkingEnabled());

        agent.setThinkingEnabled(null);

        assertEquals(null, model.getThinkingEnabled());
    }

    @Test
    void clearAllSkillsMakesAgentAPlainClientWithoutBuiltInTools() {
        ToolCaptureModel model = new ToolCaptureModel();
        AgentClient agent = new AgentClient();
        agent.setModel(model);
        agent.getSkills().add(new Skill("Test Skill", "A skill", List.of(), "skill content"));

        agent.clearAllSkills();
        agent.createSession().command("hello").execute();

        assertEquals(0, agent.getTools().size());
        assertEquals(0, agent.getSkills().size());
        assertFalse(agent.isBuiltInAgentToolsEnabled());
        assertEquals(0, model.toolCounts().get(0));
    }

    @Test
    void sessionRequestOptionsOverrideWithoutMutatingSharedModel() {
        ThinkingCaptureModel model = new ThinkingCaptureModel();
        AgentClient agent = new AgentClient();
        agent.setModel(model);
        agent.enableThinking();
        agent.setTemperature(0.8);

        AgentClientSession session = agent.createSession()
                .disableThinking()
                .setTemperature(0.2)
                .setContextSummaryTriggerRounds(1);
        int setCountBeforeRequest = model.thinkingSetCount();
        int temperatureSetCountBeforeRequest = model.temperatureSetCount();

        session.command("hello").execute();

        assertEquals(List.of(false, false), model.capturedThinking());
        assertEquals(List.of(0.2, 0.2), model.capturedTemperatures());
        assertEquals(true, agent.getThinkingEnabled());
        assertEquals(true, model.getThinkingEnabled());
        assertEquals(0.8, agent.getTemperature());
        assertEquals(0.8, model.getTemperature());
        assertEquals(setCountBeforeRequest, model.thinkingSetCount());
        assertEquals(temperatureSetCountBeforeRequest, model.temperatureSetCount());
    }

    private record TurnResponse(String content, String think) {}
    private record CompressionEvent(ContextCompressionStatus status, int beforeTokens, int afterTokens) {}

    private static class RecordingModel implements LLMModel {
        private final List<TurnResponse> responses;
        private final List<List<Message>> requests = new ArrayList<>();
        private int index;

        private RecordingModel(TurnResponse... responses) {
            this.responses = List.of(responses);
        }

        List<List<Message>> requests() {
            return requests;
        }

        @Override
        public LLMResult ask(Message message) {
            return ask(List.of(message));
        }

        @Override
        public LLMResult ask(List<Message> messages) {
            return ask(messages, List.of());
        }

        @Override
        public LLMResult ask(List<Message> messages, List<Tool> tools) {
            return ask(messages, tools, (toolName, paramJson, descriptor, handler) -> {
                throw new UnsupportedOperationException("No tools expected in this test");
            });
        }

        @Override
        public LLMResult ask(List<Message> messages, List<Tool> tools, ToolExecutor toolExecutor) {
            List<Message> snapshot = new ArrayList<>();
            for (Message msg : messages) {
                Message copy = new Message();
                copy.setRole(msg.getRole());
                copy.setContent(msg.getContent());
                copy.setThink(msg.getThink());
                snapshot.add(copy);
            }
            requests.add(snapshot);

            TurnResponse response = responses.get(index++);
            return new LLMResult(result -> {
                if (result.getHandler() != null && response.think() != null) {
                    result.getHandler().onThink(response.think());
                }
                if (result.getHandler() != null && response.content() != null) {
                    result.getHandler().onMessage(response.content());
                }
                result.complete(response.content());
            });
        }
    }

    private static class ToolTraceModel implements LLMModel {
        private final List<List<Message>> requests = new ArrayList<>();

        List<List<Message>> requests() {
            return requests;
        }

        @Override
        public LLMResult ask(Message message) {
            return ask(List.of(message));
        }

        @Override
        public LLMResult ask(List<Message> messages) {
            return ask(messages, List.of());
        }

        @Override
        public LLMResult ask(List<Message> messages, List<Tool> tools) {
            return ask(messages, tools, (toolName, paramJson, descriptor, handler) -> "ok");
        }

        @Override
        public LLMResult ask(List<Message> messages, List<Tool> tools, ToolExecutor toolExecutor) {
            requests.add(new ArrayList<>(messages));
            return new LLMResult(result -> {
                result.addAppendedMessage(Message.fromAssistant("{\"role\":\"assistant\",\"content\":null,\"tool_calls\":[]}"));
                result.addAppendedMessage(Message.fromTool("{\"role\":\"tool\",\"tool_call_id\":\"call_1\",\"content\":\"tool result\"}"));
                result.addAppendedMessage(Message.fromAssistant("最终回复"));
                result.complete("最终回复");
            });
        }
    }

    private static class UsageModel implements LLMModel {
        private final int inputTokens;
        private boolean requestDebugEnabled;
        private Boolean thinkingEnabled;

        private UsageModel(int inputTokens) {
            this.inputTokens = inputTokens;
        }

        @Override
        public LLMResult ask(Message message) {
            return ask(List.of(message));
        }

        @Override
        public LLMResult ask(List<Message> messages) {
            return ask(messages, List.of());
        }

        @Override
        public LLMResult ask(List<Message> messages, List<Tool> tools) {
            return ask(messages, tools, (toolName, paramJson, descriptor, handler) -> "ok");
        }

        @Override
        public LLMResult ask(List<Message> messages, List<Tool> tools, ToolExecutor toolExecutor) {
            return new LLMResult(result -> {
                result.addUsage(new TokenUsage(inputTokens, 12, inputTokens + 12, 123));
                result.addAppendedMessage(Message.fromAssistant("回复"));
                result.complete("回复");
            });
        }

        @Override
        public void setRequestDebugEnabled(boolean enabled) {
            this.requestDebugEnabled = enabled;
        }

        @Override
        public boolean isRequestDebugEnabled() {
            return requestDebugEnabled;
        }

        @Override
        public void setThinkingEnabled(Boolean enabled) {
            this.thinkingEnabled = enabled;
        }

        @Override
        public Boolean getThinkingEnabled() {
            return thinkingEnabled;
        }
    }

    private static class ToolCaptureModel implements LLMModel {
        private final List<Integer> toolCounts = new ArrayList<>();

        List<Integer> toolCounts() {
            return toolCounts;
        }

        @Override
        public LLMResult ask(Message message) {
            return ask(List.of(message));
        }

        @Override
        public LLMResult ask(List<Message> messages) {
            return ask(messages, List.of());
        }

        @Override
        public LLMResult ask(List<Message> messages, List<Tool> tools) {
            return ask(messages, tools, (toolName, paramJson, descriptor, handler) -> "ok");
        }

        @Override
        public LLMResult ask(List<Message> messages, List<Tool> tools, ToolExecutor toolExecutor) {
            toolCounts.add(tools.size());
            return new LLMResult(result -> {
                result.addAppendedMessage(Message.fromAssistant("ok"));
                result.complete("ok");
            });
        }
    }

    private static class ThinkingCaptureModel implements LLMModel {
        private final List<Boolean> capturedThinking = new ArrayList<>();
        private final List<Double> capturedTemperatures = new ArrayList<>();
        private Boolean thinkingEnabled;
        private Double temperature;
        private int thinkingSetCount;
        private int temperatureSetCount;

        List<Boolean> capturedThinking() {
            return capturedThinking;
        }

        int thinkingSetCount() {
            return thinkingSetCount;
        }

        List<Double> capturedTemperatures() {
            return capturedTemperatures;
        }

        int temperatureSetCount() {
            return temperatureSetCount;
        }

        @Override
        public LLMResult ask(Message message) {
            return ask(List.of(message));
        }

        @Override
        public LLMResult ask(List<Message> messages) {
            return ask(messages, List.of());
        }

        @Override
        public LLMResult ask(List<Message> messages, List<Tool> tools) {
            return ask(messages, tools, (toolName, paramJson, descriptor, handler) -> "ok");
        }

        @Override
        public LLMResult ask(List<Message> messages, List<Tool> tools, ToolExecutor toolExecutor) {
            capturedThinking.add(thinkingEnabled);
            return new LLMResult(result -> {
                result.addAppendedMessage(Message.fromAssistant("ok"));
                result.complete("ok");
            });
        }

        @Override
        public LLMResult ask(List<Message> messages, List<Tool> tools, ToolExecutor toolExecutor,
                             Boolean requestThinkingEnabled) {
            capturedThinking.add(requestThinkingEnabled);
            capturedTemperatures.add(temperature);
            return new LLMResult(result -> {
                result.addAppendedMessage(Message.fromAssistant("ok"));
                result.complete("ok");
            });
        }

        @Override
        public LLMResult ask(List<Message> messages, List<Tool> tools, ToolExecutor toolExecutor,
                             Boolean requestThinkingEnabled, Double requestTemperature) {
            capturedThinking.add(requestThinkingEnabled);
            capturedTemperatures.add(requestTemperature);
            return new LLMResult(result -> {
                result.addAppendedMessage(Message.fromAssistant("ok"));
                result.complete("ok");
            });
        }

        @Override
        public void setThinkingEnabled(Boolean enabled) {
            this.thinkingEnabled = enabled;
            thinkingSetCount++;
        }

        @Override
        public Boolean getThinkingEnabled() {
            return thinkingEnabled;
        }

        @Override
        public void setTemperature(Double temperature) {
            this.temperature = temperature;
            temperatureSetCount++;
        }

        @Override
        public Double getTemperature() {
            return temperature;
        }
    }

    private static class SummaryTriggerModel implements LLMModel {
        private int calls;

        @Override
        public LLMResult ask(Message message) {
            return ask(List.of(message));
        }

        @Override
        public LLMResult ask(List<Message> messages) {
            return ask(messages, List.of());
        }

        @Override
        public LLMResult ask(List<Message> messages, List<Tool> tools) {
            return ask(messages, tools, (toolName, paramJson, descriptor, handler) -> "ok");
        }

        @Override
        public LLMResult ask(List<Message> messages, List<Tool> tools, ToolExecutor toolExecutor) {
            calls++;
            if (calls == 1) {
                return new LLMResult(result -> {
                    result.addUsage(new TokenUsage(100_001, 10, 100_011));
                    result.addAppendedMessage(Message.fromAssistant("任务完成"));
                    result.complete("任务完成");
                });
            }
            return new LLMResult(result -> {
                result.addUsage(new TokenUsage(1234, 19, 1253));
                result.addAppendedMessage(Message.fromAssistant("重要记忆"));
                result.complete("重要记忆");
            });
        }
    }

    private static class RoundSummaryModel implements LLMModel {
        private int calls;

        @Override
        public LLMResult ask(Message message) {
            return ask(List.of(message));
        }

        @Override
        public LLMResult ask(List<Message> messages) {
            return ask(messages, List.of());
        }

        @Override
        public LLMResult ask(List<Message> messages, List<Tool> tools) {
            return ask(messages, tools, (toolName, paramJson, descriptor, handler) -> "ok");
        }

        @Override
        public LLMResult ask(List<Message> messages, List<Tool> tools, ToolExecutor toolExecutor) {
            calls++;
            if (calls <= 2) {
                int current = calls;
                return new LLMResult(result -> {
                    result.addAppendedMessage(Message.fromAssistant("回复" + current));
                    result.complete("回复" + current);
                });
            }
            return new LLMResult(result -> {
                result.addUsage(new TokenUsage(100, 7, 107));
                result.addAppendedMessage(Message.fromAssistant("轮数记忆"));
                result.complete("轮数记忆");
            });
        }
    }
}
