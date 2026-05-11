package ink.icoding.llm.core.tool.builtin.param;

import ink.icoding.llm.core.tool.ToolParam;
import ink.icoding.llm.core.tool.annotations.Param;

/**
 * 查看目录文件树参数.
 *
 * @author gsk
 */
public class ListDirectoryTreeParam extends ToolParam {

    @Param(description = "目录路径")
    private String path;

    @Param(required = false, description = "最大层级深度, 默认5")
    private Integer maxDepth;

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public Integer getMaxDepth() { return maxDepth; }
    public void setMaxDepth(Integer maxDepth) { this.maxDepth = maxDepth; }
}
