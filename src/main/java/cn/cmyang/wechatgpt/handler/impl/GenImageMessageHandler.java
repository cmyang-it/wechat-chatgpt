package cn.cmyang.wechatgpt.handler.impl;

import cn.cmyang.wechatgpt.common.CommonConstant;
import cn.cmyang.wechatgpt.common.annotation.WxMessageType;
import cn.cmyang.wechatgpt.common.enums.WxMessageTypeEnum;
import cn.cmyang.wechatgpt.config.GenImageConfig;
import cn.cmyang.wechatgpt.handler.WxMessageHandler;
import cn.cmyang.wechatgpt.manager.AsyncManager;
import cn.cmyang.wechatgpt.service.ChatgptService;
import cn.cmyang.wechatgpt.utils.RedisCacheUtils;
import lombok.extern.slf4j.Slf4j;
import me.chanjar.weixin.mp.bean.message.WxMpXmlMessage;
import me.chanjar.weixin.mp.bean.message.WxMpXmlOutImageMessage;
import me.chanjar.weixin.mp.bean.message.WxMpXmlOutTextMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Map;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@WxMessageType(WxMessageTypeEnum.GEN_IMAGE)
public class GenImageMessageHandler implements WxMessageHandler {

    private static final Map<String, Integer> genImageMessageCountMap = new ConcurrentHashMap<>();

    @Autowired
    private GenImageConfig config;

    @Autowired
    private RedisCacheUtils redisCacheUtils;

    @Autowired
    private ChatgptService chatgptService;


    @Override
    public String handlerMessage(WxMpXmlMessage mpMessage) {
        long start = System.currentTimeMillis();
        String msgId = String.valueOf(mpMessage.getMsgId());

        //缓存每个对话的微信调用次数
        genImageMessageCountMap.merge(msgId, 1, Integer::sum);
        //当前是微信的第几次调用
        final Integer currentMsgCount = genImageMessageCountMap.get(msgId);

        //默认返回success
        WxMpXmlOutTextMessage textMessage = genTextMessage(mpMessage);
        textMessage.setContent(CommonConstant.SUCCESS);

        //正式的处理逻辑
        final String waitKey = String.format(CommonConstant.CHAT_WX_USER_WAIT_KEY, mpMessage.getOpenId());
        if ("图片".equals(mpMessage.getContent())) {
            while (genImageMessageCountMap.containsKey(msgId)) {
                String replay = checkMessageCountMap(msgId, currentMsgCount, start);
                if (null != replay) {
                    textMessage.setContent(replay);
                    break;
                }
                if (redisCacheUtils.hasKey(waitKey)) {
                    Object o = redisCacheUtils.getCacheObject(waitKey);
                    genImageMessageCountMap.remove(msgId);
                    redisCacheUtils.deleteObject(waitKey);
                    //回复图片
                    WxMpXmlOutImageMessage imageMessage = genImageMessage(mpMessage);
                    imageMessage.setMediaId(String.valueOf(o));
                    return imageMessage.toXml();
                }
            }
            return textMessage.toXml();
        }

        //调用 ChatGPT
        final String msgKey = String.format(CommonConstant.CHAT_WX_USER_MSG_REPLY_KEY, msgId);
        //绘图限制
        String genImageKey = String.format(CommonConstant.GEN_IMAGE_WX_USER_RESTRICT_KEY, mpMessage.getOpenId());
        if (!redisCacheUtils.hasKey(msgKey)  && redisCacheUtils.hasKey(genImageKey)) {
            textMessage.setContent("当前绘图限制为：1h/张");
            return textMessage.toXml();
        }
        if (!redisCacheUtils.hasKey(msgKey)) {
            redisCacheUtils.setCacheObject(msgKey, textMessage.getContent(), 30, TimeUnit.SECONDS);
            AsyncManager.me().execute(new TimerTask() {
                @Override
                public void run() {
                    redisCacheUtils.deleteObject(waitKey);
                    //控制每个人画图限制
                    redisCacheUtils.setCacheObject(genImageKey, mpMessage.getOpenId(), config.getRestrictTime(), TimeUnit.SECONDS);
                    chatgptService.generateImage(mpMessage.getOpenId(), msgId, mpMessage.getContent().replace(config.getMessagePrefix(), ""));
                }
            });
        }

        while (genImageMessageCountMap.containsKey(msgId)) {
            String replay = checkMessageCountMap(msgId, currentMsgCount, start);
            if (null != replay) {
                textMessage.setContent(replay);
                break;
            }
            Object o = redisCacheUtils.getCacheObject(msgKey);
            if (!textMessage.getContent().equals(String.valueOf(o))) {
                genImageMessageCountMap.remove(msgId);
                redisCacheUtils.deleteObject(Arrays.asList(msgKey, waitKey));
                //回复图片
                WxMpXmlOutImageMessage imageMessage = genImageMessage(mpMessage);
                imageMessage.setMediaId(String.valueOf(o));
                return imageMessage.toXml();
            }
        }
        return textMessage.getContent().equals(CommonConstant.SUCCESS) ? CommonConstant.SUCCESS : textMessage.toXml();
    }

    private synchronized String checkMessageCountMap(String msgId, final Integer currentMsgCount, long start) {
        String result = null;
        if (currentMsgCount < 3 && !currentMsgCount.equals(genImageMessageCountMap.get(msgId))) {
            result = CommonConstant.SUCCESS;
        }
        if (currentMsgCount == 3 && (System.currentTimeMillis() - start) >= 4200) {
            result = "图像生成中...\n请稍后输入\"图片\"获取结果";
        }
        return result;
    }

}
