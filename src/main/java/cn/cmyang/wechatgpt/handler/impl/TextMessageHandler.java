package cn.cmyang.wechatgpt.handler.impl;

import cn.cmyang.wechatgpt.common.CommonConstant;
import cn.cmyang.wechatgpt.common.annotation.WxMessageType;
import cn.cmyang.wechatgpt.common.enums.WxMessageTypeEnum;
import cn.cmyang.wechatgpt.handler.WxMessageHandler;
import cn.cmyang.wechatgpt.manager.AsyncManager;
import cn.cmyang.wechatgpt.service.ChatgptService;
import cn.cmyang.wechatgpt.utils.RedisCacheUtils;
import lombok.extern.slf4j.Slf4j;
import me.chanjar.weixin.mp.bean.message.WxMpXmlMessage;
import me.chanjar.weixin.mp.bean.message.WxMpXmlOutTextMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@WxMessageType(WxMessageTypeEnum.TEXT)
public class TextMessageHandler implements WxMessageHandler {

    private static final Map<String, Integer> messageCountMap = new ConcurrentHashMap<>();

    @Autowired
    private RedisCacheUtils redisCacheUtils;

    @Autowired
    private ChatgptService chatgptService;

    @Override
    public String handlerMessage(WxMpXmlMessage mpMessage) {
        long start = System.currentTimeMillis();
        String msgId = String.valueOf(mpMessage.getMsgId());
        //缓存每个对话的微信调用次数
        messageCountMap.merge(msgId, 1, Integer::sum);
        //当前是微信的第几次调用
        final Integer currentMsgCount = messageCountMap.get(msgId);

        //默认返回success
        WxMpXmlOutTextMessage textMessage = genTextMessage(mpMessage);
        textMessage.setContent(CommonConstant.SUCCESS);

        //处理 "继续" 逻辑
        final String waitKey = String.format(CommonConstant.CHAT_WX_USER_WAIT_KEY, mpMessage.getOpenId());
        if ("继续".equals(mpMessage.getContent())) {
            while (messageCountMap.containsKey(msgId)) {
                String replay = checkMessageCountMap(msgId, currentMsgCount, start);
                if (null != replay) {
                    textMessage.setContent(replay);
                    break;
                }
                if (redisCacheUtils.hasKey(waitKey)) {
                    Object o = redisCacheUtils.getCacheObject(waitKey);
                    messageCountMap.remove(msgId);
                    Integer contentLength = getByteSize(String.valueOf(o));
                    if (contentLength < 2048) {
                        redisCacheUtils.deleteObject(waitKey);
                        textMessage.setContent(String.valueOf(o));
                    } else {
                        String replyContent = String.valueOf(o).substring(0, 580);
                        redisCacheUtils.setCacheObject(waitKey, String.valueOf(o).replace(replyContent, ""), 60, TimeUnit.MINUTES);
                        textMessage.setContent(replyContent + "\n(回复字符限制，输入\"继续\"查看后续内容)");
                    }
                    break;
                }
            }
            return textMessage.toXml();
        }

        //调用 ChatGPT
        final String msgKey = String.format(CommonConstant.CHAT_WX_USER_MSG_REPLY_KEY, msgId);
        if (!redisCacheUtils.hasKey(msgKey)) {
            redisCacheUtils.setCacheObject(msgKey, textMessage.getContent(), 30, TimeUnit.SECONDS);
            AsyncManager.me().execute(new TimerTask() {
                @Override
                public void run() {
                    redisCacheUtils.deleteObject(waitKey);
                    chatgptService.singleChatStreamToWX(mpMessage.getOpenId(), msgId, mpMessage.getContent());
                }
            });
        }

        while (messageCountMap.containsKey(msgId)) {
            String replay = checkMessageCountMap(msgId, currentMsgCount, start);
            if (null != replay) {
                textMessage.setContent(replay);
                break;
            }
            Object o = redisCacheUtils.getCacheObject(msgKey);
            if (!textMessage.getContent().equals(String.valueOf(o))) {
                messageCountMap.remove(msgId);
                redisCacheUtils.deleteObject(Arrays.asList(msgKey, waitKey));
                textMessage.setContent(String.valueOf(o));
                break;
            }
        }
        return textMessage.getContent().equals(CommonConstant.SUCCESS) ? CommonConstant.SUCCESS : textMessage.toXml();
    }

    private synchronized String checkMessageCountMap(String msgId, final Integer currentMsgCount, long start) {
        String result = null;
        if (currentMsgCount < 3 && !currentMsgCount.equals(messageCountMap.get(msgId))) {
            result = CommonConstant.SUCCESS;
        }
        if (currentMsgCount == 3 && (System.currentTimeMillis() - start) >= 4200) {
            result = "消息生成中... \n请稍后输入\"继续\"获取结果";
        }
        return result;
    }

    private Integer getByteSize(String content) {
        int size = 0;
        if (null != content) {
            try {
                // 汉字采用utf-8编码时占3个字节
                size = content.getBytes(StandardCharsets.UTF_8).length;
            } catch (Exception e) {
                log.error("", e);
            }
        }
        return size;
    }

}
