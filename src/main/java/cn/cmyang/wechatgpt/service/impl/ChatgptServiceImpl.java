package cn.cmyang.wechatgpt.service.impl;

import cn.cmyang.wechatgpt.bean.CacheMessageBean;
import cn.cmyang.wechatgpt.common.CommonConstant;
import cn.cmyang.wechatgpt.config.ChatgptConfig;
import cn.cmyang.wechatgpt.listener.WeChatEventSourceListener;
import cn.cmyang.wechatgpt.service.ChatgptService;
import cn.cmyang.wechatgpt.utils.RedisCacheUtils;
import com.unfbx.chatgpt.OpenAiStreamClient;
import com.unfbx.chatgpt.entity.chat.BaseMessage;
import com.unfbx.chatgpt.entity.chat.ChatCompletion;
import com.unfbx.chatgpt.entity.chat.Message;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.*;

@Slf4j
@Service
public class ChatgptServiceImpl implements ChatgptService {

    @Autowired
    private ChatgptConfig config;

    @Autowired
    private RedisCacheUtils redisCacheUtils;

    /**
     * 多轮会话
     * @param openId 用户openid
     * @param msgId 消息id
     * @param content 问话内容
     */
    @Override
    public void multiChatStreamToWX(String openId, String msgId, String content) {
        OpenAiStreamClient streamClient = getStreamClient();
        WeChatEventSourceListener weChatEventSourceListener = new WeChatEventSourceListener(openId, msgId);
        //获取历史会话记录
        List<Message> messages = getWxMessageList(openId, content);
        ChatCompletion chatCompletion = ChatCompletion.builder().model(config.getModel()).stream(true).messages(messages).build();
        streamClient.streamChatCompletion(chatCompletion, weChatEventSourceListener);
    }

    /**
     * 单轮会话
     * @param openId 用户openid
     * @param msgId 消息id
     * @param content 问话内容
     */
    @Override
    public void singleChatStreamToWX(String openId, String msgId, String content) {
        OpenAiStreamClient streamClient = getStreamClient();
        WeChatEventSourceListener weChatEventSourceListener = new WeChatEventSourceListener(openId, msgId);
        Message message = Message.builder().role(BaseMessage.Role.USER).content(content).build();
        ChatCompletion chatCompletion = ChatCompletion.builder().model(config.getModel()).stream(true).messages(Arrays.asList(message)).build();
        streamClient.streamChatCompletion(chatCompletion, weChatEventSourceListener);
    }


    private OpenAiStreamClient getStreamClient() {
        return OpenAiStreamClient.builder()
                .apiKey(config.getApiKey())
                .apiHost(config.getBaseUrl())
                .build();
    }

    private List<Message> getWxMessageList(String openId, String content) {
        List<Message> messages = new LinkedList<>();
        Message newMessage = Message.builder().role(BaseMessage.Role.USER).content(content).build();
        String key = String.format(CommonConstant.CHAT_WX_CACHE_MESSAGE_KEY, openId);
        List<CacheMessageBean> cacheList = new ArrayList<>();
        if (redisCacheUtils.hasKey(key)) {
            cacheList = redisCacheUtils.getCacheList(key);
            if (!CollectionUtils.isEmpty(cacheList)) {
                for (CacheMessageBean cacheMessageBean : cacheList) {
                    messages.add(Message.builder().role(cacheMessageBean.getRole()).content(cacheMessageBean.getContent()).build());
                }
            }
            redisCacheUtils.deleteObject(key);
        }
        cacheList.add(new CacheMessageBean(openId, BaseMessage.Role.USER.getName(), content));
        redisCacheUtils.setCacheList(key, cacheList);
        messages.add(newMessage);
        return messages;
    }

}
