package cn.cmyang.wechatgpt.service.impl;

import cn.cmyang.wechatgpt.bean.WeChatBean;
import cn.cmyang.wechatgpt.common.CommonConstant;
import cn.cmyang.wechatgpt.config.WechatMpConfig;
import cn.cmyang.wechatgpt.service.ChatgptService;
import cn.cmyang.wechatgpt.service.WeChatService;
import cn.cmyang.wechatgpt.utils.RedisCacheUtils;
import cn.cmyang.wechatgpt.utils.SignatureUtils;
import cn.cmyang.wechatgpt.utils.XMLConverUtils;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;


@Slf4j
@Service
public class WeChatServiceImpl implements WeChatService {

    private static final Map<String, Integer> messageCountMap = new ConcurrentHashMap<>();

    @Autowired
    private WechatMpConfig wechatMpConfig;

    @Autowired
    private RedisCacheUtils redisCacheUtils;

    @Autowired
    private ChatgptService chatgptService;

    @Override
    public String checkWeChatSignature(WeChatBean weChatBean) {
        String hashSignature = null;
        if (StringUtils.isBlank(weChatBean.getTimestamp()) || StringUtils.isBlank(weChatBean.getNonce())) {
            return hashSignature;
        }
        hashSignature = SignatureUtils.generateEventMessageSignature(wechatMpConfig.getToken(),
                weChatBean.getTimestamp(), weChatBean.getNonce());
        return hashSignature;
    }

    @Override
    public String onlineReply(WeChatBean weChatBean, String xmlParams) {
        long start = System.currentTimeMillis();
        log.info("获取到用户信息：{}-{}", JSON.toJSONString(weChatBean), xmlParams);
        Map<String, String> params = XMLConverUtils.convertToMap(xmlParams);
        if (params.get("MsgType").equals("event")
                && params.get("Event").equals("subscribe")) {
            String content = "这里是ChatGPT, 一个由OpenAI训练的大型语言模型。\n 因个人订阅号以及OpenAI接口的限制，消息响应速度较慢，请耐心等待，并按照提示获取处理结果。\n 现在，你可以直接发消息与我对话了！";
            return getReplyWeChat(weChatBean.getOpenid(), params.get("ToUserName"), content);
        }
        String msgId = params.get("MsgId");
        final String content = params.get("Content");

        //判断用户今天是否还能对话
        Boolean flag = checkUserChatCount(weChatBean, msgId, content);
        if (flag) {
            return getReplyWeChat(weChatBean.getOpenid(), params.get("ToUserName"), "对不起，您的体验对话已用光！");
        }
        if (!params.get("MsgType").equals("text")) {
            return getReplyWeChat(weChatBean.getOpenid(), params.get("ToUserName"), "暂时只支持接收文本信息");
        }
        messageCountMap.merge(msgId, 1, Integer::sum);
        if (messageCountMap.get(msgId) == 3) {
            messageCountMap.remove(msgId);
            return getReplyWeChat(weChatBean.getOpenid(), params.get("ToUserName"), "结果处理中，请稍后输入\"继续\"查看AI处理结果(只支持查询最新的一条处理记录)");
        }
        //正式的处理逻辑
        final String waitKey = String.format(CommonConstant.CHAT_WX_USER_WAIT_KEY, weChatBean.getOpenid());
        final String msgKey = String.format(CommonConstant.CHAT_WX_USER_MSG_REPLY_KEY, msgId);
        if (StringUtils.isNotBlank(content) && content.equals("继续")) {
            messageCountMap.remove(msgId);
            if (redisCacheUtils.hasKey(waitKey)) {
                Object o = redisCacheUtils.getCacheObject(waitKey);
                Integer contentLength = getByteSize(String.valueOf(o));
                if (contentLength < 2048) {
                    redisCacheUtils.deleteObject(waitKey);
                    return getReplyWeChat(weChatBean.getOpenid(), params.get("ToUserName"), String.valueOf(o));
                } else {
                    String replyContent = String.valueOf(o).substring(0, 580);
                    redisCacheUtils.setCacheObject(waitKey, String.valueOf(o).replace(replyContent, ""), 60, TimeUnit.MINUTES);
                    return getReplyWeChat(weChatBean.getOpenid(), params.get("ToUserName"), replyContent + "\n  (公众号回复字符限制，输入\"继续\"查看后续内容)");
                }
            } else {
                try {
                    TimeUnit.SECONDS.sleep(2);
                } catch (Exception e) {
                    log.error("", e);
                }
                return getReplyWeChat(weChatBean.getOpenid(), params.get("ToUserName"), "结果处理中，请稍后输入\"继续\"查看AI处理结果(只支持查询最新的一条处理记录)");
            }
        }
        if (!redisCacheUtils.hasKey(msgKey)) {
            redisCacheUtils.setCacheObject(msgKey, "success", 30, TimeUnit.SECONDS);
            new Thread(() -> {
                if (StringUtils.isNotBlank(content)) {
                    redisCacheUtils.deleteObject(waitKey);
                    chatgptService.multiChatStreamToWX(weChatBean.getOpenid(), msgId, content);
                }
            }).start();
        }

        //延迟5秒
        Object o = putOff(msgKey);
        if (null == o) {
            o = redisCacheUtils.getCacheObject(msgKey);
        }
        long time = System.currentTimeMillis() - start;
        if (!"success".equals(String.valueOf(o)) && time < 4900L) {
            redisCacheUtils.deleteObject(Arrays.asList(msgKey, waitKey));
            messageCountMap.remove(msgId);
            return getReplyWeChat(weChatBean.getOpenid(), params.get("ToUserName"), String.valueOf(o));
        }
        return String.valueOf(o);
    }

    private Object putOff(String msgKey) {
        for (int i = 0; i < 5; i++) {
            Object o = redisCacheUtils.getCacheObject(msgKey);
            if ("success".equals(String.valueOf(o))) {
                try {
                    TimeUnit.MILLISECONDS.sleep(1000);
                } catch (Exception e) {
                    log.error("", e);
                }
            } else {
                return o;
            }
        }
        return null;
    }

    private Boolean checkUserChatCount(WeChatBean weChatBean, String msgId, String content) {
        String chatCountKey = String.format(CommonConstant.CHAT_WX_USER_SESSION_RESTRICT_KEY, weChatBean.getOpenid());
        if (!messageCountMap.containsKey(msgId) && !"继续".equals(content)) {
            Long count = redisCacheUtils.getIncrement(chatCountKey);
            if (null != count && count > 10 && count < 1000) {
                return Boolean.TRUE;
            }
        }
        return Boolean.FALSE;
    }

    private Integer getByteSize(String content) {
        Integer size = 0;
        if (null != content) {
            try {
                // 汉字采用utf-8编码时占3个字节
                size = content.getBytes("UTF-8").length;
            } catch (Exception e) {
                log.error("", e);
            }
        }
        return size;
    }

    private String getReplyWeChat(String openId, String toUserName, String content) {
        log.info("回复用户：{}", content);
        String reply = "<xml>";
        reply += "<ToUserName><![CDATA[" + openId + "]]></ToUserName>";
        reply += "<FromUserName><![CDATA[" + toUserName + "]]></FromUserName>";
        reply += "<CreateTime>" + new Date().getDate() + "</CreateTime>";
        reply += "<MsgType><![CDATA[text]]></MsgType>";
        reply += "<Content><![CDATA[" + content + "]]></Content>";
        reply += "</xml>";
        return reply;
    }

}
