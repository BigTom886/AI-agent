package com.wc.chatmemory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.objenesis.strategy.StdInstantiatorStrategy;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

/**
 * 基于文件持久化的对话记忆
 */
public class FileBasedChatMemory implements ChatMemory
{

    private final String BASE_DIR;
    private static final Kryo kryo = new Kryo();

    static
    {

        // Kryo5 默认：registrationRequired=true，要求所有要序列化的类必须提前手动注册，否则抛异常。
        // 设置为false：关闭强制注册模式
        // 序列化时会把类的全限定类名（com.xxx.User）写入二进制字节；
        // ✅ 好处：不用提前一个个kryo.register(Class)，POJO 直接序列化，写代码省事；
        // ❌ 坏处：字节体积变大；反序列化安全风险升高，不可信输入会 RCE。
        kryo.setRegistrationRequired(false);
        // 设置实例化策略
        // 使用 JDK 内部反射，不需要无参构造函数
        kryo.setInstantiatorStrategy(new StdInstantiatorStrategy());
    }

    // 构造对象时，指定文件保存目录
    public FileBasedChatMemory(String dir)
    {
        this.BASE_DIR = dir;
        File baseDir = new File(dir);
        if (!baseDir.exists())
        {
            baseDir.mkdirs();
        }
    }

    @Override
    public void add(String conversationId, List<Message> messages)
    {
        List<Message> conversationMessages = getOrCreateConversation(conversationId);
        conversationMessages.addAll(messages);
        saveConversation(conversationId, conversationMessages);
    }

    @Override
    public List<Message> get(String conversationId)
    {
        // ✅ 修复：直接返回全部消息；窗口截断交给外层 MessageWindowChatMemory 处理
        return getOrCreateConversation(conversationId);
    }

    @Override
    public void clear(String conversationId)
    {
        File file = getConversationFile(conversationId);
        if (file.exists())
        {
            file.delete();
        }
    }

    @SuppressWarnings("unchecked")
    private List<Message> getOrCreateConversation(String conversationId)
    {
        File file = getConversationFile(conversationId);
        List<Message> messages = new ArrayList<>();
        if (file.exists())
        {
            try (Input input = new Input(new FileInputStream(file)))
            {
                // ✅ 反序列化：用 ArrayList.class 即可
                // 因为 setRegistrationRequired(false)，Kryo 写入时每个元素的具体类名（UserMessage/AssistantMessage）
                // 都已写入字节流；反序列化时按写入的具体类名还原，不依赖静态泛型类型
                messages = (List<Message>) kryo.readObject(input, ArrayList.class);
            } catch (IOException e)
            {
                e.printStackTrace();
            }
        }
        return messages;
    }

    private void saveConversation(String conversationId, List<Message> messages)
    {
        File file = getConversationFile(conversationId);
        try (Output output = new Output(new FileOutputStream(file)))
        {
            kryo.writeObject(output, messages);
        } catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    private File getConversationFile(String conversationId)
    {
        return new File(BASE_DIR, conversationId + ".kryo");
    }

}
