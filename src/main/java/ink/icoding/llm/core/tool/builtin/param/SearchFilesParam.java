package ink.icoding.llm.core.tool.builtin.param;

import ink.icoding.llm.core.tool.ToolParam;
import ink.icoding.llm.core.tool.annotations.Param;

/**
 * 从目录中搜索文件参数.
 *
 * @author gsk
 */
public class SearchFilesParam extends ToolParam {

    @Param(description = "目录路径")
    private String path;

    @Param(description = "文件名关键字列表")
    private String[] keywords;

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String[] getKeywords() { return keywords; }
    public void setKeywords(String[] keywords) { this.keywords = keywords; }
}
