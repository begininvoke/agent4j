package ink.icoding.llm.core.tool.builtin;

import ink.icoding.llm.core.tool.Tool;
import ink.icoding.llm.core.tool.annotations.ToolInfo;
import ink.icoding.llm.core.tool.builtin.param.CreateFileParam;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 创建文件工具.
 * <p>创建新文件并写入内容, 自动创建不存在的父目录.</p>
 *
 * @author gsk
 */
@ToolInfo(name = "create_file", description = "创建文件. 接收文件路径和内容, 自动创建不存在的父目录.")
public class CreateFileTool implements Tool<CreateFileParam> {

    @Override
    public String execute(CreateFileParam p) {
        Path path = Path.of(p.getPath());

        try {
            Path parent = path.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, p.getContent());
            return "File created successfully: " + p.getPath();
        } catch (IOException e) {
            return "Error creating file: " + e.getMessage();
        }
    }
}
