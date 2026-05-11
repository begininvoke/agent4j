package ink.icoding.llm.core.tool.builtin.param;

import ink.icoding.llm.core.tool.ToolParam;
import ink.icoding.llm.core.tool.annotations.Param;

/**
 * 创建文件参数.
 *
 * @author gsk
 */
public class CreateFileParam extends ToolParam {

    @Param(description = "文件路径")
    private String path;

    @Param(description = "文件内容")
    private String content;

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
