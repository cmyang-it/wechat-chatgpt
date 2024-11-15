package cn.cmyang.wechatgpt.handler.impl;

import cn.cmyang.wechatgpt.common.annotation.WxMessageType;
import cn.cmyang.wechatgpt.common.enums.WxMessageTypeEnum;
import cn.cmyang.wechatgpt.config.ChatGPTConfig;
import cn.cmyang.wechatgpt.handler.WxMessageHandler;
import me.chanjar.weixin.mp.bean.message.WxMpXmlMessage;
import me.chanjar.weixin.mp.bean.message.WxMpXmlOutTextMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@WxMessageType(WxMessageTypeEnum.EVENT_SUBSCRIBE)
public class SubscribeMessageHandler implements WxMessageHandler {

    @Autowired
    private ChatGPTConfig config;

    private static final String SUBSCRIBE_CONTENT = "感谢关注，这里是ChatGPT, 一个由OpenAI训练的大型语言模型。\n" +
            "1. 直接发送消息到公众号即可开启对话（当前对话模型：%s）。\n" +
            "2. 消息前缀【画】使用文生图模型，例如：画一个机器人。\n" +
            "(因个人订阅号以及OpenAI接口的限制，消息响应速度不稳定，请耐心等待，并按照提示获取处理结果。)";

    @Override
    public String handlerMessage(WxMpXmlMessage mpMessage) {
        WxMpXmlOutTextMessage textMessage = genTextMessage(mpMessage);
        textMessage.setContent(String.format(SUBSCRIBE_CONTENT, config.getModel()));
        return textMessage.toXml();
    }
}
