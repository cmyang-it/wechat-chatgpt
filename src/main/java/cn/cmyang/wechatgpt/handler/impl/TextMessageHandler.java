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
    private ChatgptService chatGPTService;

    @Override
    public String handlerMessage(WxMpXmlMessage mpMessage) {
        long start = System.currentTimeMillis();

        //缓存每个对话的微信调用次数
        String msgId = String.valueOf(mpMessage.getMsgId());
        messageCountMap.merge(msgId, 1, Integer::sum);
        final Integer currentMsgCount = messageCountMap.get(msgId);

        //默认返回success
        WxMpXmlOutTextMessage textMessage = genTextMessage(mpMessage);
        textMessage.setContent(CommonConstant.SUCCESS);

        //缓存openai回答的key
        final String openIdKey = String.format(CommonConstant.CHAT_WX_USER_WAIT_KEY, mpMessage.getOpenId());

        //调用 ChatGPT
        if (messageCountMap.get(msgId) == 1) {
            redisCacheUtils.deleteObject(openIdKey);
            redisCacheUtils.setCacheObject(openIdKey, textMessage.getContent(), 60, TimeUnit.SECONDS);
            AsyncManager.me().execute(new TimerTask() {
                @Override
                public void run() {
                    chatGPTService.chatStream(mpMessage.getOpenId(), mpMessage.getContent());
                }
            });
        }

        while (messageCountMap.containsKey(msgId)) {
            String replay = checkMessageCountMap(msgId, currentMsgCount, start);
            if (null != replay) {
                textMessage.setContent(replay);
                break;
            }
            Object o = redisCacheUtils.getCacheObject(openIdKey);
            if (!CommonConstant.SUCCESS.equals(String.valueOf(o))) {
                messageCountMap.remove(msgId);
                Integer contentLength = getByteSize(String.valueOf(o));
                if (contentLength < 2048) {
                    redisCacheUtils.deleteObject(openIdKey);
                    textMessage.setContent(String.valueOf(o));
                } else {
                    String replyContent = String.valueOf(o).substring(0, 580);
                    redisCacheUtils.setCacheObject(openIdKey, String.valueOf(o).replace(replyContent, ""), 60, TimeUnit.SECONDS);
                    textMessage.setContent(replyContent + "\n(回复字符限制，一分钟内输入\"" + CommonConstant.RESUME + "\"查看后续内容)");
                }
            }
        }

        log.info("回复用户:{}", textMessage.getContent());
        return textMessage.getContent().equals(CommonConstant.SUCCESS) ? CommonConstant.SUCCESS : textMessage.toXml();
    }

    private String checkMessageCountMap(String msgId, final Integer currentMsgCount, long start) {
        if (currentMsgCount < 3 && currentMsgCount < messageCountMap.get(msgId)) {
            return CommonConstant.SUCCESS;
        }
        if (currentMsgCount == 3 && (System.currentTimeMillis() - start) >= 3000) {
            return "消息生成中... \n请稍后输入\"" + CommonConstant.RESUME + "\"获取结果";
        }
        return null;
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
