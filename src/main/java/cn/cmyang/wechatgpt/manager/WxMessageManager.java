package cn.cmyang.wechatgpt.manager;

import cn.cmyang.wechatgpt.common.annotation.WxMessageType;
import cn.cmyang.wechatgpt.common.enums.WxMessageTypeEnum;
import cn.cmyang.wechatgpt.handler.WxMessageHandler;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Component
public class WxMessageManager implements ApplicationContextAware {

    private static final Map<WxMessageTypeEnum, Class<WxMessageHandler>> WX_MESSAGE_TYPE_ENUM_CLASS_MAP = new HashMap<>();

    private final ApplicationContext context;

    public WxMessageManager(ApplicationContext context) {
        this.context = context;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        applicationContext.getBeansWithAnnotation(WxMessageType.class)
                .entrySet()
                .iterator()
                .forEachRemaining(stringObjectEntry -> {
                    Class<WxMessageHandler> clazz = (Class<WxMessageHandler>) stringObjectEntry.getValue().getClass();
                    WxMessageTypeEnum wxMessageTypeEnum = clazz.getAnnotation(WxMessageType.class).value();
                    WX_MESSAGE_TYPE_ENUM_CLASS_MAP.put(wxMessageTypeEnum, clazz);
                });
        log.debug("{}", JSON.toJSONString(WX_MESSAGE_TYPE_ENUM_CLASS_MAP));
    }


    /**
     * 获取微信消息处理器
     * @param msgType 消息类型
     * @return 处理器
     */
    public WxMessageHandler getMessageHandler(String msgType) {
        WxMessageTypeEnum typeEnum = WxMessageTypeEnum.getByType(msgType);
        Class<WxMessageHandler> clazz = WX_MESSAGE_TYPE_ENUM_CLASS_MAP.get(typeEnum);
        if (Objects.nonNull(clazz)) {
            return context.getBean(clazz);
        }
        clazz = WX_MESSAGE_TYPE_ENUM_CLASS_MAP.get(WxMessageTypeEnum.DEFAULT);
        return context.getBean(clazz);
    }

}
