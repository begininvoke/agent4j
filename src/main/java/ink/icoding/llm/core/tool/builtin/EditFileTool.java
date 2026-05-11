package ink.icoding.llm.core.tool.builtin;

import ink.icoding.llm.core.tool.Tool;
import ink.icoding.llm.core.tool.annotations.ToolInfo;
import ink.icoding.llm.core.tool.builtin.param.EditFileParam;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 编辑文件工具.
 * <p>支持三种操作模式:</p>
 * <ul>
 *   <li><b>replace</b> - 替换指定行范围的内容 (默认模式)</li>
 *   <li><b>insert</b> - 在指定行前插入内容</li>
 *   <li><b>append</b> - 在文件末尾追加内容</li>
 * </ul>
 *
 * @author gsk
 */
@ToolInfo(name = "edit_file", description = "编辑文件内容. 支持三种模式: replace(替换指定行范围,默认), insert(在指定行前插入), append(在文件末尾追加). replace模式需要startLine和endLine; insert模式需要startLine; append模式不需要行号.")
public class EditFileTool implements Tool<EditFileParam> {

    @Override
    public String execute(EditFileParam p) {
        Path path = Path.of(p.getPath());
        if (!Files.exists(path)) return "Error: file does not exist: " + p.getPath();
        if (!Files.isRegularFile(path)) return "Error: not a regular file: " + p.getPath();

        String mode = p.getMode() != null ? p.getMode().toLowerCase() : "replace";

        try {
            return switch (mode) {
                case "replace" -> doReplace(path, p);
                case "insert" -> doInsert(path, p);
                case "append" -> doAppend(path, p);
                default -> "Error: unknown mode '" + mode + "', use replace/insert/append";
            };
        } catch (IOException e) {
            return "Error editing file: " + e.getMessage();
        }
    }

    private String doReplace(Path path, EditFileParam p) throws IOException {
        if (p.getStartLine() == null || p.getEndLine() == null) {
            return "Error: replace mode requires startLine and endLine";
        }

        List<String> lines = new ArrayList<>(Files.readAllLines(path));
        int totalLines = lines.size();
        int start = p.getStartLine();
        int end = p.getEndLine();

        if (start < 1 || start > totalLines) return "Error: startLine " + start + " out of range [1, " + totalLines + "]";
        if (end < start || end > totalLines) return "Error: endLine " + end + " out of range [" + start + ", " + totalLines + "]";

        List<String> newLines = new ArrayList<>(lines.subList(0, start - 1));
        if (p.getContent() != null && !p.getContent().isEmpty()) {
            newLines.addAll(List.of(p.getContent().split("\n", -1)));
        }
        newLines.addAll(lines.subList(end, totalLines));

        Files.write(path, newLines);
        return "File edited (replace). Lines " + start + "-" + end + " replaced. New total: " + newLines.size() + " lines.";
    }

    private String doInsert(Path path, EditFileParam p) throws IOException {
        if (p.getStartLine() == null) {
            return "Error: insert mode requires startLine";
        }

        List<String> lines = new ArrayList<>(Files.readAllLines(path));
        int totalLines = lines.size();
        int start = p.getStartLine();

        if (start < 1 || start > totalLines + 1) return "Error: startLine " + start + " out of range [1, " + (totalLines + 1) + "]";

        List<String> insertLines = p.getContent() != null ? List.of(p.getContent().split("\n", -1)) : List.of();
        List<String> newLines = new ArrayList<>(lines.subList(0, start - 1));
        newLines.addAll(insertLines);
        newLines.addAll(lines.subList(start - 1, totalLines));

        Files.write(path, newLines);
        return "File edited (insert). " + insertLines.size() + " line(s) inserted at line " + start + ". New total: " + newLines.size() + " lines.";
    }

    private String doAppend(Path path, EditFileParam p) throws IOException {
        List<String> lines = new ArrayList<>(Files.readAllLines(path));
        List<String> appendLines = p.getContent() != null ? List.of(p.getContent().split("\n", -1)) : List.of();
        lines.addAll(appendLines);

        Files.write(path, lines);
        return "File edited (append). " + appendLines.size() + " line(s) appended. New total: " + lines.size() + " lines.";
    }
}
