package cn.cmyang.wechatgpt.listener;

import cn.cmyang.wechatgpt.bean.CacheMessageBean;
import cn.cmyang.wechatgpt.common.CommonConstant;
import cn.cmyang.wechatgpt.config.ChatgptConfig;
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
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
public class WeChatEventSourceListener extends EventSourceListener {

    private String openId;
    private String msgId;

    private StringBuffer sb;

    private static final RedisCacheUtils redisCacheUtils = SpringUtil.getBean(RedisCacheUtils.class);

    private static final ChatgptConfig chatgptConfig = SpringUtil.getBean(ChatgptConfig.class);

    private static final Map<String, String> responseMap = new ConcurrentHashMap<>();

    public WeChatEventSourceListener() {}

    public WeChatEventSourceListener(String openId, String msgId) {
        this.openId = openId;
        this.msgId = msgId;
        this.sb = new StringBuffer();
    }

    @Override
    public void onClosed(@NotNull EventSource eventSource) {
        log.info("OpenAI关闭sse连接...");
        //缓存回复到redis
        redisCacheUtils.setCacheObject(String.format(CommonConstant.CHAT_WX_USER_WAIT_KEY, openId), sb.toString(), 60, TimeUnit.MINUTES);
        redisCacheUtils.setCacheObject(String.format(CommonConstant.CHAT_WX_USER_MSG_REPLY_KEY, msgId), sb.toString(), 30, TimeUnit.SECONDS);
        //缓存上下文到redis
        cacheReply();
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
                eventSource.cancel();
            } else {
                ResponseBody body = response.body();
                if (Objects.nonNull(body)) {
                    log.error("OpenAI  sse连接异常data：{}，异常：", body.string(), t);
                } else {
                    log.error("OpenAI  sse连接异常data：{}，异常：", response, t);
                }
                eventSource.cancel();
            }
        } catch (Exception e) {
            log.error("", e);
        }
    }

    @Override
    public void onOpen(@NotNull EventSource eventSource, @NotNull Response response) {
        log.info("OpenAI建立sse连接...");
    }

    private void cacheReply() {
        if (StringUtils.isNotBlank(openId)) {
            try {
                String key = String.format(CommonConstant.CHAT_WX_CACHE_MESSAGE_KEY, openId);
                if (redisCacheUtils.hasKey(key)) {
                    List<CacheMessageBean> cacheList = redisCacheUtils.getCacheList(key);
                    CacheMessageBean messageBean = new CacheMessageBean(openId, BaseMessage.Role.ASSISTANT.getName(), sb.toString());
                    if (cacheList.size() >= chatgptConfig.getMessageSize()) {
                        cacheList = cacheList.subList(cacheList.size() - chatgptConfig.getMessageSize(), cacheList.size());
                    }
                    cacheList.add(messageBean);
                    redisCacheUtils.deleteObject(key);
                    redisCacheUtils.setCacheList(key, cacheList);
                }
            } catch (Exception e) {
                log.error("", e);
            }
        }
    }

}
