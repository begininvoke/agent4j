package ink.icoding.llm.core.model;

import ink.icoding.llm.core.entity.ModelType;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LLMRequestDebugLoggerTest {

    @Test
    void factoryCanCreateModelWithRequestDebugEnabled() {
        LLMModel model = LLMModel.create(ModelType.OpenAI, "https://example.com", "test-model", "secret", true);

        assertTrue(model.isRequestDebugEnabled());
        model.setRequestDebugEnabled(false);
        assertFalse(model.isRequestDebugEnabled());
    }

    @Test
    void loggerPrintsRequestAndMasksSensitiveHeaders() {
        Request request = new Request.Builder()
                .url("https://example.com/v1/chat/completions")
                .header("Authorization", "Bearer sk-secret")
                .post(RequestBody.create("{\"model\":\"test\"}", MediaType.parse("application/json")))
                .build();

        PrintStream originalErr = System.err;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        System.setErr(new PrintStream(output));
        try {
            LLMRequestDebugLogger.log(true, request, "{\"model\":\"test\"}");
        } finally {
            System.setErr(originalErr);
        }

        String log = output.toString();
        assertTrue(log.contains("https://example.com/v1/chat/completions"));
        assertTrue(log.contains("Authorization: ****cret"));
        assertFalse(log.contains("Bearer sk-secret"));
        assertTrue(log.contains("{\"model\":\"test\"}"));
    }

    @Test
    void loggerPrintsRawStreamEventWhenEnabled() {
        PrintStream originalErr = System.err;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        System.setErr(new PrintStream(output));
        try {
            LLMRequestDebugLogger.logStreamEvent(true, "evt_1", "response.output_text.delta", "{\"delta\":\"hi\"}");
        } finally {
            System.setErr(originalErr);
        }

        String log = output.toString();
        assertTrue(log.contains("[DEBUG] LLM Stream Event"));
        assertTrue(log.contains("ID: evt_1"));
        assertTrue(log.contains("Type: response.output_text.delta"));
        assertTrue(log.contains("{\"delta\":\"hi\"}"));
    }

    @Test
    void loggerSkipsStreamEventWhenDisabled() {
        PrintStream originalErr = System.err;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        System.setErr(new PrintStream(output));
        try {
            LLMRequestDebugLogger.logStreamEvent(false, "evt_1", "message_delta", "{\"delta\":{}}");
        } finally {
            System.setErr(originalErr);
        }

        assertTrue(output.toString().isEmpty());
    }
}
