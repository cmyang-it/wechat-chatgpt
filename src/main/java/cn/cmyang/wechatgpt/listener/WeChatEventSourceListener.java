package cn.cmyang.wechatgpt.listener;

import cn.cmyang.wechatgpt.bean.CacheMessageBean;
import cn.cmyang.wechatgpt.common.CommonConstant;
import cn.cmyang.wechatgpt.utils.RedisCacheUtils;
import cn.hutool.extra.spring.SpringUtil;
import com.alibaba.fastjson2.JSON;
import com.unfbx.chatgpt.entity.chat.BaseMessage;
import com.unfbx.chatgpt.entity.chat.ChatCompletionResponse;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Slf4j
public class WeChatEventSourceListener extends EventSourceListener {

    private String openId;

    private StringBuffer sb;

    private static final RedisCacheUtils redisCacheUtils = SpringUtil.getBean(RedisCacheUtils.class);

    public WeChatEventSourceListener() {}

    public WeChatEventSourceListener(String openId) {
        this.openId = openId;
        this.sb = new StringBuffer();
    }

    @Override
    public void onClosed(@NotNull EventSource eventSource) {
        log.info("OpenAI关闭sse连接...");
        //缓存回复到redis
        cacheToRedis(sb.toString());
        eventSource.cancel();
    }

    @Override
    public void onEvent(@NotNull EventSource eventSource, @Nullable String id, @Nullable String type, @NotNull String data) {
        log.debug("OpenAI返回数据：{}", data);
        if (!"[DONE]".equals(data)) {
            ChatCompletionResponse response = JSON.parseObject(data, ChatCompletionResponse.class);
            if (null == response.getChoices().get(0).getFinishReason()) {
                String content = response.getChoices().get(0).getDelta().getContent();
                sb.append(content);
            }
        } else {
            log.info("OpenAI返回数据结束了");
        }
    }

    @Override
    public void onFailure(@NotNull EventSource eventSource, @Nullable Throwable t, @Nullable Response response) {
        try {
            if (Objects.isNull(response)) {
                log.error("OpenAI  sse连接异常:", t);
            } else {
                ResponseBody body = response.body();
                if (Objects.nonNull(body)) {
                    log.error("OpenAI  sse连接异常data：{}，异常：", body.string(), t);
                } else {
                    log.error("OpenAI  sse连接异常data：{}，异常：", response, t);
                }
            }
            cacheToRedis("The ChatGPT call failed, please try again later");
            eventSource.cancel();
        } catch (Exception e) {
            log.error("", e);
        }
    }

    @Override
    public void onOpen(@NotNull EventSource eventSource, @NotNull Response response) {
        log.info("OpenAI建立sse连接...");
    }

    /**
     * 缓存数据到redis中
     * @param result gpt返回数据
     */
    private void cacheToRedis(String result) {
        //缓存回复到redis
        redisCacheUtils.setCacheObject(String.format(CommonConstant.CHAT_WX_USER_WAIT_KEY, openId), result, 60, TimeUnit.MINUTES);

        //缓存对话
        String key = String.format(CommonConstant.CHAT_WX_CACHE_MESSAGE_KEY, openId);
        if (redisCacheUtils.hasKey(key)) {
            List<CacheMessageBean> cacheMessageList = new LinkedList<>();
            cacheMessageList.add(new CacheMessageBean(BaseMessage.Role.ASSISTANT.getName(), result));
            redisCacheUtils.setCacheList(key, cacheMessageList);
        }
    }

}
