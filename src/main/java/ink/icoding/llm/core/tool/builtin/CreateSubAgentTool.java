package ink.icoding.llm.core.tool.builtin;

import ink.icoding.llm.agent.AgentClient;
import ink.icoding.llm.core.tool.Tool;
import ink.icoding.llm.core.tool.ToolParam;
import ink.icoding.llm.core.tool.annotations.ToolInfo;
import ink.icoding.llm.core.tool.builtin.param.CreateSubAgentParam;

/**
 * 创建子Agent工具.
 * <p>创建一个临时的子Agent来处理子任务, 执行完毕后自动释放.</p>
 *
 * @author gsk
 */
@ToolInfo(name = "create_sub_agent", description = "创建一个临时的子Agent来处理子任务. 子Agent拥有与父Agent相同的工具和技能, 独立执行指定任务后返回结果. 适用于需要独立上下文的子任务.")
public class CreateSubAgentTool implements Tool<CreateSubAgentParam> {

    private AgentClient lastCreatedAgent;
    private String lastTask;

    @Override
    public String execute(CreateSubAgentParam p) {
        AgentClient sub = new AgentClient();
        sub.setName(p.getName());
        sub.setDescription(p.getDescription());
        this.lastCreatedAgent = sub;
        this.lastTask = p.getTask();

        return "Sub-agent created: " + p.getName() + ". Task: " + p.getTask() + ". Will be executed now.";
    }

    public AgentClient getLastCreatedAgent() {
        AgentClient a = lastCreatedAgent;
        lastCreatedAgent = null;
        return a;
    }

    public String getLastTask() {
        String t = lastTask;
        lastTask = null;
        return t;
    }
}
