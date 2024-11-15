package cn.cmyang.wechatgpt.common;

public class CommonConstant {

    public static final String CHAT_WX_BASE_KEY = "CHAT:WX:";

    public static final String CHAT_WX_CACHE_MESSAGE_KEY = CHAT_WX_BASE_KEY + "MESSAGE:USER:%s";

    public static final String CHAT_WX_USER_WAIT_KEY = CHAT_WX_BASE_KEY + "USER:WAIT:%s";

    public static final String CHAT_WX_USER_RESUME = CHAT_WX_BASE_KEY + "RESUME:%s";

    public static final String GEN_IMAGE_WX_USER_RESTRICT_KEY = "GEN:IMAGE:RESTRICT:%s";

    public static final String CHAT_IMAGE_RESULT_PREFIX = "image_";

    public static final String RESUME = "继续";

    public static final String SUCCESS = "success";

}
