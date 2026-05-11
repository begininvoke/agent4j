package ink.icoding.llm.core.tool.builtin.param;

import ink.icoding.llm.core.tool.ToolParam;
import ink.icoding.llm.core.tool.annotations.Param;

/**
 * 从目录中搜索文件内容参数.
 *
 * @author gsk
 */
public class SearchInDirectoryParam extends ToolParam {

    @Param(description = "目录路径")
    private String path;

    @Param(description = "搜索内容列表")
    private String[] patterns;

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String[] getPatterns() { return patterns; }
    public void setPatterns(String[] patterns) { this.patterns = patterns; }
}
