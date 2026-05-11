package ink.icoding.llm.core.tool.builtin;

import ink.icoding.llm.core.tool.Tool;
import ink.icoding.llm.core.tool.annotations.ToolInfo;
import ink.icoding.llm.core.tool.builtin.param.SearchInFileParam;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
 * 从文件中搜索内容工具.
 * <p>在指定文件中搜索多个关键字, 返回匹配行及其前后各5行上下文.</p>
 *
 * @author gsk
 */
@ToolInfo(name = "search_in_file", description = "在文件中搜索内容. 支持多个搜索关键字, 返回匹配行及其前后各5行上下文, 包含行号.")
public class SearchInFileTool implements Tool<SearchInFileParam> {

    @Override
    public String execute(SearchInFileParam p) {
        Path path = Path.of(p.getPath());
        if (!Files.exists(path)) return "Error: file does not exist: " + p.getPath();

        try {
            List<String> lines = Files.readAllLines(path);
            StringJoiner result = new StringJoiner("\n");
            result.add("--- Search in: " + p.getPath() + " ---");

            List<int[]> hits = new ArrayList<>();
            List<String> hitPatterns = new ArrayList<>();
            for (int i = 0; i < lines.size(); i++) {
                for (String pattern : p.getPatterns()) {
                    if (lines.get(i).contains(pattern)) {
                        hits.add(new int[]{i});
                        hitPatterns.add(pattern);
                    }
                }
            }

            if (hits.isEmpty()) {
                result.add("No matches found.");
                return result.toString();
            }

            result.add("Found " + hits.size() + " match(es):");
            for (int h = 0; h < hits.size(); h++) {
                int lineNum = hits.get(h)[0] + 1;
                result.add("");
                result.add(">>> Pattern: \"" + hitPatterns.get(h) + "\" at line " + lineNum);
                int contextStart = Math.max(0, lineNum - 6);
                int contextEnd = Math.min(lines.size(), lineNum + 5);
                for (int i = contextStart; i < contextEnd; i++) {
                    String marker = (i == lineNum - 1) ? " >> " : "    ";
                    result.add(marker + String.format("%4d | %s", i + 1, lines.get(i)));
                }
            }
            return result.toString();
        } catch (IOException e) {
            return "Error reading file: " + e.getMessage();
        }
    }
}
