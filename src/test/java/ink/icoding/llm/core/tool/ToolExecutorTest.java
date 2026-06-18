package ink.icoding.llm.core.tool;

import ink.icoding.llm.core.model.ResultHandler;
import ink.icoding.llm.core.tool.annotations.ToolInfo;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolExecutorTest {

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

    @ToolInfo(name = "failing_tool", description = "A tool that always fails")
    static class FailingTool implements Tool<EmptyParam> {
        @Override
        public String execute(EmptyParam param) {
            throw new RuntimeException("boom");
        }
    }
}
