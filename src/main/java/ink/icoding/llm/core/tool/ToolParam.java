package ink.icoding.llm.core.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 工具参数抽象基类.
 * <p>所有工具入参实体都应继承此类. 提供JSON序列化/反序列化能力,
 * LLM返回的工具调用参数JSON会被自动转换为对应的ToolParam子类实例.</p>
 * <p>使用示例:</p>
 * <pre>{@code
 * public class SearchParam extends ToolParam {
 *     private String query;
 *     private int limit;
 * }
 *
 * SearchParam param = ToolParam.fromJsonString("{\"query\":\"test\",\"limit\":10}", SearchParam.class);
 * }</pre>
 *
 * @author gsk
 */
public abstract class ToolParam {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /**
     * 从JSON字符串反序列化为ToolParam子类实例.
     *
     * @param json  JSON字符串
     * @param clazz 目标类型
     * @param <T>   ToolParam子类类型
     * @return 反序列化后的实例
     * @throws RuntimeException 如果反序列化失败
     */
    public static <T extends ToolParam> T fromJsonString(String json, Class<T> clazz) {
        if (json == null || json.isEmpty() || "{}".equals(json)) {
            try {
                return clazz.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new RuntimeException("Failed to create empty tool param instance for class: " + clazz.getName(), e);
            }
        }
        try {
            return MAPPER.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse tool param JSON: " + json, e);
        }
    }

    /**
     * 将当前实例序列化为JSON字符串.
     *
     * @return JSON字符串
     * @throws RuntimeException 如果序列化失败
     */
    public String toJsonString() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize tool param", e);
        }
    }
}
