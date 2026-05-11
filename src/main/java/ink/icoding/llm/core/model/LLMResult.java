package ink.icoding.llm.core.model;

import java.util.concurrent.CompletableFuture;

/**
 * LLM调用结果对象, 支持链式回调.
 * <p>通过 {@link #then(ResultHandler)} 和 {@link #error(java.util.function.Consumer)}
 * 注册回调后, 调用 {@link #execute()} 启动异步SSE流式请求.</p>
 * <p>使用示例:</p>
 * <pre>{@code
 * model.ask(messages, tools)
 *     .then(message -> System.out.println(message))
 *     .error(e -> e.printStackTrace())
 *     .execute();
 * }</pre>
 *
 * @author gsk
 */
public class LLMResult {
    private ResultHandler handler;
    private java.util.function.Consumer<Exception> errorHandler;
    private final java.util.function.Consumer<LLMResult> executor;
    private final CompletableFuture<String> future = new CompletableFuture<>();

    /**
     * 构造结果对象.
     *
     * @param executor 执行器, 接收当前LLMResult实例以读取回调处理器
     */
    public LLMResult(java.util.function.Consumer<LLMResult> executor) {
        this.executor = executor;
    }

    /**
     * 注册结果回调处理器.
     *
     * @param handler 回调处理器
     * @return 当前实例, 支持链式调用
     */
    public LLMResult then(ResultHandler handler) {
        this.handler = handler;
        return this;
    }

    /**
     * 注册错误回调处理器.
     *
     * @param handler 错误处理器
     * @return 当前实例, 支持链式调用
     */
    public LLMResult error(java.util.function.Consumer<Exception> handler) {
        this.errorHandler = handler;
        return this;
    }

    /**
     * 启动执行, 触发异步SSE流式请求.
     */
    public void execute() {
        executor.accept(this);
    }

    /**
     * 阻塞等待执行完成并返回结果.
     *
     * @return LLM响应的完整文本
     * @throws Exception 如果执行失败
     */
    public String get() throws Exception {
        return future.get();
    }

    /**
     * 完成Future, 由模型实现调用.
     *
     * @param result 完成结果
     */
    public void complete(String result) {
        future.complete(result);
    }

    /**
     * 异常完成Future, 由模型实现调用.
     *
     * @param t 异常
     */
    public void completeExceptionally(Throwable t) {
        future.completeExceptionally(t);
    }

    /**
     * 获取结果回调处理器.
     *
     * @return 结果处理器
     */
    public ResultHandler getHandler() {
        return handler;
    }

    /**
     * 获取错误回调处理器.
     *
     * @return 错误处理器
     */
    public java.util.function.Consumer<Exception> getErrorHandler() {
        return errorHandler;
    }
}
