package ink.icoding.llm.core.tool.builtin;

import ink.icoding.llm.core.tool.Tool;
import ink.icoding.llm.core.tool.annotations.ToolInfo;
import ink.icoding.llm.core.tool.builtin.param.ViewFileParam;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.StringJoiner;

/**
 * 查看文件工具.
 * <p>读取文件内容, 小于1000行直接返回, 大于1000行返回前1000行和总行数.
 * 支持通过startLine和endLine查看指定范围的内容.</p>
 *
 * @author gsk
 */
@ToolInfo(name = "view_file", description = "查看文件内容. 小于1000行直接返回全部内容, 大于1000行返回前1000行并提示总行数. 支持传入startLine和endLine查看指定范围.")
public class ViewFileTool implements Tool<ViewFileParam> {

    @Override
    public String execute(ViewFileParam p) {
        Path path = Path.of(p.getPath());
        if (!Files.exists(path)) return "Error: file does not exist: " + p.getPath();
        if (!Files.isRegularFile(path)) return "Error: not a regular file: " + p.getPath();

        try {
            List<String> allLines = Files.readAllLines(path);
            int totalLines = allLines.size();
            int start = p.getStartLine() != null ? Math.max(1, p.getStartLine()) : 1;
            int end = p.getEndLine() != null ? Math.min(totalLines, p.getEndLine()) : totalLines;

            if (start > totalLines) return "Error: startLine " + start + " exceeds total lines " + totalLines;

            if (p.getStartLine() == null && p.getEndLine() == null && totalLines > 1000) {
                end = 1000;
            }

            StringJoiner joiner = new StringJoiner("\n");
            joiner.add("--- File: " + p.getPath() + " (total " + totalLines + " lines, showing " + start + "-" + end + ") ---");
            for (int i = start - 1; i < end; i++) {
                joiner.add(String.format("%4d | %s", i + 1, allLines.get(i)));
            }
            if (end < totalLines && p.getStartLine() == null && p.getEndLine() == null) {
                joiner.add("... (" + (totalLines - end) + " more lines, use startLine/endLine to view)");
            }
            return joiner.toString();
        } catch (IOException e) {
            return "Error reading file: " + e.getMessage();
        }
    }
}
