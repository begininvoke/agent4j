package ink.icoding.llm.core.tool.builtin.skill;

import ink.icoding.llm.agent.Skill;
import ink.icoding.llm.core.tool.Tool;
import ink.icoding.llm.core.tool.builtin.*;

import java.util.List;

/**
 * 文件系统操作技能.
 * <p>提供完整的文件系统操作能力, 包括查看目录树、查看文件、搜索文件内容、
 * 搜索文件、编辑文件、创建文件、删除文件和移动文件.</p>
 *
 * @author gsk
 */
public class FileSystemSkill extends Skill {

    public FileSystemSkill() {
        setTitle("文件系统操作");
        setDescription("提供完整的文件系统操作能力, 包括浏览目录、查看和编辑文件、搜索文件和内容.");
        setTools(List.of(
                new ListDirectoryTreeTool(),
                new ViewFileTool(),
                new SearchInFileTool(),
                new SearchInDirectoryTool(),
                new SearchFilesTool(),
                new EditFileTool(),
                new CreateFileTool(),
                new DeleteFileTool(),
                new MoveFileTool()
        ));
        setContent("""
                ## 文件系统操作指南

                ### 浏览文件
                - 使用 `list_directory_tree` 查看目录结构, 通过 maxDepth 控制层级深度
                - 使用 `view_file` 查看文件内容, 大文件会自动截断并提示总行数
                - 使用 `search_files` 按文件名关键字在目录中搜索文件

                ### 搜索内容
                - 使用 `search_in_file` 在单个文件中搜索关键字, 返回匹配行及前后5行上下文
                - 使用 `search_in_directory` 递归搜索目录中所有文件的内容

                ### 编辑文件
                - 使用 `edit_file` 替换文件中指定行范围的内容 (行号从1开始)
                - 使用 `create_file` 创建新文件, 自动创建父目录
                - 使用 `delete_file` 删除文件
                - 使用 `move_file` 移动或重命名文件

                ### 最佳实践
                - 编辑文件前先用 `view_file` 查看当前内容
                - 搜索时可以传入多个关键字以提高效率
                - 修改重要文件前建议先备份
                """);
    }
}
