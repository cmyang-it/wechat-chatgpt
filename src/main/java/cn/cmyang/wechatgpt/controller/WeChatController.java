package cn.cmyang.wechatgpt.controller;

import cn.cmyang.wechatgpt.bean.WeChatBean;
import cn.cmyang.wechatgpt.service.WeChatService;
import me.chanjar.weixin.mp.api.WxMpService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/wechat")
public class WeChatController {

    @Autowired
    private WeChatService weChatService;

    @Autowired
    private WxMpService wxMpService;

    @GetMapping("")
    public ResponseEntity<Object> checkSignature(WeChatBean weChatBean) {
        //验证是否为微信消息
        if (!wxMpService.checkSignature(weChatBean.getTimestamp(), weChatBean.getNonce(), weChatBean.getSignature())) {
            return ResponseEntity.ok("非法数据");
        }
        //微信公众号接口认证
        if (StringUtils.isNotBlank(weChatBean.getEchostr())) {
            return ResponseEntity.ok(weChatBean.getEchostr());
        }
        return ResponseEntity.ok(null);
    }

    @PostMapping(value = "", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<Object> wechatMessage(WeChatBean weChatBean, @RequestBody String xmlParams) {
        //验证是否为微信消息
        if (!wxMpService.checkSignature(weChatBean.getTimestamp(), weChatBean.getNonce(), weChatBean.getSignature())) {
            return ResponseEntity.ok("非法数据");
        }
        return ResponseEntity.ok(weChatService.onlineReply(xmlParams));
    }

}
