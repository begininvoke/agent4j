package ink.icoding.llm.core.tool.builtin.param;

import ink.icoding.llm.core.tool.ToolParam;
import ink.icoding.llm.core.tool.annotations.Param;

/**
 * 查看文件参数.
 *
 * @author gsk
 */
public class ViewFileParam extends ToolParam {

    @Param(description = "文件路径")
    private String path;

    @Param(required = false, description = "起始行号(1-based), 默认从第1行开始")
    private Integer startLine;

    @Param(required = false, description = "结束行号(1-based), 默认到文件末尾")
    private Integer endLine;

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public Integer getStartLine() { return startLine; }
    public void setStartLine(Integer startLine) { this.startLine = startLine; }
    public Integer getEndLine() { return endLine; }
    public void setEndLine(Integer endLine) { this.endLine = endLine; }
}
