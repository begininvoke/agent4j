package ink.icoding.llm.core.entity;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * 消息附件.
 * <p>表示消息中携带的文件附件, 支持图片、文档等二进制内容.
 * 提供多种静态工厂方法用于从不同来源创建附件.</p>
 *
 * @author gsk
 */
public class MessageAttachment {
    private byte[] data;
    private String contentType;
    private String filename;

    /** 无参构造器 */
    public MessageAttachment() {}

    /**
     * 全参构造器.
     *
     * @param data        附件数据
     * @param contentType MIME类型
     * @param filename    文件名
     */
    public MessageAttachment(byte[] data, String contentType, String filename) {
        this.data = data;
        this.contentType = contentType;
        this.filename = filename;
    }

    /**
     * 从内存多部分文件创建附件.
     *
     * @param file 内存文件
     * @return 附件对象
     */
    public static MessageAttachment fromMultipart(MemoryMultipartFile file) {
        return new MessageAttachment(file.getContent(), file.getContentType(), file.getName());
    }

    /**
     * 从磁盘文件创建附件.
     *
     * @param file 磁盘文件
     * @return 附件对象
     * @throws RuntimeException 如果读取文件失败
     */
    public static MessageAttachment fromFile(File file) {
        try {
            byte[] data = Files.readAllBytes(file.toPath());
            String contentType = Files.probeContentType(file.toPath());
            return new MessageAttachment(data, contentType, file.getName());
        } catch (IOException e) {
            throw new RuntimeException("Failed to read file: " + file.getAbsolutePath(), e);
        }
    }

    /**
     * 从字节数组创建附件.
     *
     * @param data        字节数组
     * @param contentType MIME类型
     * @return 附件对象
     */
    public static MessageAttachment fromBytes(byte[] data, String contentType) {
        return new MessageAttachment(data, contentType, "attachment");
    }

    public byte[] getData() { return data; }
    public void setData(byte[] data) { this.data = data; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }
}
