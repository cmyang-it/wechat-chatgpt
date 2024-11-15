package cn.cmyang.wechatgpt.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "gen-image")
public class GenImageConfig {

    private String model;

    private String messagePrefix;

    private Integer restrictTime;

    private String style;

    private String size;

}
