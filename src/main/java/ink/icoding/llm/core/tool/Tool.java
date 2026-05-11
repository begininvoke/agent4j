package ink.icoding.llm.core.tool;

/**
 * 工具公共接口.
 * <p>所有可被LLM调用的工具都需要实现此接口.
 * 工具通过 {@link ink.icoding.llm.core.tool.annotations.ToolInfo} 注解声明名称和描述,
 * 参数通过继承 {@link ToolParam} 的子类定义.</p>
 *
 * @author gsk
 */
public interface Tool<T extends ToolParam> {

    /**
     * 执行工具.
     *
     * @param param 工具参数
     * @return 执行结果字符串
     */
    String execute(T param);

    /**
     * 执行工具, 支持进度回调.
     *
     * @param param   工具参数
     * @param handler 进度回调处理器
     * @return 执行结果字符串
     */
    default String execute(T param, Handler handler) {
        return execute(param);
    }

    /**
     * 工具执行进度回调接口.
     */
    interface Handler {
        /**
         * 进度回调.
         *
         * @param message 进度信息
         */
        default void onProgress(String message) {}
    }
}
