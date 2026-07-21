package com.hmdp.service.impl;

import com.hmdp.entity.Blog;
import com.hmdp.entity.LikedRecord;
import com.hmdp.mapper.LikedRecordMapper;
import com.hmdp.service.IBlogService;
import com.hmdp.service.ILikedRecordService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.dao.DataAccessException;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LikedRecordServiceImpl extends ServiceImpl<LikedRecordMapper, LikedRecord> implements ILikedRecordService {

    private final StringRedisTemplate stringRedisTemplate;
    private final IBlogService blogService;

    @Override
    public void addLikeRecord(Long blogId, boolean liked) {
        Long userId = UserHolder.getUser().getId();
        if (userId == null) return;

        String setKey = RedisConstants.LIKES_BIZ_KEY_PREFIX + blogId;
        String timesKey = RedisConstants.LIKES_TIMES_KEY_PREFIX + "BLOG";
        String rankKey = RedisConstants.BLOG_LIKES_KEY;

        if (liked) {
            // 点赞：SADD 自动去重
            Long result = stringRedisTemplate.opsForSet().add(setKey, userId.toString());
            if (result != null && result > 0) {
                Long likedTimes = stringRedisTemplate.opsForSet().size(setKey);
                if (likedTimes != null) {
                    stringRedisTemplate.opsForZSet().add(timesKey, blogId.toString(), likedTimes);
                    stringRedisTemplate.opsForZSet().incrementScore(rankKey, blogId.toString(), 1);
                }
            }
        } else {
            // 取消点赞：SREM
            Long result = stringRedisTemplate.opsForSet().remove(setKey, userId.toString());
            if (result != null && result > 0) {
                Long likedTimes = stringRedisTemplate.opsForSet().size(setKey);
                if (likedTimes != null) {
                    stringRedisTemplate.opsForZSet().add(timesKey, blogId.toString(), likedTimes);
                    stringRedisTemplate.opsForZSet().incrementScore(rankKey, blogId.toString(), -1);
                }
            }
        }
    }

    @Override
    public Set<Long> isBlogLiked(List<Long> blogIds) {
        Long userId = UserHolder.getUser().getId();
        if (userId == null) return Collections.emptySet();

        // ✅ 使用 SessionCallback，不需要去手动转 byte[]，不会出现序列化错误
        List<Object> results = stringRedisTemplate.executePipelined(
                new SessionCallback<List<Object>>() {
                    @Override
                    public List<Object> execute(org.springframework.data.redis.core.RedisOperations operations) throws DataAccessException {
                        for (Long blogId : blogIds) {
                            String key = RedisConstants.LIKES_BIZ_KEY_PREFIX + blogId;
                            // ✅ 直接使用 opsForSet().isMember，安全又高效
                            operations.opsForSet().isMember(key, userId.toString());
                        }
                        return null;
                    }
                }
        );

        // ✅ 解析结果，并过滤出已点赞的 blogId
        Set<Long> likedBlogIds = new HashSet<>();
        for (int i = 0; i < blogIds.size(); i++) {
            // 注意：pipeline 返回的结果是 Boolean 类型，不能直接用强转，要先判断
            Object rawResult = results.get(i);
            if (rawResult instanceof Boolean && (Boolean) rawResult) {
                likedBlogIds.add(blogIds.get(i));
            }
        }
        return likedBlogIds;
    }

    @Scheduled(fixedDelay = 20000)
    public void syncLikedTimes() {
        String key = RedisConstants.LIKES_TIMES_KEY_PREFIX + "BLOG";
        Set<ZSetOperations.TypedTuple<String>> tuples = stringRedisTemplate.opsForZSet().popMin(key, 30);

        if (tuples == null || tuples.isEmpty()) return;

        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            Long blogId = Long.valueOf(tuple.getValue());
            Integer likedTimes = tuple.getScore().intValue();

            Blog blog = new Blog();
            blog.setId(blogId);
            blog.setLiked(likedTimes);
            blogService.updateById(blog);
        }
    }
}