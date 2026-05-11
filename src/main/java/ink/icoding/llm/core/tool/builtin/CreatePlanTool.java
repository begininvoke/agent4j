package ink.icoding.llm.core.tool.builtin;

import ink.icoding.llm.agent.Plan;
import ink.icoding.llm.core.tool.Tool;
import ink.icoding.llm.core.tool.ToolParam;
import ink.icoding.llm.core.tool.annotations.ToolInfo;
import ink.icoding.llm.core.tool.builtin.param.CreatePlanParam;

import java.util.ArrayList;
import java.util.List;

/**
 * 创建计划工具.
 * <p>创建一个包含多个步骤的执行计划. 计划创建后由会话负责逐步执行.
 * 计划用完即毁.</p>
 *
 * @author gsk
 */
@ToolInfo(name = "create_plan", description = "创建并执行一个多步骤计划. 接收计划名称、描述和步骤列表, 系统会逐步执行每个步骤并报告进度. 适用于需要分解执行的复杂任务.")
public class CreatePlanTool implements Tool<CreatePlanParam> {

    private Plan lastCreatedPlan;

    @Override
    public String execute(CreatePlanParam p) {
        if (p.getSteps() == null || p.getSteps().length == 0) {
            return "Error: plan must have at least one step";
        }

        Plan plan = new Plan(p.getName(), p.getDescription());
        plan.setSteps(List.of(p.getSteps()));
        this.lastCreatedPlan = plan;

        return "Plan created: " + p.getName() + " (" + p.getSteps().length + " steps). Will be executed now.";
    }

    public Plan getLastCreatedPlan() {
        Plan p = lastCreatedPlan;
        lastCreatedPlan = null;
        return p;
    }
}
