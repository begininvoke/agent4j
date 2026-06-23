package ink.icoding.llm.core.entity;

/**
 * 协议无关的工具返回记录.
 */
public class MessageToolResult {
    private String toolCallId;
    private String content;

    public MessageToolResult() {}

    public MessageToolResult(String toolCallId, String content) {
        this.toolCallId = toolCallId;
        this.content = content;
    }

    public String getToolCallId() { return toolCallId; }
    public void setToolCallId(String toolCallId) { this.toolCallId = toolCallId; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
