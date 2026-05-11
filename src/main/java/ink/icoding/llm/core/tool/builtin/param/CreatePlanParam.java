package ink.icoding.llm.core.tool.builtin.param;

import ink.icoding.llm.core.tool.ToolParam;

/**
 * 创建计划参数.
 *
 * @author gsk
 */
public class CreatePlanParam extends ToolParam {

    @ink.icoding.llm.core.tool.annotations.Param(description = "计划名称")
    private String name;

    @ink.icoding.llm.core.tool.annotations.Param(description = "计划描述")
    private String description;

    @ink.icoding.llm.core.tool.annotations.Param(description = "计划步骤列表, 每个步骤是一个要执行的任务描述")
    private String[] steps;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String[] getSteps() { return steps; }
    public void setSteps(String[] steps) { this.steps = steps; }
}
