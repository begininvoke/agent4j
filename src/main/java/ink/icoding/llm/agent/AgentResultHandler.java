package ink.icoding.llm.agent;

import ink.icoding.llm.core.model.ResultHandler;
import ink.icoding.llm.core.tool.ToolDescriptor;
import ink.icoding.llm.core.tool.ToolStatus;

/**
 * 智能体结果回调处理器接口.
 * <p>扩展了 {@link ResultHandler}, 增加了子Agent和计划相关的回调方法.</p>
 *
 * @author gsk
 */
public interface AgentResultHandler extends ResultHandler {

    /**
     * 当子Agent被调用时回调.
     *
     * @param agent   子Agent实例
     * @param message 调用消息
     */
    default void onSubAgent(AgentClient agent, String message) {}

    /**
     * 当子Agent开始执行时回调.
     *
     * @param agent  子Agent实例
     * @param task   子Agent任务
     */
    default void onSubAgentStart(AgentClient agent, String task) {}

    /**
     * 当子Agent返回结果时回调.
     *
     * @param agent  子Agent实例
     * @param result 执行结果
     */
    default void onSubAgentResult(AgentClient agent, String result) {}

    /**
     * 当计划被创建时回调.
     *
     * @param plan 被创建的计划
     */
    default void onPlanCreated(Plan plan) {}

    /**
     * 当计划开始执行时回调.
     *
     * @param plan 被执行的计划
     */
    default void onPlanExecuted(Plan plan) {}

    /**
     * 当计划中的某个步骤开始执行时回调.
     *
     * @param plan    所属计划
     * @param current 当前步骤索引 (从1开始)
     * @param total   总步骤数
     * @param step    步骤描述
     */
    default void onPlanStepStart(Plan plan, int current, int total, String step) {}

    /**
     * 当计划中的某个步骤执行完成时回调.
     *
     * @param plan    所属计划
     * @param current 当前步骤索引 (从1开始)
     * @param total   总步骤数
     * @param step    步骤描述
     * @param result  步骤执行结果
     */
    default void onPlanStepComplete(Plan plan, int current, int total, String step, String result) {}

    /**
     * 当计划步骤执行过程中工具调用状态变化时回调.
     *
     * @param plan   所属计划
     * @param tool   工具描述对象
     * @param status 工具调用状态
     */
    default void onPlanStepTool(Plan plan, ToolDescriptor tool, ToolStatus status) {}

    /**
     * 当计划步骤中的工具调用状态变化时回调, 并携带工具执行结果.
     *
     * @param plan   所属计划
     * @param tool   工具描述对象
     * @param status 工具调用状态
     * @param result 工具执行结果; 尚未完成时为 {@code null}
     */
    default void onPlanStepTool(Plan plan, ToolDescriptor tool, ToolStatus status, Object result) {
        onPlanStepTool(plan, tool, status);
    }

    /**
     * 当计划中的某个步骤执行出错时回调.
     *
     * @param plan    所属计划
     * @param current 当前步骤索引 (从1开始)
     * @param total   总步骤数
     * @param step    步骤描述
     * @param error   异常信息
     */
    default void onPlanStepError(Plan plan, int current, int total, String step, Exception error) {}
}
