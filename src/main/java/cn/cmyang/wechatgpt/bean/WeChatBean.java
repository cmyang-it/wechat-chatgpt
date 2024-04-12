package cn.cmyang.wechatgpt.bean;

import lombok.Data;

@Data
public class WeChatBean {

    private String signature;
    private String timestamp;
    private String nonce;
    private String echostr;
    private String openid;

}
