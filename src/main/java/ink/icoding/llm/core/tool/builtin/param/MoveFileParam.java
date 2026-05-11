package ink.icoding.llm.core.tool.builtin.param;

import ink.icoding.llm.core.tool.ToolParam;
import ink.icoding.llm.core.tool.annotations.Param;

/**
 * 移动文件参数.
 *
 * @author gsk
 */
public class MoveFileParam extends ToolParam {

    @Param(description = "原文件路径")
    private String source;

    @Param(description = "新文件路径")
    private String target;

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }
}
