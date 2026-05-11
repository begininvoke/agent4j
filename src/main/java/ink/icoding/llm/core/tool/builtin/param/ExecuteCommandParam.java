package ink.icoding.llm.core.tool.builtin.param;

import ink.icoding.llm.core.tool.ToolParam;
import ink.icoding.llm.core.tool.annotations.Param;

/**
 * 执行命令参数.
 *
 * @author gsk
 */
public class ExecuteCommandParam extends ToolParam {

    @Param(description = "要执行的命令")
    private String command;

    public String getCommand() { return command; }
    public void setCommand(String command) { this.command = command; }
}
