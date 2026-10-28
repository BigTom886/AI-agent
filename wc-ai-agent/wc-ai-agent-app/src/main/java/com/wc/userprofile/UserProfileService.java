package com.wc.userprofile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.wc.mapper.UserProfileMapper;

/**
 * 用户画像的业务逻辑
 *
 * 画像正文用「一行一条」的纯文本存，格式固定为：
 *   - [昵称] 韩跑跑
 *   - [偏好] 说话别太热情，理性一点
 *
 * 为什么不建成多张表（一行一条记录）？
 * 画像天生是「一整份文档」：要整体读出来塞进提示词、要整体展示给用户看。
 * 一行一条的纯文本既能当数据库字段存，又能原样拼进提示词，还方便人工排查。
 * 等真要按类别做检索/统计了，再拆表也不迟。
 *
 * 类别（category）是画像的「键」：同一个类别再次记录就是覆盖，
 * 「忘掉偏好」就是按类别删掉整条。
 */
@Service
public class UserProfileService
{
    /** 一条画像的前缀 */
    private static final String ITEM_PREFIX = "- [";

    /** 类别和内容之间的分隔符 */
    private static final String ITEM_SEPARATOR = "] ";

    /** 最多保存多少条，超出就丢最旧的（防止画像无限膨胀吃掉提示词） */
    private final int maxItems;

    private final UserProfileMapper mapper;

    public UserProfileService(UserProfileMapper mapper,
            @Value("${love.profile.max-items:30}") int maxItems)
    {
        this.mapper = mapper;
        this.maxItems = maxItems;
    }

    /**
     * 读取画像条目
     *
     * @param userId 用户 ID
     * @return 画像条目列表（每条形如 "- [昵称] 韩跑跑"）；没有画像时返回空列表
     */
    public List<String> items(String userId)
    {
        // user_id 是主键，直接按主键查
        UserProfile profile = mapper.selectById(userId);
        if (profile == null)
        {
            return List.of();
        }
        return splitItems(profile.getContent());
    }

    /**
     * 读取画像正文（直接拼进提示词用的那一份）
     *
     * @param userId 用户 ID
     * @return 画像正文；没有画像时返回空字符串
     */
    public String profileText(String userId)
    {
        return String.join("\n", items(userId));
    }

    /**
     * 记一条画像：同类别覆盖，不同类别追加
     *
     * @param userId   用户 ID
     * @param category 类别，例如「昵称」「偏好」
     * @param value    内容
     */
    public void remember(String userId, String category, String value)
    {
        String cat = category == null ? "" : category.trim();
        String val = value == null ? "" : value.trim();
        if (cat.isEmpty() || val.isEmpty())
        {
            throw new IllegalArgumentException("画像的类别和内容都不能为空");
        }

        List<String> items = new ArrayList<>(items(userId));
        // 同类别先删掉旧值：用户改口了（比如先说"讨厌猫"后说"其实喜欢猫"）以新的为准
        items.removeIf(item -> categoryOf(item).equalsIgnoreCase(cat));
        items.add(ITEM_PREFIX + cat + ITEM_SEPARATOR + val);

        // 超出上限就丢最旧的
        while (items.size() > maxItems)
        {
            items.remove(0);
        }

        save(userId, String.join("\n", items));
    }

    /**
     * 忘掉某一类画像
     *
     * @param userId   用户 ID
     * @param category 类别
     * @return true 表示确实删掉了东西；false 表示本来就没有这一类
     */
    public boolean forget(String userId, String category)
    {
        String cat = category == null ? "" : category.trim();
        if (cat.isEmpty())
        {
            throw new IllegalArgumentException("要忘掉的类别不能为空");
        }

        List<String> items = new ArrayList<>(items(userId));
        boolean removed = items.removeIf(item -> categoryOf(item).equalsIgnoreCase(cat));
        if (removed)
        {
            // 删空了就把整行记录也删掉，库里不留空画像
            if (items.isEmpty())
            {
                mapper.deleteById(userId);
            }
            else
            {
                save(userId, String.join("\n", items));
            }
        }
        return removed;
    }

    /**
     * 清空某个用户的全部画像
     *
     * @param userId 用户 ID
     */
    public void clear(String userId)
    {
        mapper.deleteById(userId);
    }

    /**
     * 整份覆盖写（upsert）
     *
     * insertOrUpdate 会先按主键查：不存在就 insert，存在就 updateById。
     * 这里必须把 updated_at 显式写进去 —— updateById 会忽略 null 字段，
     * 否则更新时间永远停留在第一次插入的时刻。
     */
    private void save(String userId, String content)
    {
        mapper.insertOrUpdate(new UserProfile(userId, content, LocalDateTime.now()));
    }

    /** 拆成一行一条 */
    private static List<String> splitItems(String content)
    {
        List<String> items = new ArrayList<>();
        for (String line : content.split("\n"))
        {
            String trimmed = line.trim();
            if (!trimmed.isEmpty())
            {
                items.add(trimmed);
            }
        }
        return items;
    }

    /** 从 "- [类别] 内容" 里取出类别；格式不对就返回空串 */
    private static String categoryOf(String item)
    {
        if (!item.startsWith(ITEM_PREFIX))
        {
            return "";
        }
        int end = item.indexOf(']');
        if (end < 0)
        {
            return "";
        }
        return item.substring(ITEM_PREFIX.length(), end).trim();
    }
}
