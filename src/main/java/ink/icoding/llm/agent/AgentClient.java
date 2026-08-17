package ink.icoding.llm.agent;

import ink.icoding.llm.core.model.LLMModel;
import ink.icoding.llm.core.tool.Tool;

import java.util.ArrayList;
import java.util.List;

/**
 * 智能体客户端.
 * <p>基于 {@link LLMModel} 的智能体化封装, 预置了名称、描述、工具集和技能集,
 * 使LLM具备更智能的自主决策能力.</p>
 * <p>使用示例:</p>
 * <pre>{@code
 * AgentClient agent = new AgentClient();
 * agent.setModel(LLMModel.create(ModelType.OpenAI, baseUrl, modelName, apiKey));
 * agent.setName("助手");
 * agent.setDescription("一个智能助手");
 * agent.getTools().add(new SearchTool());
 *
 * AgentClientSession session = agent.createSession();
 * session.command("帮我搜索Java最新版本").then(handler).error(errHandler);
 * }</pre>
 *
 * @author gsk
 */
public class AgentClient {
    private LLMModel model;
    private String name;
    private String description;
    private List<Tool> tools = new ArrayList<>();
    private List<Skill> skills = new ArrayList<>();
    private boolean llmRequestDebugEnabled;
    private Boolean thinkingEnabled;
    private Double temperature;
    private boolean builtInAgentToolsEnabled = true;

    /**
     * 创建新的会话.
     *
     * @return 新的智能体会话实例
     */
    public AgentClientSession createSession() {
        return new AgentClientSession(this);
    }

    /**
     * 从序列化数据恢复会话.
     *
     * @param json 序列化JSON字符串
     * @return 恢复的会话实例
     */
    public AgentClientSession getSessionFromSerialization(String json) {
        return AgentClientSession.fromSerialization(json, this);
    }

    public LLMModel getModel() { return model; }
    public void setModel(LLMModel model) {
        this.model = model;
        if (this.model != null) {
            this.model.setRequestDebugEnabled(llmRequestDebugEnabled);
            this.model.setThinkingEnabled(thinkingEnabled);
            this.model.setTemperature(temperature);
        }
    }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public List<Tool> getTools() { return tools; }
    public void setTools(List<Tool> tools) { this.tools = tools == null ? new ArrayList<>() : tools; }
    public List<Skill> getSkills() { return skills; }
    public void setSkills(List<Skill> skills) { this.skills = skills == null ? new ArrayList<>() : skills; }
    public boolean isBuiltInAgentToolsEnabled() { return builtInAgentToolsEnabled; }
    public void setBuiltInAgentToolsEnabled(boolean builtInAgentToolsEnabled) {
        this.builtInAgentToolsEnabled = builtInAgentToolsEnabled;
    }
    public AgentClient clearAllSkills() {
        tools.clear();
        skills.clear();
        builtInAgentToolsEnabled = false;
        return this;
    }
    public boolean isLlmRequestDebugEnabled() { return llmRequestDebugEnabled; }
    public void setLlmRequestDebugEnabled(boolean llmRequestDebugEnabled) {
        this.llmRequestDebugEnabled = llmRequestDebugEnabled;
        if (this.model != null) {
            this.model.setRequestDebugEnabled(llmRequestDebugEnabled);
        }
    }
    public Boolean getThinkingEnabled() { return thinkingEnabled; }
    public void setThinkingEnabled(Boolean thinkingEnabled) {
        this.thinkingEnabled = thinkingEnabled;
        if (this.model != null) {
            this.model.setThinkingEnabled(thinkingEnabled);
        }
    }
    public void enableThinking() { setThinkingEnabled(true); }
    public void disableThinking() { setThinkingEnabled(false); }
    public Double getTemperature() { return temperature; }
    public void setTemperature(Double temperature) {
        this.temperature = temperature;
        if (this.model != null) {
            this.model.setTemperature(temperature);
        }
    }
}
