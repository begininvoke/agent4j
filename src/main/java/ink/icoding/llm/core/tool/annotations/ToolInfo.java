package ink.icoding.llm.core.tool.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 工具信息注解.
 * <p>标注在工具实现类上, 声明工具的名称和描述, 供LLM识别和调用.</p>
 * <p>使用示例:</p>
 * <pre>{@code
 * @ToolInfo(name = "search", description = "搜索互联网内容")
 * public class SearchTool implements Tool<SearchParam> { ... }
 * }</pre>
 *
 * @author gsk
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ToolInfo {

    /**
     * 工具名称, 用于LLM识别.
     */
    String name();

    /**
     * 工具描述, 帮助LLM理解工具用途.
     */
    String description();
}
