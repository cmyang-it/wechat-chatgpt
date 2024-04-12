package cn.cmyang.wechatgpt.bean;

import lombok.Data;

@Data
public class WebChatBean {

    private String openId;

    private String sessionId;

    private String chatDesc;

    private String content;

}
