package cn.cmyang.wechatgpt.common.enums;

/**
 * 消息类型
 */
public enum WxMessageTypeEnum {

    EVENT("event"),

    EVENT_SUBSCRIBE("subscribe"),

    TEXT("text"),

    IMAGE("image"),

    GEN_IMAGE("gen_image"),

    RESUME("resume"),

    CUSTOM("custom"),

    DEFAULT("default"),

    ;

    WxMessageTypeEnum(String type) {
        this.type = type;
    }

    private String type;

    public String getType() {
        return type;
    }

    public static WxMessageTypeEnum getByType(String type) {
        for (WxMessageTypeEnum wxMessageTypeEnum : WxMessageTypeEnum.values()) {
            if (wxMessageTypeEnum.type.equals(type)) {
                return wxMessageTypeEnum;
            }
        }
        return WxMessageTypeEnum.DEFAULT;
    }
}
