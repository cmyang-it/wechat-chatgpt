package cn.cmyang.wechatgpt.bean;

import lombok.Data;

import java.io.Serializable;

@Data
public class CacheMessageBean implements Serializable {

    private String sessionId;

    private String role;

    private String content;

    public CacheMessageBean() {}

    public CacheMessageBean(String sessionId, String role, String content) {
        this.sessionId = sessionId;
        this.role = role;
        this.content = content;
    }

}
