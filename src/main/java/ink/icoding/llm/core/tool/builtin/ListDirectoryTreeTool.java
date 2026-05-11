package ink.icoding.llm.core.tool.builtin;

import ink.icoding.llm.core.tool.Tool;
import ink.icoding.llm.core.tool.annotations.ToolInfo;
import ink.icoding.llm.core.tool.builtin.param.ListDirectoryTreeParam;

import java.io.File;
import java.util.StringJoiner;

/**
 * 查看目录文件树工具.
 * <p>列出指定目录的文件和文件夹结构, 支持控制最大层级深度.</p>
 *
 * @author gsk
 */
@ToolInfo(name = "list_directory_tree", description = "查看目录中的文件树结构, 返回目录下所有文件和文件夹的层级列表. 可选参数maxDepth控制最大层级深度, 默认5.")
public class ListDirectoryTreeTool implements Tool<ListDirectoryTreeParam> {

    @Override
    public String execute(ListDirectoryTreeParam p) {
        File dir = new File(p.getPath());
        if (!dir.exists()) return "Error: path does not exist: " + p.getPath();
        if (!dir.isDirectory()) return "Error: path is not a directory: " + p.getPath();

        int maxDepth = p.getMaxDepth() != null ? p.getMaxDepth() : 5;
        StringJoiner joiner = new StringJoiner("\n");
        buildTree(dir, "", 0, maxDepth, joiner);
        return joiner.toString();
    }

    private void buildTree(File dir, String prefix, int depth, int maxDepth, StringJoiner joiner) {
        if (depth >= maxDepth) {
            joiner.add(prefix + "...");
            return;
        }
        File[] files = dir.listFiles();
        if (files == null) return;

        java.util.Arrays.sort(files, (a, b) -> {
            if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
            return a.getName().compareTo(b.getName());
        });

        for (int i = 0; i < files.length; i++) {
            boolean isLast = i == files.length - 1;
            String connector = isLast ? "└── " : "├── ";
            String childPrefix = isLast ? "    " : "│   ";

            joiner.add(prefix + connector + files[i].getName() + (files[i].isDirectory() ? "/" : ""));
            if (files[i].isDirectory()) {
                buildTree(files[i], prefix + childPrefix, depth + 1, maxDepth, joiner);
            }
        }
    }
}
