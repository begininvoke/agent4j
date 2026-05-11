package ink.icoding.llm.core.tool.builtin;

import ink.icoding.llm.core.tool.Tool;
import ink.icoding.llm.core.tool.annotations.ToolInfo;
import ink.icoding.llm.core.tool.builtin.param.DeleteFileParam;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 删除文件工具.
 * <p>删除指定路径的文件.</p>
 *
 * @author gsk
 */
@ToolInfo(name = "delete_file", description = "删除文件. 接收文件路径, 删除指定文件.")
public class DeleteFileTool implements Tool<DeleteFileParam> {

    @Override
    public String execute(DeleteFileParam p) {
        Path path = Path.of(p.getPath());
        if (!Files.exists(path)) return "Error: file does not exist: " + p.getPath();

        try {
            Files.delete(path);
            return "File deleted successfully: " + p.getPath();
        } catch (IOException e) {
            return "Error deleting file: " + e.getMessage();
        }
    }
}
