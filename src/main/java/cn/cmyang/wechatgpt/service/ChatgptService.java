package cn.cmyang.wechatgpt.service;


public interface ChatgptService {

    void multiChatStreamToWX(String openId, String msgId, String content);

    void singleChatStreamToWX(String openId, String msgId, String content);

    void generateImage(String openId, String msgId, String prompt);
}
