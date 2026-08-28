package ink.icoding.llm.core.tool;

import ink.icoding.llm.core.model.ResultHandler;
import ink.icoding.llm.core.tool.annotations.ToolInfo;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolExecutorTest {

    @Test
    void defaultExecuteReportsResultWhenToolCompletes() {
        ToolDescriptor descriptor = ToolDescriptor.fromTool(new SuccessfulTool());
        List<ToolStatus> statuses = new ArrayList<>();
        List<Object> results = new ArrayList<>();

        String result = ToolExecutor.defaultExecute(new SuccessfulTool(), "{}", descriptor, new ResultHandler() {
            @Override
            public void onTool(ToolDescriptor tool, ToolStatus status, Object toolResult) {
                statuses.add(status);
                results.add(toolResult);
            }
        });

        assertEquals("done", result);
        assertEquals(List.of(ToolStatus.PREPARING, ToolStatus.CALLING, ToolStatus.COMPLETED), statuses);
        assertEquals(Arrays.asList(null, null, "done"), results);
    }

    @Test
    void threeArgumentCallbackRemainsCompatibleWithLegacyHandler() {
        ToolDescriptor descriptor = ToolDescriptor.fromTool(new SuccessfulTool());
        AtomicInteger callbackCount = new AtomicInteger();

        ToolExecutor.defaultExecute(new SuccessfulTool(), "{}", descriptor, new ResultHandler() {
            @Override
            public void onTool(ToolDescriptor tool, ToolStatus status) {
                callbackCount.incrementAndGet();
            }
        });

        assertEquals(3, callbackCount.get());
    }

    @Test
    void defaultExecuteReportsToolErrorWithoutThrowing() {
        ToolDescriptor descriptor = ToolDescriptor.fromTool(new FailingTool());
        AtomicReference<Exception> errorRef = new AtomicReference<>();

        String result = ToolExecutor.defaultExecute(new FailingTool(), "{}", descriptor, new ResultHandler() {
            @Override
            public void onToolError(ToolDescriptor tool, Exception error) {
                errorRef.set(error);
            }
        });

        assertNotNull(errorRef.get());
        assertEquals("boom", errorRef.get().getMessage());
        assertTrue(result.contains("failed"));
        assertTrue(result.contains("boom"));
    }

    static class EmptyParam extends ToolParam {}

    @ToolInfo(name = "successful_tool", description = "A tool that always succeeds")
    static class SuccessfulTool implements Tool<EmptyParam> {
        @Override
        public String execute(EmptyParam param) {
            return "done";
        }
    }

    @ToolInfo(name = "failing_tool", description = "A tool that always fails")
    static class FailingTool implements Tool<EmptyParam> {
        @Override
        public String execute(EmptyParam param) {
            throw new RuntimeException("boom");
        }
    }
}
