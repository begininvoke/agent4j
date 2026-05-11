package ink.icoding.llm.agent;

import java.util.ArrayList;
import java.util.List;

/**
 * 计划实体.
 * <p>表示智能体创建的执行计划, 包含计划名称、描述和步骤列表.
 * 计划用完即毁, 不会持久化.</p>
 *
 * @author gsk
 */
public class Plan {
    private String name;
    private String description;
    private List<String> steps = new ArrayList<>();

    /** 无参构造器 */
    public Plan() {}

    /**
     * 全参构造器.
     *
     * @param name        计划名称
     * @param description 计划描述
     */
    public Plan(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public List<String> getSteps() { return steps; }
    public void setSteps(List<String> steps) { this.steps = steps; }
}
