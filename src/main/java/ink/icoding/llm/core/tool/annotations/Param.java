package ink.icoding.llm.core.tool.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 工具参数注解.
 * <p>标注在 {@link ink.icoding.llm.core.tool.ToolParam} 子类的字段上,
 * 声明参数的元信息供LLM生成正确的调用参数.</p>
 * <p>使用示例:</p>
 * <pre>{@code
 * public class SearchParam extends ToolParam {
 *     @Param(required = true, description = "搜索关键词")
 *     private String query;
 *
 *     @Param(required = false, description = "返回结果数量", enums = {"5", "10", "20"})
 *     private String limit;
 * }
 * }</pre>
 *
 * @author gsk
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Param {

    /**
     * 是否必填, 默认为true.
     */
    boolean required() default true;

    /**
     * 参数描述.
     */
    String description() default "";

    /**
     * 可选值列表, 用于枚举类型参数.
     */
    String[] enums() default {};
}
