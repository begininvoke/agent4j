package ink.icoding.llm.core.tool;

/**
 * 工具调用状态枚举.
 * <p>表示工具在LLM调用过程中的生命周期状态.</p>
 *
 * @author gsk
 */
public enum ToolStatus {

    /** 准备调用 - LLM决定调用工具, 尚未执行 */
    PREPARING,

    /** 调用中 - 工具正在执行 */
    CALLING,

    /** 调用结束 - 工具执行完成 */
    COMPLETED
}
