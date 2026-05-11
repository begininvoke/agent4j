package ink.icoding.llm.core.tool.builtin;

import ink.icoding.llm.core.tool.Tool;
import ink.icoding.llm.core.tool.annotations.ToolInfo;
import ink.icoding.llm.core.tool.builtin.param.SearchFilesParam;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
 * 从目录中搜索文件工具.
 * <p>递归搜索目录中匹配关键字的文件, 返回文件树结构.</p>
 *
 * @author gsk
 */
@ToolInfo(name = "search_files", description = "在目录中按文件名关键字搜索文件. 支持多个关键字, 返回匹配的文件列表.")
public class SearchFilesTool implements Tool<SearchFilesParam> {

    @Override
    public String execute(SearchFilesParam p) {
        Path dir = Path.of(p.getPath());
        if (!Files.exists(dir)) return "Error: path does not exist: " + p.getPath();
        if (!Files.isDirectory(dir)) return "Error: not a directory: " + p.getPath();

        List<String> matched = new ArrayList<>();
        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    String fileName = file.getFileName().toString();
                    for (String keyword : p.getKeywords()) {
                        if (fileName.contains(keyword)) {
                            matched.add(file.toString());
                            break;
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            return "Error walking directory: " + e.getMessage();
        }

        StringJoiner result = new StringJoiner("\n");
        if (matched.isEmpty()) {
            result.add("--- Search files in: " + p.getPath() + " ---");
            result.add("No files matched the given keywords.");
        } else {
            result.add("--- Search files in: " + p.getPath() + " (found " + matched.size() + " file(s)) ---");
            matched.forEach(result::add);
        }
        return result.toString();
    }
}
