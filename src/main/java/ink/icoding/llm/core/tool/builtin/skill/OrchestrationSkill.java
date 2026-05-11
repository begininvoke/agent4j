package ink.icoding.llm.core.tool.builtin.skill;

import ink.icoding.llm.agent.Skill;
import ink.icoding.llm.core.tool.Tool;
import ink.icoding.llm.core.tool.builtin.CreatePlanTool;
import ink.icoding.llm.core.tool.builtin.CreateSubAgentTool;

import java.util.List;

/**
 * 编排技能.
 * <p>提供计划创建和子Agent创建能力, 使智能体能够分解复杂任务并协调执行.
 * 计划和子Agent都是临时的, 用完即毁.</p>
 *
 * @author gsk
 */
public class OrchestrationSkill extends Skill {

    public OrchestrationSkill() {
        setTitle("Task Orchestration");
        setDescription("Create execution plans and sub-agents to coordinate complex tasks");
        setTools(List.of(new CreatePlanTool(), new CreateSubAgentTool()));
        setContent("""
                ## Task Orchestration Guide

                ### Creating Plans
                Use `create_plan` to break down complex tasks into sequential steps:
                - Each step is executed as an independent LLM call with full tool access
                - Steps are executed in order, and each step's result is reported
                - Plans are temporary and destroyed after execution

                ### Creating Sub-Agents
                Use `create_sub_agent` to delegate independent sub-tasks:
                - Sub-agents inherit the parent agent's tools and skills
                - Sub-agents execute independently and return results
                - Sub-agents are temporary and destroyed after execution

                ### When to Use Plans vs Sub-Agents
                - **Plan**: When steps depend on each other and must be sequential
                - **Sub-Agent**: When a sub-task is independent and can be parallelized
                """);
    }
}
