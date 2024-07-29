package cn.cmyang.wechatgpt.service.impl;

import cn.cmyang.wechatgpt.bean.CacheMessageBean;
import cn.cmyang.wechatgpt.common.CommonConstant;
import cn.cmyang.wechatgpt.config.ChatgptConfig;
import cn.cmyang.wechatgpt.config.GenImageConfig;
import cn.cmyang.wechatgpt.listener.WeChatEventSourceListener;
import cn.cmyang.wechatgpt.service.ChatgptService;
import cn.cmyang.wechatgpt.utils.RedisCacheUtils;
import com.unfbx.chatgpt.OpenAiClient;
import com.unfbx.chatgpt.OpenAiStreamClient;
import com.unfbx.chatgpt.entity.chat.BaseMessage;
import com.unfbx.chatgpt.entity.chat.ChatCompletion;
import com.unfbx.chatgpt.entity.chat.Message;
import com.unfbx.chatgpt.entity.images.Image;
import com.unfbx.chatgpt.entity.images.ImageResponse;
import lombok.extern.slf4j.Slf4j;
import me.chanjar.weixin.common.bean.result.WxMediaUploadResult;
import me.chanjar.weixin.mp.api.WxMpService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.io.ByteArrayInputStream;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class ChatgptServiceImpl implements ChatgptService {

    @Autowired
    private ChatgptConfig config;

    @Autowired
    private GenImageConfig mpConfig;

    @Autowired
    private RedisCacheUtils redisCacheUtils;

    @Autowired
    private WxMpService wxMpService;

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

    /**
     * 生成图片并上传到公众号临时素材库（保存时间为3天，即3天后media_id失效）
     * @param openId 用户openid
     * @param msgId 会话msgid
     * @param prompt 绘图描述
     */
    @Override
    public void generateImage(String openId, String msgId, String prompt) {
        OpenAiClient client = getClient();
        Image image;
        //这里可以用其他风格，不局限于接口规定的两个 natural or vivid
        prompt += " in the style of " + mpConfig.getGenImageStyle();
        if (config.getGenImageModel().equals(Image.Model.DALL_E_2.getName())) {
            image = Image.builder()
                    .model(config.getGenImageModel())
                    .responseFormat(mpConfig.getGenImageResultType())
                    .size(mpConfig.getGenImageSize())
                    .prompt(prompt)
                    .build();
        } else {
            image = Image.builder()
                    .model(config.getGenImageModel())
                    .responseFormat(mpConfig.getGenImageResultType())
                    .size(mpConfig.getGenImageSize())
                    .style(mpConfig.getGenImageStyle())
                    .prompt(prompt)
                    .build();
        }
        ImageResponse response = client.genImages(image);
        if (null != response && !CollectionUtils.isEmpty(response.getData())) {
            String base64String = response.getData().get(0).getB64Json();
            byte[] bytes = Base64.getDecoder().decode(base64String);
            try {
                //上传图片到临时素材库
                WxMediaUploadResult result = wxMpService.getMaterialService().mediaUpload("image", "png", new ByteArrayInputStream(bytes));
                log.debug("{}", result);
                if (null != result && StringUtils.isNotBlank(result.getMediaId())) {
                    redisCacheUtils.setCacheObject(String.format(CommonConstant.CHAT_WX_USER_WAIT_KEY, openId), result.getMediaId(), 60, TimeUnit.MINUTES);
                    redisCacheUtils.setCacheObject(String.format(CommonConstant.CHAT_WX_USER_MSG_REPLY_KEY, msgId), result.getMediaId(), 30, TimeUnit.SECONDS);
                }
            } catch (Exception e) {
                log.error("", e);
            }
        }
    }

    private OpenAiStreamClient getStreamClient() {
        return OpenAiStreamClient.builder()
                .apiKey(config.getApiKey())
                .apiHost(config.getBaseUrl())
                .build();
    }

    private OpenAiClient getClient() {
        return OpenAiClient.builder()
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
