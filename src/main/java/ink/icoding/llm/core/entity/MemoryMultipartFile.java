package ink.icoding.llm.core.entity;

/**
 * 内存中的多部分文件表示.
 * <p>用于在不依赖Spring框架的情况下表示文件数据,
 * 包含文件内容、MIME类型和文件名.</p>
 *
 * @author gsk
 */
public class MemoryMultipartFile {
    private byte[] content;
    private String contentType;
    private String name;

    /** 无参构造器 */
    public MemoryMultipartFile() {}

    /**
     * 全参构造器.
     *
     * @param content     文件内容字节数组
     * @param contentType MIME类型, 如 image/png
     * @param name        文件名
     */
    public MemoryMultipartFile(byte[] content, String contentType, String name) {
        this.content = content;
        this.contentType = contentType;
        this.name = name;
    }

    public byte[] getContent() { return content; }
    public void setContent(byte[] content) { this.content = content; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
