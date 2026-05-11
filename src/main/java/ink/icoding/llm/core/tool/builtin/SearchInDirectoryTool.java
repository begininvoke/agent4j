package ink.icoding.llm.core.tool.builtin;

import ink.icoding.llm.core.tool.Tool;
import ink.icoding.llm.core.tool.annotations.ToolInfo;
import ink.icoding.llm.core.tool.builtin.param.SearchInDirectoryParam;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
 * 从目录中搜索文件内容工具.
 * <p>递归搜索目录中所有文件, 查找包含指定关键字的文件, 返回匹配内容及其上下文.</p>
 *
 * @author gsk
 */
@ToolInfo(name = "search_in_directory", description = "在目录中递归搜索文件内容. 支持多个搜索关键字, 返回匹配内容所在文件列表, 包含匹配行前后5行上下文和行号.")
public class SearchInDirectoryTool implements Tool<SearchInDirectoryParam> {

    @Override
    public String execute(SearchInDirectoryParam p) {
        Path dir = Path.of(p.getPath());
        if (!Files.exists(dir)) return "Error: path does not exist: " + p.getPath();
        if (!Files.isDirectory(dir)) return "Error: not a directory: " + p.getPath();

        List<FileMatch> allMatches = new ArrayList<>();
        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (Files.isRegularFile(file) && !isBinary(file)) {
                        try {
                            List<String> lines = Files.readAllLines(file);
                            for (int i = 0; i < lines.size(); i++) {
                                for (String pattern : p.getPatterns()) {
                                    if (lines.get(i).contains(pattern)) {
                                        allMatches.add(new FileMatch(file.toString(), i + 1, lines.get(i), pattern));
                                    }
                                }
                            }
                        } catch (IOException ignored) {}
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            return "Error walking directory: " + e.getMessage();
        }

        StringJoiner result = new StringJoiner("\n");
        result.add("--- Search in directory: " + p.getPath() + " ---");

        if (allMatches.isEmpty()) {
            result.add("No matches found.");
            return result.toString();
        }

        result.add("Found " + allMatches.size() + " match(es) in " +
                allMatches.stream().map(m -> m.filePath).distinct().count() + " file(s):");

        String currentFile = "";
        for (FileMatch match : allMatches) {
            if (!match.filePath.equals(currentFile)) {
                currentFile = match.filePath;
                result.add("");
                result.add("=== " + currentFile + " ===");
            }
            result.add("  Line " + match.lineNum + ": " + match.content);
        }
        return result.toString();
    }

    private boolean isBinary(Path file) {
        try {
            String name = file.getFileName().toString().toLowerCase();
            return name.endsWith(".class") || name.endsWith(".jar") || name.endsWith(".zip")
                    || name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".gif")
                    || name.endsWith(".pdf") || name.endsWith(".exe") || name.endsWith(".so")
                    || name.endsWith(".dylib") || name.endsWith(".dll");
        } catch (Exception e) {
            return true;
        }
    }

    private static class FileMatch {
        String filePath;
        int lineNum;
        String content;
        String pattern;

        FileMatch(String filePath, int lineNum, String content, String pattern) {
            this.filePath = filePath;
            this.lineNum = lineNum;
            this.content = content;
            this.pattern = pattern;
        }
    }
}
