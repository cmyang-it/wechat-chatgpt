package cn.cmyang.wechatgpt.handler.impl;

import cn.cmyang.wechatgpt.common.annotation.WxMessageType;
import cn.cmyang.wechatgpt.common.enums.WxMessageTypeEnum;
import cn.cmyang.wechatgpt.handler.WxMessageHandler;
import me.chanjar.weixin.mp.bean.message.WxMpXmlMessage;
import me.chanjar.weixin.mp.bean.message.WxMpXmlOutTextMessage;
import org.springframework.stereotype.Service;

@Service
@WxMessageType(WxMessageTypeEnum.DEFAULT)
public class DefaultMessageHandler implements WxMessageHandler {

    @Override
    public String handlerMessage(WxMpXmlMessage mpMessage) {
        WxMpXmlOutTextMessage textMessage = genTextMessage(mpMessage);
        textMessage.setContent("目前只支持文本信息！");
        return textMessage.toXml();
    }

}
