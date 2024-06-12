# 前言

**众说周知，微信未经过认证的订阅号在接口权限上面有非常大的限制，例如：只能回复用户消息而不能主动推送；回复消息只能在三次微信推送的15秒内；回复用户消息有字符限制等等。这里主要是为了平衡国内调用ChatGPT接口的速度和微信公众号限制**

> 本项目是微信公众号 订阅号（未认证）通过简单的配置接入到 ChatGPT 中，通过 openAI 接口实现在订阅号聊天界面直接对话进行AI聊天

> 大家可以关注本人订阅号免费体验 gpt-4o 最新模型，回复 #chatgptkey 可免费获取 ChatGPT 令牌。

![微信订阅号二维码](https://image.cmyang.cn/i/2024/03/28/660508c75724d.jpg)

# 一、配置

### 1. 微信公众号后台配置

- a. 点开设置与开发 - 基本配置，启用 服务器配置
- b. 填写服务器地址 http://自己的域名:端口/wechat
- c. 填写令牌，选择明文模式
- d. 需要注意的是，要先把服务配置好，启动起来，服务器配置保存的时候需要验证

![微信公众号服务器配置](https://image.cmyang.cn/i/2024/03/28/66050890d243a.png)

### 2. 后端代码配置

> 所有需要修改的配置都有注释，都在 application-dev.yml 和 application-prod.yml文件中，请按需选择上线的配置文件

- a. 拉取代码
> git clone https://github.com/cmyang-it/wechatgpt.git
- b. 配置redis
- c. 配置微信上述填写的令牌
- d. 配置chatgpt相关

![修改配置](https://image.cmyang.cn/i/2024/06/04/665eb317f28b0.png)

### 3. 项目部署

- 直接部署到linux服务器上，通过systemctl管理
1. 将上述配置都修改好后，执行 mvn clean package 会生成 wechatgpt.jar 文件
2. 直接将 wechatgpt.jar 放到目标服务器的 /opt 目录下
3. 将项目的 service 目录下面的 wechatgpt.service 放到 /lib/systmed/system目录下
4. 执行 systemctl daemon-reload 和 systemctl start wechatgpt 即可启动服务

- 使用docker部署
1. 将上述配置都修改好后，执行 mvn clean package 会生成 wechatgpt.jar 文件
2. 项目目录下执行 docker build -t wechatgpt:latest .
3. docker run -it -d -p 18080:8080 --restart=always --name wechatgpt --privileged=true  -v /etc/localtime:/etc/localtime:ro wechatgpt:latest

### 4. 免费令牌获取
1. 关注公众号后，根据提示回复 #chatgpt 即可获取免费的ChatGPT令牌，三日内有效。
2. 访问 https://api.xiyangai.cn 注册登录后即可领取免费额度，创建ChatGPT令牌。

# 🙏 鸣谢 
- chatgpt-java: https://github.com/Grt1228/chatgpt-java
