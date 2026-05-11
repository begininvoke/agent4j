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
        if (handler != null) handler.onTool(descriptor, ToolStatus.PREPARING);
        if (handler != null) handler.onTool(descriptor, ToolStatus.CALLING);
        Tool<ToolParam> typedTool = (Tool<ToolParam>) tool;
        String result = typedTool.execute(ToolParam.fromJsonString(paramJson, descriptor.getParamClass()));
        if (handler != null) handler.onTool(descriptor, ToolStatus.COMPLETED);
        return result;
    }
}
