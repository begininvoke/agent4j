package ink.icoding.llm.core.entity;

/**
 * 协议无关的工具调用记录.
 */
public class MessageToolCall {
    private String id;
    private String name;
    private String argumentsJson;

    public MessageToolCall() {}

    public MessageToolCall(String id, String name, String argumentsJson) {
        this.id = id;
        this.name = name;
        this.argumentsJson = argumentsJson;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getArgumentsJson() { return argumentsJson; }
    public void setArgumentsJson(String argumentsJson) { this.argumentsJson = argumentsJson; }
}
