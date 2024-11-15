package cn.cmyang.wechatgpt.service.impl;

import cn.cmyang.wechatgpt.common.CommonConstant;
import cn.cmyang.wechatgpt.common.enums.WxMessageTypeEnum;
import cn.cmyang.wechatgpt.config.GenImageConfig;
import cn.cmyang.wechatgpt.handler.WxMessageHandler;
import cn.cmyang.wechatgpt.manager.WxMessageManager;
import cn.cmyang.wechatgpt.service.WeChatService;
import lombok.extern.slf4j.Slf4j;
import me.chanjar.weixin.mp.bean.message.WxMpXmlMessage;
import me.chanjar.weixin.mp.util.xml.XStreamTransformer;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


@Slf4j
@Service
public class WeChatServiceImpl implements WeChatService {

    @Autowired
    private GenImageConfig config;

    @Autowired
    private WxMessageManager messageManager;

    /**
     * 被动回复微信公众号消息
     * @param xmlParams 微信消息
     * @return 被动回复
     */
    @Override
    public String onlineReply(String xmlParams) {
        log.info("微信消息：{}", xmlParams);
        WxMpXmlMessage mpMessage = XStreamTransformer.fromXml(WxMpXmlMessage.class, xmlParams);
        mpMessage.setOpenId(mpMessage.getFromUser());

        String msgType = WxMessageTypeEnum.DEFAULT.getType();
        //订阅
        if (WxMessageTypeEnum.EVENT_SUBSCRIBE.getType().equals(mpMessage.getEvent())) {
            msgType = WxMessageTypeEnum.EVENT_SUBSCRIBE.getType();
            //继续
        } else if (StringUtils.isNotBlank(mpMessage.getContent())
                && mpMessage.getContent().equals(CommonConstant.RESUME)) {
            msgType = WxMessageTypeEnum.RESUME.getType();
            //绘图
        } else if (StringUtils.isNotBlank(mpMessage.getContent())
                && mpMessage.getContent().startsWith(config.getMessagePrefix())) {
            msgType = WxMessageTypeEnum.GEN_IMAGE.getType();
            //普通对话文本消息
        } else if (StringUtils.isNotBlank(mpMessage.getContent())
                && mpMessage.getMsgType().equals(WxMessageTypeEnum.TEXT.getType())) {
            msgType = WxMessageTypeEnum.TEXT.getType();
        }
        WxMessageHandler messageHandler = messageManager.getMessageHandler(msgType);
        return messageHandler.handlerMessage(mpMessage);
    }

}
