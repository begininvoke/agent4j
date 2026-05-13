package ink.icoding.llm.core.tool.builtin.skill;

import ink.icoding.llm.agent.Skill;
import ink.icoding.llm.core.tool.Tool;
import ink.icoding.llm.core.tool.builtin.ExecuteCommandTool;

import java.util.List;

/**
 * 命令执行技能.
 * <p>提供跨平台的系统命令执行能力, 自动识别操作系统并选择合适的Shell.</p>
 *
 * @author gsk
 */
public class CommandExecutionSkill extends Skill {

    public CommandExecutionSkill() {
        setTitle("命令执行");
        setDescription("提供跨平台的系统命令执行能力, 自动识别操作系统(Windows/macOS/Linux)并选择合适的Shell.");
        setTools(List.of(new ExecuteCommandTool()));
        setContent("""
                ## 命令执行指南

                ### 平台适配
                - Windows: 自动使用 PowerShell 执行命令
                - macOS / Linux: 自动使用 bash 执行命令

                ### 使用建议
                - 命令最长执行60秒, 超时会被自动终止
                - 优先使用跨平台兼容的命令
                - 查看文件系统信息时优先使用专用工具而非shell命令
                - 对于需要管理员权限的操作, 需要明确告知用户
                - 执行命令时, 建议先试用 cd 切换到目标目录, 确保命令在正确的上下文中执行.
                - 如果命令中需要用到路径, 且你知道全路径, 直接使用全路径执行命令可以避免环境变量问题.

                ### 常用命令参考
                - 查看当前目录: `pwd` (Unix) / `Get-Location` (PowerShell)
                - 查看环境变量: `env` (Unix) / `Get-ChildItem Env:` (PowerShell)
                - 查看进程: `ps aux` (Unix) / `Get-Process` (PowerShell)
                - 网络诊断: `ping`, `curl`, `wget`
                """);
    }
}
