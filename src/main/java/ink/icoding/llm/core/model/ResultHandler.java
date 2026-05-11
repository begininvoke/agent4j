package ink.icoding.llm.core.model;

import ink.icoding.llm.core.tool.ToolDescriptor;
import ink.icoding.llm.core.tool.ToolStatus;

/**
 * LLM结果回调处理器接口.
 * <p>定义了处理LLM流式响应的回调方法, 包括文本内容、思考过程和工具调用.</p>
 *
 * @author gsk
 */
public interface ResultHandler {

    /**
     * 当收到文本内容时回调.
     *
     * @param message 文本内容片段
     */
    default void onMessage(String message) {}

    /**
     * 当收到思考/推理内容时回调.
     *
     * @param think 思考内容片段
     */
    default void onThink(String think) {}

    /**
     * 当工具调用状态变化时回调.
     *
     * @param tool   工具描述对象
     * @param status 工具调用状态
     */
    default void onTool(ToolDescriptor tool, ToolStatus status) {}
}
