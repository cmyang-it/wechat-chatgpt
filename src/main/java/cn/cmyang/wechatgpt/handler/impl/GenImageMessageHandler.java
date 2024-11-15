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

import java.util.*;
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
    private ChatgptService chatGPTService;


    @Override
    public String handlerMessage(WxMpXmlMessage mpMessage) {
        long start = System.currentTimeMillis();

        //默认返回success
        WxMpXmlOutTextMessage textMessage = genTextMessage(mpMessage);
        textMessage.setContent(CommonConstant.SUCCESS);

        //缓存每个对话的微信调用次数
        String msgId = String.valueOf(mpMessage.getMsgId());
        genImageMessageCountMap.merge(msgId, 1, Integer::sum);
        //当前是微信的第几次调用
        final Integer currentMsgCount = genImageMessageCountMap.get(msgId);

        //绘图限制
        String genImageKey = String.format(CommonConstant.GEN_IMAGE_WX_USER_RESTRICT_KEY, mpMessage.getOpenId());
        if (genImageMessageCountMap.get(msgId) == 1 && redisCacheUtils.hasKey(genImageKey)) {
            textMessage.setContent("绘图限制，稍后再试");
            return textMessage.toXml();
        }

        //调用 ChatGPT
        final String openIdKey = String.format(CommonConstant.CHAT_WX_USER_WAIT_KEY, mpMessage.getOpenId());
        if (genImageMessageCountMap.get(msgId) == 1) {
            redisCacheUtils.deleteObject(openIdKey);
            redisCacheUtils.setCacheObject(openIdKey, CommonConstant.SUCCESS, 60, TimeUnit.SECONDS);
            AsyncManager.me().execute(new TimerTask() {
                @Override
                public void run() {
                    chatGPTService.generateImage(mpMessage.getOpenId(), msgId, mpMessage.getContent().replace(config.getMessagePrefix(), ""));
                }
            });
        }

        while (genImageMessageCountMap.containsKey(msgId)) {
            String replay = checkMessageCountMap(msgId, currentMsgCount, start);
            if (null != replay) {
                textMessage.setContent(replay);
                break;
            }
            Object o = redisCacheUtils.getCacheObject(openIdKey);
            if (null != o && !textMessage.getContent().equals(String.valueOf(o))) {
                genImageMessageCountMap.remove(msgId);
                redisCacheUtils.deleteObject(openIdKey);
                if (String.valueOf(o).startsWith(CommonConstant.CHAT_IMAGE_RESULT_PREFIX)) {
                    //回复图片
                    WxMpXmlOutImageMessage imageMessage = genImageMessage(mpMessage);
                    imageMessage.setMediaId(String.valueOf(o).replace(CommonConstant.CHAT_IMAGE_RESULT_PREFIX, ""));
                    log.info("回复用户图片:{}", o);
                    return imageMessage.toXml();
                }
                textMessage.setContent(String.valueOf(o));
            }
        }
        log.info("回复用户:{}", textMessage.getContent());
        return textMessage.getContent().equals(CommonConstant.SUCCESS) ? CommonConstant.SUCCESS : textMessage.toXml();
    }

    private String checkMessageCountMap(String msgId, final Integer currentMsgCount, long start) {
        if (currentMsgCount < 3 && currentMsgCount < genImageMessageCountMap.get(msgId)) {
            return CommonConstant.SUCCESS;
        }
        if (currentMsgCount == 3 && (System.currentTimeMillis() - start) >= 3000) {
            return "图像生成中...\n请稍后输入\"" + CommonConstant.RESUME + "\"获取结果";
        }
        return null;
    }

}
