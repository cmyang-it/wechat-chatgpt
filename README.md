# 一、说明

**众说周知，微信未经过认证的订阅号在接口权限上面有非常大的限制，例如：只能回复用户消息而不能主动推送；回复消息只能在三次微信推送的15秒内；回复用户消息有字符限制等等。这里主要是为了平衡国内调用ChatGPT接口的速度和微信公众号限制**

- 本项目是微信公众号 订阅号（未认证）通过简单的配置接入到 ChatGPT 中，通过 openAI 接口实现在订阅号聊天界面直接对话进行AI聊天
- 通过设定好的前缀，可以调用绘图模型（`dall-e-2/dall-e-3`）实现文生图功能。
- 如果项目对你有用，请给个`star⭐`谢谢。

> 可以关注下面的公众号免费体验，回复口令可免费获取 ChatGPT 令牌（不限制次数，只能使用`gpt-3-turbo`，用于学习和测试）

![微信订阅号二维码](https://easyimage.cmyang.cn/i/2024/08/01/fslzhe.webp)

# 二、功能

- chatgpt智能对话功能（`gpt-3.5-turbo`、`gpt-4`、`gpt-4o`、`gpt-4o-mini`）等。
- chatgpt文生图模型（`dall-e-2/dall-e-3`），支持设定匹配前缀、绘图参数等。

![对话示例](https://easyimage.cmyang.cn/i/2024/07/23/f8e4j6.webp)

# 三、配置

### 1. 微信公众号后台配置

- a. 点开设置与开发 - 基本配置，启用 服务器配置
- b. 填写服务器地址 `https://自己的域名:端口/wechat`
- c. 填写令牌，选择明文模式
- d. 需要注意的是，要先把服务配置好，启动起来，服务器配置保存的时候需要验证
- e. 需要将部署服务的IP加入到公众号配置的白名单里面（否则 `绘图` 会报错无法将图片上传至临时素材库）

![微信公众号服务器配置](https://easyimage.cmyang.cn/i/2024/06/29/10hjr2u.webp)
### 2. 后端代码配置

> 所有需要修改的配置都有注释，都在 `application-dev.yml` 和 `application-prod.yml` 文件中，请按需选择上线的配置文件

- a. 拉取代码
```bash
git clone https://github.com/cmyang-it/wechat-chatgpt.git
```
- b. 配置`redis`
- c. 配置微信上述填写的令牌
- d. 配置`chatgpt`相关
- e. 配置绘图相关

![修改配置](https://easyimage.cmyang.cn/i/2024/07/29/10kimp9.webp)

# 四、部署

### 1. 直接部署
> 直接部署到linux服务器上，通过systemctl管理
1. 将上述配置都修改好后，执行 `mvn clean package` 会生成 wechat-chatgpt.jar 文件
2. 直接将 wechat-chatgpt.jar 放到目标服务器的 /opt 目录下
3. 将项目的 service 目录下面的 wechatgpt.service 放到 `/lib/systmed/system` 目录下
4. 执行 `systemctl daemon-reload` 和 `systemctl start wechatgpt` 即可启动服务

### 2. 使用docker部署
1. 将上述配置都修改好后，执行 `mvn clean package` 会生成 wechat-chatgpt.jar 文件
2. 执行项目目录下构建命令
```bash
docker build -t wechatgpt:latest .
```
3. 运行容器
```bash
docker run -it -d -p 18080:8080 --restart=always --name wechatgpt --privileged=true  -v /etc/localtime:/etc/localtime:ro wechatgpt:latest
```

# 五、免费令牌获取
1. 关注公众号后，根据提示回复口令即可获取免费的ChatGPT令牌，三日内有效（不限制次数，只能使用`gpt-3-turbo`，可用于学习和测试）。
2. 访问 https://api.xiyangai.cn 注册登录后即可领取`10000`免费额度，创建ChatGPT令牌。

# 🙏 鸣谢 
- chatgpt-java: https://github.com/Grt1228/chatgpt-java 
- WxJava: https://github.com/Wechat-Group/WxJava
