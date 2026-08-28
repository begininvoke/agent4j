package ink.icoding.llm.core.tool;

/**
 * 工具执行器接口.
 * <p>允许会话层自定义工具执行逻辑, 用于拦截特殊工具(如计划、子Agent)的执行.
 * 普通工具应委托给 {@link #defaultExecute(Tool, String, ToolDescriptor, ink.icoding.llm.core.model.ResultHandler)} 执行.</p>
 *
 * @author gsk
 */
public interface ToolExecutor {

    /**
     * 执行工具.
     *
     * @param toolName   工具名称
     * @param paramJson  工具参数JSON字符串
     * @param descriptor 工具描述对象
     * @param handler    结果回调处理器
     * @return 工具执行结果
     */
    String execute(String toolName, String paramJson, ToolDescriptor descriptor, ink.icoding.llm.core.model.ResultHandler handler);

    /**
     * 默认工具执行逻辑: 反序列化参数并调用工具的execute方法.
     *
     * @param tool       工具实例
     * @param paramJson  工具参数JSON字符串
     * @param descriptor 工具描述对象
     * @param handler    结果回调处理器
     * @return 工具执行结果
     */
    @SuppressWarnings("unchecked")
    static String defaultExecute(Tool<?> tool, String paramJson, ToolDescriptor descriptor, ink.icoding.llm.core.model.ResultHandler handler) {
        try {
            if (handler != null) handler.onTool(descriptor, ToolStatus.PREPARING, null);
            if (handler != null) handler.onTool(descriptor, ToolStatus.CALLING, null);
            Tool<ToolParam> typedTool = (Tool<ToolParam>) tool;
            String result = typedTool.execute(ToolParam.fromJsonString(paramJson, descriptor.getParamClass()));
            if (handler != null) handler.onTool(descriptor, ToolStatus.COMPLETED, result);
            return result;
        } catch (Exception e) {
            return handleToolError(descriptor, handler, e);
        }
    }

    /**
     * 处理工具执行异常, 避免异常冒泡中断Agent循环.
     *
     * @param descriptor 工具描述对象
     * @param handler    结果回调处理器
     * @param error      工具执行异常
     * @return 反馈给LLM的工具错误结果
     */
    static String handleToolError(ToolDescriptor descriptor, ink.icoding.llm.core.model.ResultHandler handler, Exception error) {
        if (handler != null) {
            try {
                handler.onToolError(descriptor, error);
            } catch (Exception handlerError) {
                System.err.println("[Tool Error Handler Failed] " + handlerError.getMessage());
                handlerError.printStackTrace(System.err);
            }
        } else {
            String toolName = descriptor == null ? "unknown" : descriptor.getName();
            System.err.println("[Tool Error] " + toolName + ": " + error.getMessage());
            error.printStackTrace(System.err);
        }
        String toolName = descriptor == null ? "unknown" : descriptor.getName();
        return "Tool '" + toolName + "' failed: " + error.getMessage();
    }
}
