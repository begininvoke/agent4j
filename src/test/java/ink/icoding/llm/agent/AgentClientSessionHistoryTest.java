package ink.icoding.llm.agent;

import ink.icoding.llm.core.entity.Message;
import ink.icoding.llm.core.model.LLMModel;
import ink.icoding.llm.core.model.LLMResult;
import ink.icoding.llm.core.tool.Tool;
import ink.icoding.llm.core.tool.ToolExecutor;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    private record TurnResponse(String content, String think) {}

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
}

