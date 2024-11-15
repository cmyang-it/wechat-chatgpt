package cn.cmyang.wechatgpt.service;


public interface ChatgptService {

    void chatStream(String openId, String content);

    void generateImage(String openId, String msgId, String prompt);

}
