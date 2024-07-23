package cn.cmyang.wechatgpt.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "gen-image")
public class GenImageConfig {

    private String genImageMessagePrefix;

    private String genImageRedisPrefix;

    private Integer genImageRestrictTime;

    private String genImageStyle;

    private String genImageSize;

    private String genImageResultType;

}
