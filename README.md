<div align="center">
  <h1>🌸 探花交友平台</h1>
  <strong>一款基于大数据智能推荐的陌生人社交平台</strong>

  <em>支持即时通讯、动态分享、附近的人查找、智能匹配等核心功能</em>
</div>

<p align="center">
  <a href="http://mail.qq.com/cgi-bin/qm_share?t=qm_mailme&email=f0hLSE9OTkdHTT8ODlEcEBI">
    <img alt="邮箱联系" src="https://img.shields.io/badge/mail-747011882@qq.com-red">
  </a> 

  <a href="#">
    <img alt="JDK版本" src="https://img.shields.io/badge/JDK-1.8%2B-brightgreen">
  </a> 
  <a href="#">
    <img alt="Maven版本" src="https://img.shields.io/badge/maven-3.6.3%2B-yellowgreen">
  </a>
  <a href="#">
    <img alt="开源协议" src="https://img.shields.io/badge/license-Apache-green">
  </a> 
</p>


## 🧩 一、项目简介

探花交友是一款面向年轻人的陌生人社交平台，支持以下核心功能：

- 📍 搜索附近的人  
- 💬 实时在线聊天（集成环信 IM）  
- 📰 查看好友动态  
- 🤖 基于 Spark + MLlib 的智能推荐系统  
- 📷 支持图文、小视频上传与展示  
- 📸 虹软人脸识别技术应用  

用户可以通过兴趣标签、地理位置、行为偏好等多维度数据进行智能匹配，提升交友质量与粘性。


### 🔗 在线体验  
🔗 [PC端后台管理地址](https://tanhua-admin.itheima.net/#/login)  
👉 推荐使用 Chrome 浏览器访问


---

## 📷 平台截图预览

<div align="center">

![屏幕截图1](https://broadscope-dialogue-new.oss-cn-beijing.aliyuncs.com/output/20250516/810edfcbc3a5382a02eb264b72834b1b.png?Expires=1778924223&OSSAccessKeyId=LTAI5tL97mBYzVcjkG1cUyin&Signature=io42D%2ForOXIAWmPSoGVYN%2BySGAk%3D)
![屏幕截图2](https://broadscope-dialogue-new.oss-cn-beijing.aliyuncs.com/output/20250516/8cac6dff264e496a0372fbd3d448a954.png?Expires=1778924224&OSSAccessKeyId=LTAI5tL97mBYzVcjkG1cUyin&Signature=J9oCy8G7qAP7M%2Bb617K3jlvXcpI%3D)
![屏幕截图3](https://broadscope-dialogue-new.oss-cn-beijing.aliyuncs.com/output/20250516/2d62c85e34bc30e873d0449565c73102.png?Expires=1778924224&OSSAccessKeyId=LTAI5tL97mBYzVcjkG1cUyin&Signature=sRt%2FH6dUf5GG75bpv39ebkce2q8%3D)
![屏幕截图4](https://broadscope-dialogue-new.oss-cn-beijing.aliyuncs.com/output/20250516/52977066b68b611f018ca183d9e82c93.png?Expires=1778924224&OSSAccessKeyId=LTAI5tL97mBYzVcjkG1cUyin&Signature=1rE0oNVYD4K4A%2B1FxjVlG6M7%2Fho%3D)
![屏幕截图5](https://broadscope-dialogue-new.oss-cn-beijing.aliyuncs.com/output/20250516/bac9dc6d5b85949d8c7df0663d1dc55a.png?Expires=1778924224&OSSAccessKeyId=LTAI5tL97mBYzVcjkG1cUyin&Signature=Ow8bcWVXWyIZz6fi4ef6LWN8jro%3D)

</div>

---

## 🎯 二、项目背景

随着移动互联网的发展，社交需求从熟人关系逐渐延伸至陌生人领域。探花交友平台顺应趋势，聚焦年轻群体，满足其在情感交流、兴趣分享、拓展社交圈等方面的需求。

---

## 👥 三、目标用户群体

| 用户类型 | 特征描述 |
|----------|-----------|
| **上班群体** | 热衷分享生活、建立人设，通过社交扩大交际圈 |
| **学生群体** | 追求个性表达，寻找志同道合的朋友 |
| **文艺群体** | 有独特爱好，希望通过分享结识同类 |
| **沟通弱势群体** | 渴望有效且安全的社交方式解决沟通难题 |

---

## ⚙️ 四、技术方案

### 后端架构
- Spring Boot + SpringMVC + MyBatis + MyBatis Plus
- Dubbo 微服务框架
- Redis 缓存集群
- MongoDB 海量数据存储
- Elasticsearch Geo 查询实现「附近的人」
- Spark + MLlib 构建智能推荐引擎
- RocketMQ 异步消息队列
- 第三方服务：
  - 环信 IM：即时通讯模块
  - 阿里云 OSS：文件存储
  - CDN：加速静态资源加载
  - 虹软开放平台：人脸识别功能

---

### 核心技术解决方案

| 功能模块 | 技术方案 |
|----------|-----------|
| 附近的人 | Elasticsearch geo 地理位置查询 |
| 智能推荐 | Spark + MLlib 构建推荐模型 |
| 海量数据存储 | MongoDB 存储用户信息和动态数据 |
| 小视频处理 | 分布式文件系统 + CDN 加速 |
| 人脸识别 | 虹软 SDK 集成人脸检测与识别 |

---

### ✨ 技术亮点

- 使用 **Elasticsearch geo** 实现精准的地理定位搜索  
- 基于 **Spark + MLlib** 的协同过滤算法实现个性化推荐  
- 利用 **CDN** 提升静态资源与视频加载速度  
- 使用 **MongoDB** 解决海量非结构化数据存储问题  
- 采用 **Apache Dubbo** 实现微服务架构解耦  
- **Redis 集群** 支撑高并发缓存操作  
- **RocketMQ** 保证异步任务的可靠性与高性能  

---

## 📁 五、项目源码 & 社区支持

### 🔗 项目链接 & 社区支持  
![微信公众号二维码](docs/心飞为你飞.jpg)  
🚀 项目地址：[https://gitee.com/itxinfei/mytanhua](https://gitee.com/itxinfei/mytanhua)  
👥 QQ交流群：[661543188](https://qm.qq.com/cgi-bin/qm/qr?k=gNgch-wCkfUu-QbI7DZSudrax2BN7vY0&jump_from=webapi&authKey=QHSRnxQvu+h5S3AXGn/DSHrVPiFQAYEk6bSlCE1lS276SFjQAUagV4FG7bHf0OSM)  
📧 邮箱支持：[747011882@qq.com](http://mail.qq.com/cgi-bin/qm_share?t=qm_mailme&email=f0hLSE9OTkdHTT8ODlEcEBI)  
