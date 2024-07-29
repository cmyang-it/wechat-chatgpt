package cn.cmyang.wechatgpt.handler;


import cn.cmyang.wechatgpt.common.enums.WxMessageTypeEnum;
import me.chanjar.weixin.mp.bean.message.WxMpXmlMessage;
import me.chanjar.weixin.mp.bean.message.WxMpXmlOutImageMessage;
import me.chanjar.weixin.mp.bean.message.WxMpXmlOutTextMessage;

public interface WxMessageHandler {

    String handlerMessage(WxMpXmlMessage mpMessage);

    default WxMpXmlOutTextMessage genTextMessage(WxMpXmlMessage message) {
        WxMpXmlOutTextMessage textMessage = new WxMpXmlOutTextMessage();
        textMessage.setFromUserName(message.getToUser());
        textMessage.setToUserName(message.getFromUser());
        textMessage.setCreateTime(System.currentTimeMillis() / 1000L);
        textMessage.setMsgType(WxMessageTypeEnum.TEXT.getType());
        return textMessage;
    }

    default WxMpXmlOutImageMessage genImageMessage(WxMpXmlMessage message) {
        WxMpXmlOutImageMessage imageMessage = new WxMpXmlOutImageMessage();
        imageMessage.setFromUserName(message.getToUser());
        imageMessage.setToUserName(message.getFromUser());
        imageMessage.setCreateTime(System.currentTimeMillis() / 1000L);
        imageMessage.setMsgType(WxMessageTypeEnum.IMAGE.getType());
        return imageMessage;
    }

}
