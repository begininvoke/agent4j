package ink.icoding.llm.core.tool.builtin;

import ink.icoding.llm.core.tool.Tool;
import ink.icoding.llm.core.tool.annotations.ToolInfo;
import ink.icoding.llm.core.tool.builtin.param.MoveFileParam;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 移动文件工具.
 * <p>将文件从原路径移动到新路径, 自动创建目标路径的父目录.</p>
 *
 * @author gsk
 */
@ToolInfo(name = "move_file", description = "移动或重命名文件. 接收原文件路径和新文件路径, 自动创建目标目录.")
public class MoveFileTool implements Tool<MoveFileParam> {

    @Override
    public String execute(MoveFileParam p) {
        Path source = Path.of(p.getSource());
        Path target = Path.of(p.getTarget());
        if (!Files.exists(source)) return "Error: source file does not exist: " + p.getSource();

        try {
            Path parent = target.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            return "File moved successfully: " + p.getSource() + " -> " + p.getTarget();
        } catch (IOException e) {
            return "Error moving file: " + e.getMessage();
        }
    }
}
