package ink.icoding.llm.core.entity;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 消息实体.
 * <p>表示LLM对话中的一条消息, 支持文本内容、附件和思考过程.
 * 通过静态工厂方法和链式调用构建消息:</p>
 * <pre>{@code
 * Message msg = Message.fromUser("你好");
 * msg.appendAttachment(imageFile);
 * msg.appendThink("让我想想...");
 * }</pre>
 *
 * @author gsk
 */
public class Message {
    private String content;
    private List<MessageAttachment> attachments = new ArrayList<>();
    private String think;
    private Role role;

    /**
     * 消息角色枚举.
     */
    public enum Role {
        /** 用户消息 */
        user,
        /** 助手消息 */
        assistant,
        /** 工具返回消息 */
        tool
    }

    /** 无参构造器 */
    public Message() {}

    private Message(Role role) {
        this.role = role;
    }

    private Message(Role role, String content) {
        this.role = role;
        this.content = content;
    }

    /**
     * 创建用户消息.
     *
     * @param content 消息内容
     * @return 消息对象
     */
    public static Message fromUser(String content) {
        return new Message(Role.user, content);
    }

    /**
     * 创建空的用户消息, 用于后续链式添加内容.
     *
     * @return 消息对象
     */
    public static Message fromUser() {
        return new Message(Role.user);
    }

    /**
     * 创建助手消息.
     *
     * @param content 消息内容
     * @return 消息对象
     */
    public static Message fromAssistant(String content) {
        return new Message(Role.assistant, content);
    }

    /**
     * 创建空的助手消息.
     *
     * @return 消息对象
     */
    public static Message fromAssistant() {
        return new Message(Role.assistant);
    }

    /**
     * 创建工具返回消息.
     *
     * @param content 工具返回内容
     * @return 消息对象
     */
    public static Message fromTool(String content) {
        return new Message(Role.tool, content);
    }

    /**
     * 追加文本内容.
     *
     * @param content 要追加的文本
     * @return 当前消息对象, 支持链式调用
     */
    public Message appendContent(String content) {
        if (this.content == null) {
            this.content = content;
        } else {
            this.content += content;
        }
        return this;
    }

    /**
     * 追加内存文件附件.
     *
     * @param file 内存文件
     * @return 当前消息对象, 支持链式调用
     */
    public Message appendAttachment(MemoryMultipartFile file) {
        this.attachments.add(MessageAttachment.fromMultipart(file));
        return this;
    }

    /**
     * 追加磁盘文件附件.
     *
     * @param file 磁盘文件
     * @return 当前消息对象, 支持链式调用
     */
    public Message appendAttachment(File file) {
        this.attachments.add(MessageAttachment.fromFile(file));
        return this;
    }

    /**
     * 追加字节数组附件.
     *
     * @param data        字节数组
     * @param contentType MIME类型
     * @return 当前消息对象, 支持链式调用
     */
    public Message appendAttachment(byte[] data, String contentType) {
        this.attachments.add(MessageAttachment.fromBytes(data, contentType));
        return this;
    }

    /**
     * 追加思考/推理内容.
     *
     * @param think 思考内容
     * @return 当前消息对象, 支持链式调用
     */
    public Message appendThink(String think) {
        if (this.think == null) {
            this.think = think;
        } else {
            this.think += think;
        }
        return this;
    }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public List<MessageAttachment> getAttachments() { return attachments; }
    public void setAttachments(List<MessageAttachment> attachments) { this.attachments = attachments; }
    public String getThink() { return think; }
    public void setThink(String think) { this.think = think; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
}
