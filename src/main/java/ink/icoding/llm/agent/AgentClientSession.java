package ink.icoding.llm.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import ink.icoding.llm.core.entity.MemoryMultipartFile;
import ink.icoding.llm.core.entity.Message;
import ink.icoding.llm.core.model.ContextCompressionStatus;
import ink.icoding.llm.core.model.LLMResult;
import ink.icoding.llm.core.model.ResultHandler;
import ink.icoding.llm.core.model.TokenUsage;
import ink.icoding.llm.core.tool.Tool;
import ink.icoding.llm.core.tool.ToolDescriptor;
import ink.icoding.llm.core.tool.ToolExecutor;
import ink.icoding.llm.core.tool.ToolStatus;
import ink.icoding.llm.core.tool.builtin.CreatePlanTool;
import ink.icoding.llm.core.tool.builtin.CreateSubAgentTool;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 智能体客户端会话.
 * <p>管理与智能体的对话交互, 维护对话历史, 支持子Agent和计划管理.
 * 通过 {@link #command(String)} 发送指令, 支持流式回调.
 * 内置计划和子Agent执行能力, 通过 {@link AgentResultHandler} 报告执行进度.</p>
 * <p>使用示例:</p>
 * <pre>{@code
 * AgentClientSession session = agent.createSession();
 * session.command("帮我分析这段代码")
 *     .then(new AgentResultHandler() {
 *         public void onMessage(String msg) { System.out.print(msg); }
 *         public void onTool(ToolDescriptor tool, ToolStatus status) { ... }
 *         public void onPlanCreated(Plan plan) { ... }
 *         public void onPlanStepStart(Plan plan, int cur, int total, String step) { ... }
 *     })
 *     .error(e -> e.printStackTrace());
 * }</pre>
 *
 * @author gsk
 */
public class AgentClientSession {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final int DEFAULT_CONTEXT_SUMMARY_TRIGGER_TOKENS = 100_000;
    private static final int MEMORY_SUMMARY_MAX_TOKENS = 5_000;

    private final AgentClient agent;
    private final List<Message> history = new ArrayList<>();
    private final List<AgentClient> subAgents = new ArrayList<>();
    private final List<Plan> plans = new ArrayList<>();
    private final CreatePlanTool createPlanTool = new CreatePlanTool();
    private final CreateSubAgentTool createSubAgentTool = new CreateSubAgentTool();
    private String memorySummary;
    private int lastContextTokens;
    private int contextSummaryTriggerTokens = DEFAULT_CONTEXT_SUMMARY_TRIGGER_TOKENS;
    private int contextSummaryTriggerRounds;
    private Boolean thinkingEnabled;
    private Double temperature;

    /**
     * 构造会话实例.
     *
     * @param agent 所属的智能体客户端
     */
    public AgentClientSession(AgentClient agent) {
        this.agent = agent;
    }

    /**
     * 发送文本指令.
     *
     * @param message 指令内容
     * @return 结果对象, 支持链式回调
     */
    public AgentSessionResult command(String message) {
        return command(message, List.of());
    }

    /**
     * 发送文本指令, 可携带附件.
     *
     * @param message 指令内容
     * @param files   附件列表
     * @return 结果对象, 支持链式回调
     */
    public AgentSessionResult command(String message, List<MemoryMultipartFile> files) {
        Message userMsg = Message.fromUser(message);
        for (MemoryMultipartFile file : files) {
            userMsg.appendAttachment(file);
        }
        history.add(userMsg);

        return new AgentSessionResult(r -> executeCommand(r));
    }

    /**
     * 执行指令, 调用底层LLM并处理响应.
     * 保持完整历史, 只在上下文达到阈值后做一次性记忆总结.
     *
     * @param result 会话结果对象
     */
    private void executeCommand(AgentSessionResult result) {
        List<Message> messages = buildMessages(history);
        List<Tool> allTools = getAllTools();

        ToolExecutor toolExecutor = createToolExecutor(result);
        Message assistantMessage = Message.fromAssistant();
        try {
            LLMResult llmResult = askModel(messages, allTools, toolExecutor);

            // 先设handler, 再execute, 确保错误不会丢失
            llmResult.then(new ResultHandler() {
                @Override
                public void onMessage(String msg) {
                    assistantMessage.appendContent(msg);
                    if (result.getHandler() != null) {
                        result.getHandler().onMessage(msg);
                    }
                }

                @Override
                public void onThink(String think) {
                    assistantMessage.appendThink(think);
                    if (result.getHandler() != null) {
                        result.getHandler().onThink(think);
                    }
                }

                @Override
                public void onTool(ToolDescriptor tool, ToolStatus status) {
                    if (result.getHandler() != null) {
                        result.getHandler().onTool(tool, status);
                    }
                }

                @Override
                public void onToolError(ToolDescriptor tool, Exception error) {
                    if (result.getHandler() != null) {
                        result.getHandler().onToolError(tool, error);
                    } else {
                        logToolError(tool, error);
                    }
                }

                @Override
                public void onUsage(TokenUsage usage) {
                    result.addUsage(usage);
                    lastContextTokens = Math.max(lastContextTokens, usage.getInputTokens());
                    if (result.getHandler() != null) {
                        result.getHandler().onUsage(usage);
                    }
                }
            }).error(e -> {
                result.completeExceptionally(e);
                if (result.getErrorHandler() != null) {
                    result.getErrorHandler().accept(e);
                }
            });

            // handler已设置, 启动SSE请求
            llmResult.execute();

            // 阻塞等待LLM完成(包含Agent循环)
            String response = llmResult.get();
            List<Message> appendedMessages = llmResult.getAppendedMessages();
            if (!appendedMessages.isEmpty()) {
                history.addAll(appendedMessages);
            } else {
                if ((assistantMessage.getContent() == null || assistantMessage.getContent().isEmpty())
                        && response != null && !response.isEmpty()) {
                    assistantMessage.appendContent(response);
                }
                if ((assistantMessage.getContent() != null && !assistantMessage.getContent().isEmpty())
                        || (assistantMessage.getThink() != null && !assistantMessage.getThink().isEmpty())) {
                    history.add(assistantMessage);
                }
            }
            lastContextTokens = Math.max(lastContextTokens, llmResult.getMaxInputTokens());
            summarizeHistoryIfNeeded(result);
            result.complete(response);
        } catch (Exception e) {
            result.completeExceptionally(e);
            if (result.getErrorHandler() != null) {
                result.getErrorHandler().accept(e);
            }
        }
    }

    private LLMResult askModel(List<Message> messages, List<Tool> tools, ToolExecutor toolExecutor) {
        if (thinkingEnabled == null && temperature == null) {
            return agent.getModel().ask(messages, tools, toolExecutor);
        }
        Boolean requestThinkingEnabled = thinkingEnabled != null
                ? thinkingEnabled : agent.getModel().getThinkingEnabled();
        Double requestTemperature = temperature != null
                ? temperature : agent.getModel().getTemperature();
        return agent.getModel().ask(messages, tools, toolExecutor,
                requestThinkingEnabled, requestTemperature);
    }

    private LLMResult askModel(List<Message> messages, List<Tool> tools) {
        if (thinkingEnabled == null && temperature == null) {
            return agent.getModel().ask(messages, tools);
        }
        ToolExecutor noToolExecutor = (toolName, paramJson, descriptor, handler) -> {
            throw new IllegalStateException("No tools are available for this LLM call");
        };
        Boolean requestThinkingEnabled = thinkingEnabled != null
                ? thinkingEnabled : agent.getModel().getThinkingEnabled();
        Double requestTemperature = temperature != null
                ? temperature : agent.getModel().getTemperature();
        return agent.getModel().ask(messages, tools, noToolExecutor,
                requestThinkingEnabled, requestTemperature);
    }

    /**
     * 创建工具执行器, 拦截计划和子Agent工具的执行.
     *
     * @param result 会话结果对象, 用于报告进度
     * @return 工具执行器
     */
    private ToolExecutor createToolExecutor(AgentSessionResult result) {
        return (toolName, paramJson, descriptor, handler) -> {
            if ("create_plan".equals(toolName)) {
                return executePlanTool(paramJson, descriptor, handler, result);
            } else if ("create_sub_agent".equals(toolName)) {
                return executeSubAgentTool(paramJson, descriptor, handler, result);
            } else {
                Tool tool = findToolByName(toolName);
                if (tool == null) throw new RuntimeException("Tool not found: " + toolName);
                return ToolExecutor.defaultExecute(tool, paramJson, descriptor, handler);
            }
        };
    }

    /**
     * 执行计划工具: 创建计划并逐步执行.
     */
    private String executePlanTool(String paramJson, ToolDescriptor descriptor,
                                    ResultHandler handler, AgentSessionResult result) {
        // 调用CreatePlanTool存储计划
        String toolResult = ToolExecutor.defaultExecute(createPlanTool, paramJson, descriptor, handler);
        Plan plan = createPlanTool.getLastCreatedPlan();
        if (plan == null) return toolResult;

        plans.add(plan);
        AgentResultHandler agentHandler = result.getHandler();

        // 报告计划创建
        if (agentHandler != null) agentHandler.onPlanCreated(plan);
        if (agentHandler != null) agentHandler.onPlanExecuted(plan);

        List<String> steps = plan.getSteps();
        int total = steps.size();
        StringBuilder planResult = new StringBuilder();
        planResult.append("Plan '").append(plan.getName()).append("' execution started.\n");

        for (int i = 0; i < total; i++) {
            String step = steps.get(i);
            int current = i + 1;

            // 报告步骤开始
            if (agentHandler != null) agentHandler.onPlanStepStart(plan, current, total, step);

            try {
                // 每个步骤作为一次LLM调用
                Message stepMsg = Message.fromUser(
                        "Execute ONLY step " + current + "/" + total + ": \"" + step + "\"\n" +
                        "Do not work on any other steps. Once this step is complete, report what you did.");
                List<Message> stepMessages = new ArrayList<>();
                stepMessages.add(Message.fromUser(buildPlanSystemPrompt(plan, current, total)));
                stepMessages.addAll(history);
                stepMessages.add(stepMsg);

                List<Tool> allTools = getAllTools();
                ToolExecutor stepToolExecutor = (tn, pj, desc, h) -> {
                    Tool tool = findToolByName(tn);
                    if (tool == null) throw new RuntimeException("Tool not found: " + tn);
                    return ToolExecutor.defaultExecute(tool, pj, desc, h);
                };

                LLMResult stepResult = askModel(stepMessages, allTools, stepToolExecutor);
                stepResult.then(new ResultHandler() {
                    @Override
                    public void onMessage(String msg) {
                        if (agentHandler != null) agentHandler.onMessage(msg);
                    }

                    @Override
                    public void onThink(String think) {
                        if (agentHandler != null) agentHandler.onThink(think);
                    }

                    @Override
                    public void onTool(ToolDescriptor tool, ToolStatus status) {
                        if (agentHandler != null) agentHandler.onPlanStepTool(plan, tool, status);
                    }

                    @Override
                    public void onToolError(ToolDescriptor tool, Exception error) {
                        if (agentHandler != null) {
                            agentHandler.onToolError(tool, error);
                        } else {
                            logToolError(tool, error);
                        }
                    }

                    @Override
                    public void onUsage(TokenUsage usage) {
                        if (agentHandler != null) agentHandler.onUsage(usage);
                    }
                }).error(e -> {
                    if (agentHandler != null) agentHandler.onPlanStepError(plan, current, total, step, e);
                });
                stepResult.execute();

                String stepResponse = stepResult.get();
                planResult.append("Step ").append(current).append(": ").append(stepResponse).append("\n");

                // 报告步骤完成
                if (agentHandler != null) agentHandler.onPlanStepComplete(plan, current, total, step, stepResponse);
            } catch (Exception e) {
                if (agentHandler != null) agentHandler.onPlanStepError(plan, current, total, step, e);
                planResult.append("Step ").append(current).append(" failed: ").append(e.getMessage()).append("\n");
            }
        }

        planResult.append("Plan '").append(plan.getName()).append("' completed.");
        return planResult.toString();
    }

    /**
     * 构建计划步骤的系统提示.
     */
    private String buildPlanSystemPrompt(Plan plan, int current, int total) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are executing step ").append(current).append(" of ").append(total);
        sb.append(" from plan '").append(plan.getName()).append("'.\n\n");

        sb.append("## CURRENT TASK (step ").append(current).append(")\n");
        sb.append(plan.getSteps().get(current - 1)).append("\n\n");

        sb.append("## STRICT RULES\n");
        sb.append("- You MUST ONLY complete the current step described above.\n");
        sb.append("- Do NOT perform any work from other steps.\n");
        sb.append("- Do NOT create additional plans.\n");
        sb.append("- Do NOT skip ahead to future steps.\n");
        sb.append("- Once the current step is done, respond with a brief summary of what you accomplished.\n\n");

        sb.append("## PLAN OVERVIEW (for context only, do NOT execute these)\n");
        List<String> steps = plan.getSteps();
        for (int i = 0; i < steps.size(); i++) {
            String prefix = (i == current - 1) ? ">> " : "   ";
            sb.append(prefix).append(i + 1).append(". ").append(steps.get(i));
            if (i == current - 1) sb.append("  <-- YOU ARE HERE");
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * 执行子Agent工具: 创建子Agent并执行任务.
     */
    private String executeSubAgentTool(String paramJson, ToolDescriptor descriptor,
                                        ResultHandler handler, AgentSessionResult result) {
        // 调用CreateSubAgentTool存储子Agent信息
        String toolResult = ToolExecutor.defaultExecute(createSubAgentTool, paramJson, descriptor, handler);
        AgentClient subAgent = createSubAgentTool.getLastCreatedAgent();
        String task = createSubAgentTool.getLastTask();
        if (subAgent == null || task == null) return toolResult;

        // 子Agent继承父Agent的模型、工具和技能
        subAgent.setLlmRequestDebugEnabled(agent.isLlmRequestDebugEnabled());
        subAgent.setThinkingEnabled(agent.getThinkingEnabled());
        subAgent.setTemperature(agent.getTemperature());
        subAgent.setModel(agent.getModel());
        subAgent.getTools().addAll(agent.getTools());
        subAgent.getSkills().addAll(agent.getSkills());

        subAgents.add(subAgent);
        AgentResultHandler agentHandler = result.getHandler();

        // 报告子Agent创建和执行
        if (agentHandler != null) agentHandler.onSubAgent(subAgent, task);
        if (agentHandler != null) agentHandler.onSubAgentStart(subAgent, task);

        try {
            AgentClientSession subSession = subAgent.createSession();
            subSession.setThinkingEnabled(thinkingEnabled);
            subSession.setTemperature(temperature);
            AgentSessionResult subResult = subSession.command(task);
            subResult.then(new AgentResultHandler() {
                @Override
                public void onMessage(String msg) {
                    if (agentHandler != null) agentHandler.onMessage(msg);
                }

                @Override
                public void onThink(String think) {
                    if (agentHandler != null) agentHandler.onThink(think);
                }

                @Override
                public void onTool(ToolDescriptor tool, ToolStatus status) {
                    if (agentHandler != null) agentHandler.onTool(tool, status);
                }

                @Override
                public void onToolError(ToolDescriptor tool, Exception error) {
                    if (agentHandler != null) {
                        agentHandler.onToolError(tool, error);
                    } else {
                        logToolError(tool, error);
                    }
                }

                @Override
                public void onUsage(TokenUsage usage) {
                    if (agentHandler != null) agentHandler.onUsage(usage);
                }
            }).error(e -> {
                if (agentHandler != null) agentHandler.onSubAgentResult(subAgent, "Error: " + e.getMessage());
            }).execute();

            String subResponse = subResult.get();
            if (agentHandler != null) agentHandler.onSubAgentResult(subAgent, subResponse);
            return "Sub-agent '" + subAgent.getName() + "' completed.\nResult: " + subResponse;
        } catch (Exception e) {
            String errorResult = "Sub-agent '" + subAgent.getName() + "' failed: " + e.getMessage();
            if (agentHandler != null) agentHandler.onSubAgentResult(subAgent, errorResult);
            return errorResult;
        }
    }

    /**
     * 根据名称查找工具.
     */
    private Tool findToolByName(String name) {
        for (Tool tool : getAllTools()) {
            ToolDescriptor desc = ToolDescriptor.fromTool(tool);
            if (name.equals(desc.getName())) {
                return tool;
            }
        }
        return null;
    }

    private void logToolError(ToolDescriptor tool, Exception error) {
        String toolName = tool == null ? "unknown" : tool.getName();
        System.err.println("[Tool Error] " + toolName + ": " + error.getMessage());
        error.printStackTrace(System.err);
    }

    /**
     * 构建发送给LLM的消息列表, 包含系统提示和指定的对话消息.
     *
     * @param contextMessages 要包含的对话消息
     * @return 消息列表
     */
    private List<Message> buildMessages(List<Message> contextMessages) {
        List<Message> messages = new ArrayList<>();

        StringBuilder systemPrompt = new StringBuilder();
        systemPrompt.append("You are ").append(agent.getName());
        if (agent.getDescription() != null && !agent.getDescription().isEmpty()) {
            systemPrompt.append(", ").append(agent.getDescription());
        }
        systemPrompt.append(".\n\n");

        if (!agent.getSkills().isEmpty()) {
            systemPrompt.append("## Available Skills\n");
            for (Skill skill : agent.getSkills()) {
                systemPrompt.append("- **").append(skill.getTitle()).append("**: ").append(skill.getDescription()).append("\n");
                if (skill.getContent() != null) {
                    systemPrompt.append("  ").append(skill.getContent()).append("\n");
                }
            }
            systemPrompt.append("\n");
        }

        if (!agent.getTools().isEmpty()) {
            systemPrompt.append("## Available Tools\n");
            systemPrompt.append("You have access to the following tools. Use them when appropriate.\n\n");
        }

        // 注入系统环境信息. 放在更稳定的身份、技能和工具说明之后, 有利于前缀缓存命中.
        systemPrompt.append("## System Environment\n");
        systemPrompt.append(buildSystemContext());
        systemPrompt.append("\n");

        if (memorySummary != null && !memorySummary.isBlank()) {
            systemPrompt.append("## Session Memory (Compressed, Not Verbatim Dialogue)\n");
            systemPrompt.append("The following is a distilled memory of earlier conversation in this session. ");
            systemPrompt.append("It is not the original dialogue transcript. Treat it as background notes, ");
            systemPrompt.append("do not invent missing tool calls or exact wording from it, and use tools again when fresh verification is needed.\n");
            systemPrompt.append(memorySummary).append("\n\n");
        }

        Message systemMsg = Message.fromUser(systemPrompt.toString());
        messages.add(systemMsg);
        messages.addAll(contextMessages);

        return messages;
    }

    private void summarizeHistoryIfNeeded(AgentSessionResult result) {
        if (!shouldSummarizeHistory() || history.isEmpty()) {
            return;
        }
        int beforeTokens = lastContextTokens;
        AgentResultHandler handler = result.getHandler();
        if (handler != null) {
            handler.onContextCompression(ContextCompressionStatus.STARTED, beforeTokens, 0);
        }

        SummaryResult summaryResult = summarizeHistory();
        int afterTokens = summaryResult == null ? 0 : summaryResult.afterTokens();
        if (summaryResult == null || summaryResult.summary() == null || summaryResult.summary().isBlank()) {
            if (handler != null) {
                handler.onContextCompression(ContextCompressionStatus.COMPLETED, beforeTokens, afterTokens);
            }
            return;
        }
        memorySummary = summaryResult.summary().trim();
        history.clear();
        lastContextTokens = 0;
        if (handler != null) {
            handler.onContextCompression(ContextCompressionStatus.COMPLETED, beforeTokens, afterTokens);
        }
    }

    private boolean shouldSummarizeHistory() {
        boolean tokenLimitReached = contextSummaryTriggerTokens > 0
                && lastContextTokens >= contextSummaryTriggerTokens;
        boolean roundLimitReached = contextSummaryTriggerRounds > 0
                && countHistoryRounds() >= contextSummaryTriggerRounds;
        return tokenLimitReached || roundLimitReached;
    }

    private int countHistoryRounds() {
        int rounds = 0;
        for (Message msg : history) {
            if (msg.getRole() == Message.Role.user) {
                rounds++;
            }
        }
        return rounds;
    }

    private SummaryResult summarizeHistory() {
        List<Message> summaryMessages = new ArrayList<>();
        summaryMessages.add(Message.fromUser(buildSummarySystemPrompt()));
        if (memorySummary != null && !memorySummary.isBlank()) {
            summaryMessages.add(Message.fromUser("Previous compressed session memory:\n" + memorySummary));
        }
        summaryMessages.add(Message.fromUser("Full recent session transcript to distill follows. Preserve important facts, decisions, user preferences, unresolved tasks, and important tool findings. This transcript may include structured tool-call JSON; summarize the meaning, not the JSON syntax."));
        summaryMessages.addAll(history);
        summaryMessages.add(Message.fromUser("Create the new compressed session memory now. Keep it under about "
                + MEMORY_SUMMARY_MAX_TOKENS + " tokens."));

        try {
            LLMResult summaryResult = askModel(summaryMessages, List.of());
            summaryResult.execute();
            String summary = summaryResult.get();
            if ((summary == null || summary.isBlank()) && !summaryResult.getAppendedMessages().isEmpty()) {
                Message last = summaryResult.getAppendedMessages().get(summaryResult.getAppendedMessages().size() - 1);
                summary = last.getContent();
            }
            return new SummaryResult(summary, compressedTokens(summaryResult));
        } catch (Exception e) {
            return null;
        }
    }

    private int compressedTokens(LLMResult summaryResult) {
        TokenUsage usage = summaryResult.getLastUsage();
        if (usage == null) {
            return 0;
        }
        if (usage.getOutputTokens() > 0) {
            return usage.getOutputTokens();
        }
        return usage.getTotalTokens();
    }

    private record SummaryResult(String summary, int afterTokens) {}

    private String buildSummarySystemPrompt() {
        return """
                You are compressing an agent session into durable memory.

                Rules:
                - Output only the compressed memory, not analysis or commentary.
                - The memory must stay under about 5000 tokens.
                - Combine any previous memory with the current transcript into one updated memory.
                - Preserve durable user preferences, explicit requirements, project facts, decisions made, unresolved tasks, and important tool results.
                - Forget routine tool logs, repeated content, obsolete intermediate attempts, small talk, and details that are unlikely to matter later.
                - Mark uncertainty explicitly when a fact was inferred or only partially verified.
                - Do not claim this memory is an exact transcript.
                - Do not invent tool calls, file contents, command results, or user instructions that are not supported by the transcript.
                """;
    }

    /**
     * 构建系统环境上下文信息.
     * <p>自动检测当前操作系统、工作目录、Java版本等信息.</p>
     *
     * @return 系统环境信息字符串
     */
    private String buildSystemContext() {
        StringBuilder ctx = new StringBuilder();
        String os = System.getProperty("os.name", "Unknown");
        String osVersion = System.getProperty("os.version", "Unknown");
        String osArch = System.getProperty("os.arch", "Unknown");
        String javaVersion = System.getProperty("java.version", "Unknown");
        String workingDir = System.getProperty("user.dir", "Unknown");
        String userHome = System.getProperty("user.home", "Unknown");
        String userName = System.getProperty("user.name", "Unknown");
        String lineSep = System.getProperty("line.separator", "\n");
        String fileSep = System.getProperty("file.separator", "/");

        String shell;
        if (os.toLowerCase().contains("win")) {
            shell = "PowerShell";
        } else if (os.toLowerCase().contains("mac")) {
            shell = "bash (zsh)";
        } else {
            shell = "bash";
        }

        ctx.append("- Operating System: ").append(os).append(" ").append(osVersion).append(" (").append(osArch).append(")\n");
        ctx.append("- Shell: ").append(shell).append("\n");
        ctx.append("- Java Version: ").append(javaVersion).append("\n");
        ctx.append("- Working Directory: ").append(workingDir).append("\n");
        ctx.append("- User Home: ").append(userHome).append("\n");
        ctx.append("- User: ").append(userName).append("\n");
        ctx.append("- File Separator: \"").append(fileSep).append("\"\n");
        ctx.append("- Line Separator: ").append(lineSep.replace("\n", "\\n").replace("\r", "\\r")).append("\n");

        // 检测常用工具
        ctx.append("- Available Tools: ");
        java.util.List<String> available = new java.util.ArrayList<>();
        for (String tool : new String[]{"git", "java", "javac", "mvn", "gradle", "node", "npm", "python3", "python", "docker"}) {
            if (isCommandAvailable(tool)) {
                available.add(tool);
            }
        }
        ctx.append(available.isEmpty() ? "none detected" : String.join(", ", available)).append("\n");

        return ctx.toString();
    }

    /**
     * 检测命令是否可用.
     *
     * @param command 命令名称
     * @return 是否可用
     */
    private boolean isCommandAvailable(String command) {
        try {
            String[] cmd;
            if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
                cmd = new String[]{"powershell.exe", "-Command", "Get-Command " + command + " -ErrorAction SilentlyContinue"};
            } else {
                cmd = new String[]{"which", command};
            }
            Process process = Runtime.getRuntime().exec(cmd);
            boolean finished = process.waitFor(3, java.util.concurrent.TimeUnit.SECONDS);
            return finished && process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 获取所有可用工具, 包括Agent自身工具、技能中的工具和内置的计划/子Agent工具.
     *
     * @return 工具列表
     */
    private List<Tool> getAllTools() {
        if (!agent.isBuiltInAgentToolsEnabled() && agent.getTools().isEmpty() && agent.getSkills().isEmpty()) {
            return List.of();
        }
        List<Tool> allTools = new ArrayList<>(agent.getTools());
        // 添加session级别的计划/子Agent工具(使用session持有的实例)
        if (agent.isBuiltInAgentToolsEnabled()) {
            allTools.add(createPlanTool);
            allTools.add(createSubAgentTool);
        }
        // 添加技能中的工具, 跳过已存在的同名工具
        java.util.Set<String> names = new java.util.HashSet<>();
        for (Tool<?> t : allTools) {
            names.add(ink.icoding.llm.core.tool.ToolDescriptor.fromTool(t).getName());
        }
        for (Skill skill : agent.getSkills()) {
            for (Tool<?> t : skill.getTools()) {
                String name = ink.icoding.llm.core.tool.ToolDescriptor.fromTool(t).getName();
                if (names.add(name)) {
                    allTools.add(t);
                }
            }
        }
        return allTools;
    }

    /**
     * 将当前会话序列化为JSON字符串.
     *
     * @return JSON字符串
     * @throws RuntimeException 如果序列化失败
     */
    public String serialization() {
        try {
            SerializationData data = new SerializationData();
            data.setHistory(new ArrayList<>(history));
            data.setMemorySummary(memorySummary);
            data.setLastContextTokens(lastContextTokens);
            data.setContextSummaryTriggerTokens(contextSummaryTriggerTokens);
            data.setContextSummaryTriggerRounds(contextSummaryTriggerRounds);
            data.setThinkingEnabled(thinkingEnabled);
            data.setTemperature(temperature);
            data.setSubAgents(subAgents.stream()
                    .map(AgentClient::getName)
                    .collect(Collectors.toList()));
            data.setPlans(new ArrayList<>(plans));
            return MAPPER.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize session", e);
        }
    }

    /**
     * 从JSON字符串反序列化会话.
     *
     * @param json  序列化JSON字符串
     * @param agent 所属的智能体客户端
     * @return 恢复的会话实例
     * @throws RuntimeException 如果反序列化失败
     */
    public static AgentClientSession fromSerialization(String json, AgentClient agent) {
        try {
            SerializationData data = MAPPER.readValue(json, SerializationData.class);
            AgentClientSession session = new AgentClientSession(agent);
            session.getHistory().addAll(data.getHistory());
            session.getPlans().addAll(data.getPlans());
            session.memorySummary = data.getMemorySummary();
            session.lastContextTokens = data.getLastContextTokens();
            session.contextSummaryTriggerTokens = data.getContextSummaryTriggerTokens();
            session.contextSummaryTriggerRounds = data.getContextSummaryTriggerRounds();
            session.thinkingEnabled = data.getThinkingEnabled();
            session.temperature = data.getTemperature();
            return session;
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize session", e);
        }
    }

    /** 获取对话历史 */
    public List<Message> getHistory() { return history; }

    /** 获取子Agent列表 */
    public List<AgentClient> getSubAgents() { return subAgents; }

    /** 获取计划列表 */
    public List<Plan> getPlans() { return plans; }

    /** 获取压缩后的会话记忆 */
    public String getMemorySummary() { return memorySummary; }

    /** 获取最近记录到的上下文Token量 */
    public int getLastContextTokens() { return lastContextTokens; }

    /** 获取触发上下文压缩的Token阈值; 小于等于0表示关闭Token限制 */
    public int getContextSummaryTriggerTokens() { return contextSummaryTriggerTokens; }

    /** 设置触发上下文压缩的Token阈值; 小于等于0表示关闭Token限制 */
    public AgentClientSession setContextSummaryTriggerTokens(int contextSummaryTriggerTokens) {
        this.contextSummaryTriggerTokens = contextSummaryTriggerTokens;
        return this;
    }

    /** 获取触发上下文压缩的对话轮数阈值; 小于等于0表示关闭轮数限制 */
    public int getContextSummaryTriggerRounds() { return contextSummaryTriggerRounds; }

    /** 设置触发上下文压缩的对话轮数阈值; 小于等于0表示关闭轮数限制 */
    public AgentClientSession setContextSummaryTriggerRounds(int contextSummaryTriggerRounds) {
        this.contextSummaryTriggerRounds = contextSummaryTriggerRounds;
        return this;
    }

    /** 获取当前Session级思考开关; null表示使用Agent/底层模型默认设置 */
    public Boolean getThinkingEnabled() { return thinkingEnabled; }

    /** 设置当前Session级思考开关; null表示使用Agent/底层模型默认设置 */
    public AgentClientSession setThinkingEnabled(Boolean thinkingEnabled) {
        this.thinkingEnabled = thinkingEnabled;
        return this;
    }

    /** 仅对当前Session开启思考 */
    public AgentClientSession enableThinking() { return setThinkingEnabled(true); }

    /** 仅对当前Session关闭思考 */
    public AgentClientSession disableThinking() { return setThinkingEnabled(false); }

    /** 获取当前Session级温度; null表示使用Agent/底层模型默认设置 */
    public Double getTemperature() { return temperature; }

    /** 设置当前Session级温度; null表示使用Agent/底层模型默认设置 */
    public AgentClientSession setTemperature(Double temperature) {
        this.temperature = temperature;
        return this;
    }

    /**
     * 序列化数据内部类.
     */
    private static class SerializationData {
        private List<Message> history = new ArrayList<>();
        private List<String> subAgents = new ArrayList<>();
        private List<Plan> plans = new ArrayList<>();
        private String memorySummary;
        private int lastContextTokens;
        private int contextSummaryTriggerTokens = DEFAULT_CONTEXT_SUMMARY_TRIGGER_TOKENS;
        private int contextSummaryTriggerRounds;
        private Boolean thinkingEnabled;
        private Double temperature;

        public List<Message> getHistory() { return history; }
        public void setHistory(List<Message> history) { this.history = history; }
        public List<String> getSubAgents() { return subAgents; }
        public void setSubAgents(List<String> subAgents) { this.subAgents = subAgents; }
        public List<Plan> getPlans() { return plans; }
        public void setPlans(List<Plan> plans) { this.plans = plans; }
        public String getMemorySummary() { return memorySummary; }
        public void setMemorySummary(String memorySummary) { this.memorySummary = memorySummary; }
        public int getLastContextTokens() { return lastContextTokens; }
        public void setLastContextTokens(int lastContextTokens) { this.lastContextTokens = lastContextTokens; }
        public int getContextSummaryTriggerTokens() { return contextSummaryTriggerTokens; }
        public void setContextSummaryTriggerTokens(int contextSummaryTriggerTokens) { this.contextSummaryTriggerTokens = contextSummaryTriggerTokens; }
        public int getContextSummaryTriggerRounds() { return contextSummaryTriggerRounds; }
        public void setContextSummaryTriggerRounds(int contextSummaryTriggerRounds) { this.contextSummaryTriggerRounds = contextSummaryTriggerRounds; }
        public Boolean getThinkingEnabled() { return thinkingEnabled; }
        public void setThinkingEnabled(Boolean thinkingEnabled) { this.thinkingEnabled = thinkingEnabled; }
        public Double getTemperature() { return temperature; }
        public void setTemperature(Double temperature) { this.temperature = temperature; }
    }
}
