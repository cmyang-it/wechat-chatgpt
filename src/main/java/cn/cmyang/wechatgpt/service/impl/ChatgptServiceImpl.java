package cn.cmyang.wechatgpt.service.impl;

import cn.cmyang.wechatgpt.bean.CacheMessageBean;
import cn.cmyang.wechatgpt.common.CommonConstant;
import cn.cmyang.wechatgpt.config.ChatGPTConfig;
import cn.cmyang.wechatgpt.config.GenImageConfig;
import cn.cmyang.wechatgpt.listener.WeChatEventSourceListener;
import cn.cmyang.wechatgpt.service.ChatgptService;
import cn.cmyang.wechatgpt.utils.RedisCacheUtils;
import com.alibaba.fastjson2.JSON;
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

    private static final List<String> DEFAULT_STYLE_LIST = new LinkedList<>(Arrays.asList("natural", "vivid"));

    @Autowired
    private ChatGPTConfig config;

    @Autowired
    private GenImageConfig genImageConfig;

    @Autowired
    private GenImageConfig mpConfig;

    @Autowired
    private RedisCacheUtils redisCacheUtils;

    @Autowired
    private WxMpService wxMpService;

    /**
     * 对话模型
     * @param openId 用户openid
     * @param content 问话内容
     */
    @Override
    public void chatStream(String openId, String content) {
        OpenAiStreamClient streamClient = getStreamClient();
        WeChatEventSourceListener weChatEventSourceListener = new WeChatEventSourceListener(openId);
        //获取对话
        List<Message> messages = getWxMessageList(openId, content);
        ChatCompletion chatCompletion = ChatCompletion.builder().maxTokens(config.getMaxTokens()).model(config.getModel()).stream(true).messages(messages).build();
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
        Image image = Image.builder()
                .model(mpConfig.getModel())
                .responseFormat("b64_json")
                .size(mpConfig.getSize())
                .prompt(prompt)
                .build();
        if (mpConfig.getModel().equals(Image.Model.DALL_E_3.getName())) {
            image.setStyle(mpConfig.getStyle());
            //这里可以用其他风格，不局限于接口规定的两个 natural or vivid
            if (!DEFAULT_STYLE_LIST.contains(mpConfig.getStyle())) {
                image.setStyle("natural");
                prompt += " in the style of " + mpConfig.getStyle();
                image.setPrompt(prompt);
            }
        }
        try {
            ImageResponse response = getClient().genImages(image);
            if (null != response && !CollectionUtils.isEmpty(response.getData())) {
                //控制每个人画图限制
                redisCacheUtils.setCacheObject(String.format(CommonConstant.GEN_IMAGE_WX_USER_RESTRICT_KEY, openId), openId, genImageConfig.getRestrictTime(), TimeUnit.SECONDS);

                String base64String = response.getData().get(0).getB64Json();
                byte[] bytes = Base64.getDecoder().decode(base64String);
                WxMediaUploadResult result = wxMpService.getMaterialService().mediaUpload("image", "png", new ByteArrayInputStream(bytes));
                if (null != result && StringUtils.isNotBlank(result.getMediaId())) {
                    //缓存回复到redis
                    redisCacheUtils.setCacheObject(String.format(CommonConstant.CHAT_WX_USER_WAIT_KEY, openId),
                            CommonConstant.CHAT_IMAGE_RESULT_PREFIX + result.getMediaId(), 60, TimeUnit.SECONDS);
                }
            }
        } catch (Exception e) {
            log.error("", e);
            redisCacheUtils.setCacheObject(String.format(CommonConstant.CHAT_WX_USER_WAIT_KEY, openId),
                    "图片生成失败，请稍后再试", 60, TimeUnit.SECONDS);
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
        Message sysMessage = Message.builder().role(BaseMessage.Role.SYSTEM).content(config.getCallWord()).build();
        Message newMessage = Message.builder().role(BaseMessage.Role.USER).content(content).build();
        //设置角色提示词
        messages.add(sysMessage);
        //是否开启多轮对话
        String key = String.format(CommonConstant.CHAT_WX_CACHE_MESSAGE_KEY, openId);
        List<CacheMessageBean> cacheList = new LinkedList<>();
        if (redisCacheUtils.hasKey(key) && config.getMessageSize() > 0) {
            cacheList = redisCacheUtils.getCacheList(key);
            redisCacheUtils.deleteObject(key);
            if (!CollectionUtils.isEmpty(cacheList)) {
                if (cacheList.size() > config.getMessageSize() * 2) {
                    int len = cacheList.size() - (config.getMessageSize() * 2);
                    cacheList = cacheList.subList(len, cacheList.size());
                }
                cacheList.forEach(x -> messages.add(Message.builder().role(x.getRole()).content(x.getContent()).build()));
            }

        }
        cacheList.add(new CacheMessageBean(openId, BaseMessage.Role.USER.getName(), content));
        redisCacheUtils.setCacheList(key, cacheList);
        redisCacheUtils.expire(key, 60 , TimeUnit.MINUTES);
        //最新对话
        messages.add(newMessage);
        log.debug("{}", JSON.toJSONString(messages));
        return messages;
    }


}
