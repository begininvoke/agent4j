package ink.icoding.llm.agent;

import ink.icoding.llm.core.tool.Tool;

import java.util.ArrayList;
import java.util.List;

/**
 * 技能实体.
 * <p>表示智能体具备的技能, 包含技能标题、描述、关联工具和技能内容说明.
 * 技能可以看作是一组工具的集合加上使用说明, 帮助LLM更好地理解和使用工具.</p>
 *
 * @author gsk
 */
public class Skill {
    private String title;
    private String description;
    private List<Tool> tools = new ArrayList<>();
    private String content;

    /** 无参构造器 */
    public Skill() {}

    /**
     * 全参构造器.
     *
     * @param title       技能标题
     * @param description 技能描述
     * @param tools       关联工具列表
     * @param content     技能内容说明
     */
    public Skill(String title, String description, List<Tool> tools, String content) {
        this.title = title;
        this.description = description;
        this.tools = tools;
        this.content = content;
    }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public List<Tool> getTools() { return tools; }
    public void setTools(List<Tool> tools) { this.tools = tools; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
