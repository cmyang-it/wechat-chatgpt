package cn.cmyang.wechatgpt.service.impl;

import cn.cmyang.wechatgpt.bean.WeChatBean;
import cn.cmyang.wechatgpt.common.CommonConstant;
import cn.cmyang.wechatgpt.config.WechatMpConfig;
import cn.cmyang.wechatgpt.manager.AsyncManager;
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
import java.util.TimerTask;
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
            String content = "这里是ChatGPT, 一个由OpenAI训练的大型语言模型，当前模型为 gpt-4o。\n 因个人订阅号以及OpenAI接口的限制，消息响应速度较慢，请耐心等待，并按照提示获取处理结果。\n 现在，你可以直接发消息与我对话了！";
            return getReplyWeChat(weChatBean.getOpenid(), params.get("ToUserName"), content);
        }
        //微信推送的参数
        String msgId = params.get("MsgId");
        final String content = params.get("Content");
        String toUserName = params.get("ToUserName");

        if (!params.get("MsgType").equals("text")) {
            return getReplyWeChat(weChatBean.getOpenid(), toUserName, "暂时只支持接收文本信息");
        }

        //缓存每个对话的微信调用次数
        messageCountMap.merge(msgId, 1, Integer::sum);
        //当前是微信的第几次调用
        final Integer currentMsgCount = messageCountMap.get(msgId);

        //判断用户今天是否还能对话
        Boolean flag = checkUserChatCount(weChatBean, msgId, content);
        if (flag) {
            messageCountMap.remove(msgId);
            return getReplyWeChat(weChatBean.getOpenid(), toUserName, "对不起，您的体验对话已用光！");
        }

        //默认返回success
        String success = "success";

        //正式的处理逻辑
        final String waitKey = String.format(CommonConstant.CHAT_WX_USER_WAIT_KEY, weChatBean.getOpenid());
        if (StringUtils.isNotBlank(content) && content.equals("继续")) {
            while (messageCountMap.containsKey(msgId)) {
                String replay = checkMessageCountMap(msgId, currentMsgCount, start, toUserName, weChatBean);
                if (null != replay) {
                    return replay;
                }
                if (redisCacheUtils.hasKey(waitKey)) {
                    Object o = redisCacheUtils.getCacheObject(waitKey);
                    Integer contentLength = getByteSize(String.valueOf(o));
                    messageCountMap.remove(msgId);
                    if (contentLength < 2048) {
                        redisCacheUtils.deleteObject(waitKey);
                        return getReplyWeChat(weChatBean.getOpenid(), toUserName, String.valueOf(o));
                    } else {
                        String replyContent = String.valueOf(o).substring(0, 580);
                        redisCacheUtils.setCacheObject(waitKey, String.valueOf(o).replace(replyContent, ""), 60, TimeUnit.MINUTES);
                        return getReplyWeChat(weChatBean.getOpenid(), toUserName, replyContent + "\n  (公众号回复字符限制，输入\"继续\"查看后续内容)");
                    }
                }
            }
            return success;
        }

        //调用chatgpt
        final String msgKey = String.format(CommonConstant.CHAT_WX_USER_MSG_REPLY_KEY, msgId);
        if (!redisCacheUtils.hasKey(msgKey)) {
            redisCacheUtils.setCacheObject(msgKey, success, 30, TimeUnit.SECONDS);
            AsyncManager.me().execute(new TimerTask() {
                @Override
                public void run() {
                    if (StringUtils.isNotBlank(content)) {
                        redisCacheUtils.deleteObject(waitKey);
                        chatgptService.singleChatStreamToWX(weChatBean.getOpenid(), msgId, content);
                    }
                }
            });
        }

        while (messageCountMap.containsKey(msgId)) {
            String replay = checkMessageCountMap(msgId, currentMsgCount, start, toUserName, weChatBean);
            if (null != replay) {
                return replay;
            }
            Object o = redisCacheUtils.getCacheObject(msgKey);
            if (!success.equals(String.valueOf(o))) {
                messageCountMap.remove(msgId);
                redisCacheUtils.deleteObject(Arrays.asList(msgKey, waitKey));
                return getReplyWeChat(weChatBean.getOpenid(), toUserName, String.valueOf(o));
            }
        }
        log.info("回复用户：{}", success);
        return success;
    }

    private String checkMessageCountMap(String msgId, final Integer currentMsgCount, long start, String toUserName, WeChatBean weChatBean) {
        if (currentMsgCount < 3 && !currentMsgCount.equals(messageCountMap.get(msgId))) {
            log.info("回复用户：{}", "success");
            return "success";
        }
        if (currentMsgCount == 3 && (System.currentTimeMillis() - start) >= 4000) {
            return getReplyWeChat(weChatBean.getOpenid(), toUserName, "结果处理中，请稍后输入\"继续\"查看AI处理结果(只支持查询最新的一条处理记录)");
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
