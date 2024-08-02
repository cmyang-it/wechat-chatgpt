package cn.cmyang.wechatgpt.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Setter
@Getter
@Component
@ConfigurationProperties(prefix = "chatgpt")
public class ChatGPTConfig {

    private List<String> apiKey;

    private String baseUrl;

    private String model;

    private Integer maxTokens;

    private Integer messageSize;

    private String callWord;

    public void setMessageSize(Integer messageSize) {
        this.messageSize = messageSize;
        if (null != messageSize && messageSize > 5) {
            this.messageSize = 5;
        }
    }

}
