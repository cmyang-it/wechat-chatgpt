package cn.cmyang.wechatgpt.service;


import cn.cmyang.wechatgpt.bean.WebChatBean;

public interface ChatgptService {

    void multiChatStreamToWX(String openId, String msgId, String content);

    void singleChatStreamToWX(String openId, String msgId, String content);


}
