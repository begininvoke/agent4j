package ink.icoding.llm.core.model;

import okhttp3.Headers;
import okhttp3.Request;

/**
 * LLM请求调试日志.
 */
public final class LLMRequestDebugLogger {
    private LLMRequestDebugLogger() {}

    public static void log(boolean enabled, Request request, String body) {
        if (!enabled || request == null) {
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("[DEBUG] LLM Request\n");
        sb.append("URL: ").append(request.url()).append("\n");
        sb.append("Headers:\n");
        Headers headers = request.headers();
        for (String name : headers.names()) {
            sb.append("  ").append(name).append(": ")
                    .append(maskHeader(name, headers.get(name)))
                    .append("\n");
        }
        sb.append("Body:\n");
        sb.append(body == null ? "" : body);
        System.err.println(sb);
    }

    private static String maskHeader(String name, String value) {
        if (value == null) {
            return "";
        }
        String lower = name == null ? "" : name.toLowerCase();
        if (!"authorization".equals(lower) && !"x-api-key".equals(lower) && !"api-key".equals(lower)) {
            return value;
        }
        int keep = Math.min(4, value.length());
        return "****" + value.substring(value.length() - keep);
    }
}
