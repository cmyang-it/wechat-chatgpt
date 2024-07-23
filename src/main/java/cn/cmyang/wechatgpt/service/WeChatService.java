package cn.cmyang.wechatgpt.service;

import cn.cmyang.wechatgpt.bean.WeChatBean;


public interface WeChatService {

    String onlineReply(WeChatBean weChatBean, String xmlParams);

}
