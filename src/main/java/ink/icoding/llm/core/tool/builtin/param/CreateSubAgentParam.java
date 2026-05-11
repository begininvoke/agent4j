package ink.icoding.llm.core.tool.builtin.param;

import ink.icoding.llm.core.tool.ToolParam;

/**
 * 创建子Agent参数.
 *
 * @author gsk
 */
public class CreateSubAgentParam extends ToolParam {

    @ink.icoding.llm.core.tool.annotations.Param(description = "子Agent名称")
    private String name;

    @ink.icoding.llm.core.tool.annotations.Param(description = "子Agent的职责描述")
    private String description;

    @ink.icoding.llm.core.tool.annotations.Param(description = "要交给子Agent执行的任务")
    private String task;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getTask() { return task; }
    public void setTask(String task) { this.task = task; }
}
