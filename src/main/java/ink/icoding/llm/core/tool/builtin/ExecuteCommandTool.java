package ink.icoding.llm.core.tool.builtin;

import ink.icoding.llm.core.tool.Tool;
import ink.icoding.llm.core.tool.annotations.ToolInfo;
import ink.icoding.llm.core.tool.builtin.param.ExecuteCommandParam;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * 执行命令工具.
 * <p>自动识别当前操作系统, 在Windows上使用PowerShell, 在macOS/Linux上使用bash执行命令.
 * 命令最长执行60秒, 超时自动终止.</p>
 *
 * @author gsk
 */
@ToolInfo(name = "execute_command", description = "执行系统命令. 自动识别操作系统: Windows使用PowerShell, macOS/Linux使用bash. 命令最长执行60秒.")
public class ExecuteCommandTool implements Tool<ExecuteCommandParam> {

    @Override
    public String execute(ExecuteCommandParam p) {
        String command = p.getCommand();
        if (command == null || command.isBlank()) return "Error: command is empty";

        String os = System.getProperty("os.name").toLowerCase();
        ProcessBuilder builder;

        if (os.contains("win")) {
            builder = new ProcessBuilder("powershell.exe", "-NoProfile", "-Command", command);
        } else {
            builder = new ProcessBuilder("bash", "-c", command);
        }

        builder.redirectErrorStream(true);

        try {
            Process process = builder.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            boolean finished = process.waitFor(60, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return "Command timed out after 60 seconds.\n" + output;
            }

            int exitCode = process.exitValue();
            String result = output.toString().trim();
            if (exitCode != 0) {
                return "Exit code: " + exitCode + "\n" + result;
            }
            return result.isEmpty() ? "Command executed successfully (no output)." : result;
        } catch (IOException e) {
            return "Error executing command: " + e.getMessage();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Command interrupted: " + e.getMessage();
        }
    }
}
