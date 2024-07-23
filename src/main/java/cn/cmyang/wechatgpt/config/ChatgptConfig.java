package cn.cmyang.wechatgpt.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConfigurationProperties(prefix = "chatgpt")
public class ChatgptConfig {

    private String model;

    private String genImageModel;

    private List<String> apiKey;

    private String baseUrl;


    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getGenImageModel() {
        return genImageModel;
    }

    public void setGenImageModel(String genImageModel) {
        this.genImageModel = genImageModel;
    }

    public List<String> getApiKey() {
        return apiKey;
    }

    public void setApiKey(List<String> apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

}
