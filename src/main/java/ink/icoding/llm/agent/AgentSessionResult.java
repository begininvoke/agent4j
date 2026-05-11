package ink.icoding.llm.agent;

import java.util.concurrent.CompletableFuture;

/**
 * 智能体会话结果对象, 支持链式回调.
 * <p>通过 {@link #then(AgentResultHandler)} 和 {@link #error(java.util.function.Consumer)}
 * 注册回调后, 调用 {@link #execute()} 启动异步执行.</p>
 * <p>使用示例:</p>
 * <pre>{@code
 * session.command("你好")
 *     .then(new AgentResultHandler() {
 *         public void onMessage(String msg) { System.out.print(msg); }
 *     })
 *     .error(e -> e.printStackTrace());
 * }</pre>
 *
 * @author gsk
 */
public class AgentSessionResult {
    private AgentResultHandler handler;
    private java.util.function.Consumer<Exception> errorHandler;
    private final java.util.function.Consumer<AgentSessionResult> executor;
    private final CompletableFuture<String> future = new CompletableFuture<>();

    /**
     * 构造结果对象.
     *
     * @param executor 执行器, 接收当前实例以读取回调处理器
     */
    public AgentSessionResult(java.util.function.Consumer<AgentSessionResult> executor) {
        this.executor = executor;
    }

    /**
     * 注册结果回调处理器.
     *
     * @param handler 智能体结果处理器
     * @return 当前实例, 支持链式调用
     */
    public AgentSessionResult then(AgentResultHandler handler) {
        this.handler = handler;
        return this;
    }

    /**
     * 注册错误回调处理器.
     * <p>注册后自动触发执行.</p>
     *
     * @param handler 错误处理器
     * @return 当前实例, 支持链式调用
     */
    public AgentSessionResult error(java.util.function.Consumer<Exception> handler) {
        this.errorHandler = handler;
        return this;
    }


    public void execute() {
        executor.accept(this);
    }

    /**
     * 阻塞等待执行完成并返回结果.
     *
     * @return 智能体响应的完整文本
     * @throws Exception 如果执行失败
     */
    public String get() throws Exception {
        return future.get();
    }

    /**
     * 完成Future, 由会话内部调用.
     *
     * @param result 完成结果
     */
    public void complete(String result) {
        future.complete(result);
    }

    /**
     * 异常完成Future, 由会话内部调用.
     *
     * @param t 异常
     */
    public void completeExceptionally(Throwable t) {
        future.completeExceptionally(t);
    }

    /** 获取结果回调处理器 */
    public AgentResultHandler getHandler() {
        return handler;
    }

    /** 获取错误回调处理器 */
    public java.util.function.Consumer<Exception> getErrorHandler() {
        return errorHandler;
    }
}
