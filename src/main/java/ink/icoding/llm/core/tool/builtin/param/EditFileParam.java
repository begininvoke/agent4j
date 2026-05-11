package ink.icoding.llm.core.tool.builtin.param;

import ink.icoding.llm.core.tool.ToolParam;
import ink.icoding.llm.core.tool.annotations.Param;

/**
 * 编辑文件参数.
 *
 * @author gsk
 */
public class EditFileParam extends ToolParam {

    @Param(description = "文件路径")
    private String path;

    @Param(required = false, description = "操作模式: replace(替换,默认), insert(在指定行前插入), append(在文件末尾追加)")
    private String mode;

    @Param(required = false, description = "起始行号(1-based), replace模式和insert模式需要. append模式忽略此参数")
    private Integer startLine;

    @Param(required = false, description = "结束行号(1-based), replace模式需要, 与startLine相同时替换单行. insert和append模式忽略此参数")
    private Integer endLine;

    @Param(description = "内容")
    private String content;

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public Integer getStartLine() { return startLine; }
    public void setStartLine(Integer startLine) { this.startLine = startLine; }
    public Integer getEndLine() { return endLine; }
    public void setEndLine(Integer endLine) { this.endLine = endLine; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
