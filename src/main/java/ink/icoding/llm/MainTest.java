package ink.icoding.llm;

import ink.icoding.llm.agent.AgentClient;
import ink.icoding.llm.agent.AgentClientSession;
import ink.icoding.llm.agent.AgentResultHandler;
import ink.icoding.llm.agent.Plan;
import ink.icoding.llm.core.entity.ModelType;
import ink.icoding.llm.core.model.LLMModel;
import ink.icoding.llm.core.tool.ToolDescriptor;
import ink.icoding.llm.core.tool.ToolStatus;
import ink.icoding.llm.core.tool.builtin.skill.BuiltInSkills;

public class MainTest {
    public static void main(String[] args) {
        String baseURL = System.getenv("BASE_URL");
        String apiKey = System.getenv("API_KEY");
        String model = System.getenv("MODEL");

        LLMModel llm = LLMModel.create(ModelType.OpenAI, baseURL, model, apiKey);

        AgentClient agent = new AgentClient();
        agent.setName("CodeAgent");
        agent.setDescription("一个专业的全栈开发工程师, 擅长创建Chrome插件和Java Spring Boot后端应用.");
        agent.setModel(llm);
        agent.getSkills().addAll(BuiltInSkills.all());

        AgentClientSession session = agent.createSession();

        String task = """
                创建一个子任务，子任务输出“你好”
                """;

        session.command(task).then(new AgentResultHandler() {
            @Override
            public void onMessage(String message) {
                System.out.print(message);
            }

            @Override
            public void onThink(String think) {
                System.out.print(think);
            }

            @Override
            public void onTool(ToolDescriptor tool, ToolStatus status) {
                switch (status) {
                    case PREPARING -> {
                        System.out.println("\n[工具准备] " + tool.getName());
                        if (tool.getInputParams() != null && !tool.getInputParams().isEmpty()) {
                            System.out.println("  入参: " + tool.getInputParams());
                        }
                    }
                    case CALLING -> System.out.println("[工具调用中] " + tool.getName());
                    case COMPLETED -> System.out.println("[工具完成] " + tool.getName());
                }
            }

            @Override
            public void onPlanCreated(Plan plan) {
                System.out.println("\n[计划创建] " + plan.getName());
                System.out.println("  描述: " + plan.getDescription());
                System.out.println("  步骤数: " + plan.getSteps().size());
                for (int i = 0; i < plan.getSteps().size(); i++) {
                    System.out.println("    " + (i + 1) + ". " + plan.getSteps().get(i));
                }
            }

            @Override
            public void onPlanExecuted(Plan plan) {
                System.out.println("\n[计划执行开始] " + plan.getName());
            }

            @Override
            public void onPlanStepStart(Plan plan, int current, int total, String step) {
                System.out.println("\n  [步骤 " + current + "/" + total + " 开始] " + step);
            }

            @Override
            public void onPlanStepComplete(Plan plan, int current, int total, String step, String result) {
                System.out.println("  [步骤 " + current + "/" + total + " 完成] " + step);
            }

            @Override
            public void onPlanStepTool(Plan plan, ToolDescriptor tool, ToolStatus status) {
                switch (status) {
                    case PREPARING -> System.out.println("    [计划工具准备] " + tool.getName());
                    case CALLING -> System.out.println("    [计划工具调用中] " + tool.getName());
                    case COMPLETED -> System.out.println("    [计划工具完成] " + tool.getName());
                }
            }

            @Override
            public void onPlanStepError(Plan plan, int current, int total, String step, Exception error) {
                System.out.println("  [步骤 " + current + "/" + total + " 失败] " + step + " - " + error.getMessage());
            }

            @Override
            public void onSubAgent(AgentClient subAgent, String message) {
                System.out.println("\n[子Agent创建] " + subAgent.getName());
                System.out.println("  描述: " + subAgent.getDescription());
                System.out.println("  任务: " + message);
            }

            @Override
            public void onSubAgentStart(AgentClient subAgent, String task) {
                System.out.println("\n[子Agent执行开始] " + subAgent.getName());
            }

            @Override
            public void onSubAgentResult(AgentClient subAgent, String result) {
                System.out.println("\n[子Agent完成] " + subAgent.getName());
                System.out.println("  结果: " + result);
            }
        }).error(e -> {
            System.err.println("\n[错误] " + e.getMessage());
            e.printStackTrace();
        }).execute();

        System.out.println("=====================> 结束 <============================");
    }
}
