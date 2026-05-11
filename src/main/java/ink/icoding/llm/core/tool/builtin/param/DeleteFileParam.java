package ink.icoding.llm.core.tool.builtin.param;

import ink.icoding.llm.core.tool.ToolParam;
import ink.icoding.llm.core.tool.annotations.Param;

/**
 * 删除文件参数.
 *
 * @author gsk
 */
public class DeleteFileParam extends ToolParam {

    @Param(description = "文件路径")
    private String path;

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
}
