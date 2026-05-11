package ink.icoding.llm.core.tool.builtin.skill;

import ink.icoding.llm.agent.Skill;
import ink.icoding.llm.core.tool.Tool;
import ink.icoding.llm.core.tool.builtin.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 内置技能和工具集合.
 * <p>提供预定义的技能和工具实例, 方便快速构建智能体.</p>
 *
 * @author gsk
 */
public class BuiltInSkills {

    private BuiltInSkills() {}

    /**
     * 获取所有内置技能.
     *
     * @return 技能列表
     */
    public static List<Skill> all() {
        List<Skill> skills = new ArrayList<>();
        skills.add(fileSystem());
        skills.add(commandExecution());
        skills.add(orchestration());
        return skills;
    }

    /**
     * 获取文件系统操作技能.
     *
     * @return 文件系统技能
     */
    public static Skill fileSystem() {
        return new FileSystemSkill();
    }

    /**
     * 获取命令执行技能.
     *
     * @return 命令执行技能
     */
    public static Skill commandExecution() {
        return new CommandExecutionSkill();
    }

    /**
     * 获取编排技能(计划和子Agent).
     *
     * @return 编排技能
     */
    public static Skill orchestration() {
        return new OrchestrationSkill();
    }

    /**
     * 获取所有内置工具.
     *
     * @return 工具列表
     */
    public static List<Tool> allTools() {
        List<Tool> tools = new ArrayList<>();
        tools.add(new ListDirectoryTreeTool());
        tools.add(new ViewFileTool());
        tools.add(new SearchInFileTool());
        tools.add(new SearchInDirectoryTool());
        tools.add(new SearchFilesTool());
        tools.add(new EditFileTool());
        tools.add(new CreateFileTool());
        tools.add(new DeleteFileTool());
        tools.add(new MoveFileTool());
        tools.add(new ExecuteCommandTool());
        tools.add(new CreatePlanTool());
        tools.add(new CreateSubAgentTool());
        return tools;
    }
}
