package cn.cmyang.wechatgpt.common.enums;

import lombok.Getter;

/**
 * 消息类型
 */
@Getter
public enum WxMessageTypeEnum {

    EVENT("event"),

    EVENT_SUBSCRIBE("subscribe"),

    TEXT("text"),

    IMAGE("image"),

    GEN_IMAGE("gen_image"),

    CUSTOM("custom"),

    DEFAULT("default"),

    ;

    WxMessageTypeEnum(String type) {
        this.type = type;
    }

    private String type;

    public static WxMessageTypeEnum getByType(String type) {
        for (WxMessageTypeEnum wxMessageTypeEnum : WxMessageTypeEnum.values()) {
            if (wxMessageTypeEnum.type.equals(type)) {
                return wxMessageTypeEnum;
            }
        }
        return WxMessageTypeEnum.DEFAULT;
    }
}
