### 探花交友
![](th.jpg)

<p align="center">
  <a href="https://gitee.com/itxinfei">
    <img alt="code style" src="https://img.shields.io/badge/心飞为你飞-https%3A%2F%2Fgitee.com%2Fitxinfei-green">
  </a> 
  <a href="https://qm.qq.com/cgi-bin/qm/qr?k=9yLlyD1dRBL97xmBKw43zRt0-6xg8ohb&jump_from=webapi">
    <img alt="code style" src="https://img.shields.io/badge/QQ群-863662849-red">
  </a> 
  <a href="http://mail.qq.com/cgi-bin/qm_share?t=qm_mailme&email=f0hLSE9OTkdHTT8ODlEcEBI">
    <img alt="code style" src="https://img.shields.io/badge/mail-747011882@qq.com-red">
  </a> 

  <a href=" ">
    <img alt="code style" src="https://img.shields.io/badge/JDK-1.8%2B-brightgreen">
  </a> 
  <a href=" ">
    <img alt="maven" src="https://img.shields.io/badge/maven-3.6.3%2B-yellowgreen">
  </a>
  <a href=" ">
    <img alt="code style" src="https://img.shields.io/badge/license-Apache-green">
  </a> 
</p>

### 一、项目简介
探花交友是一个陌生人的在线交友平台，在该平台中可以搜索附近的人，查看好友动态，平台还会通过
大数据计算进行智能推荐，通过智能推荐可以找到更加匹配的好友，这样才能增进用户对产品的喜爱
度。探花平台还提供了在线即时通讯功能，可以实时的与好友进行沟通，让沟通随时随地的进行。

![输入图片说明](https://images.gitee.com/uploads/images/2020/1221/130407_27f60ec2_800553.png "屏幕截图.png")

![输入图片说明](https://images.gitee.com/uploads/images/2020/1221/130320_6dabbd99_800553.png "屏幕截图.png")

![输入图片说明](https://images.gitee.com/uploads/images/2020/1221/130330_5cefb9f4_800553.png "屏幕截图.png")

![输入图片说明](https://images.gitee.com/uploads/images/2020/1221/130338_6b84078e_800553.png "屏幕截图.png")

![输入图片说明](https://images.gitee.com/uploads/images/2020/1221/130435_3e2c1914_800553.png "屏幕截图.png")


### 二、项目背景
在线社交是互联网时代的产物，已成为互联网用户的基础需求之一。移动互联网自2003年起快速发展，
促使在线社交逐渐从PC端转移至移动端。移动社交最初以熟人社交为主，以维系熟人关系、共享资源信
息的形式存在。随着人们交友需求的延伸，移动社交开始向陌生人社交、兴趣社交等垂直方向发展，形
式丰富多样。

### 三、目标用户群体
从整体年龄段来看：目前目标用户群体主要以30岁以下为主，其中以18-25岁年龄群体为主要受众人
群。

上班群体： 热衷于通过分享内容或表达“个人情绪”在陌生人面前建立特殊的人设，并借此提升自我
价值扩大自己的交际圈；
学生群体： 追求个性选择，更倾向找到有共同话题的陌生人对象并建立长期的关系，乐于展现自
我；
文艺群体： 拥有自己独特的爱好且拥有特别的个人追求，追求文艺圈子内的交流，希望通过分享结
交更多好友；
沟通弱势群体： 对现有长期保持线上对社交模式表现无力且无效，渴望有更加有效且安全的社交方
式出现，解决目前单调乏味的沟通方式；

### 四、技术方案
后端：
- Spring Boot + SpringMVC + Mybatis + MybatisPlus + Dubbo
- Elasticsearch geo 实现地理位置查询
- MongoDB 实现海量数据的存储
- Redis 数据的缓存
- cdn 加速静态文件的加载
- Spark + MLlib 实现智能推荐
- 第三方服务 环信即时通讯
- 第三方服务 阿里云 OSS

![输入图片说明](https://images.gitee.com/uploads/images/2020/1221/130130_ee91cb12_800553.png "屏幕截图.png")

#### 技术解决方案
- 使用 Elasticsearch geo实现附近的人的解决方案
- 使用 Spark + Mllib实现智能推荐的解决方案
- 使用 MongoDB进行海量数据的存储的解决方案
- 使用采用分布式文件系统存储小视频数据的解决方案
- 使用虹软开放平台进行人脸识别的解决方案
#### 技术亮点
- 采用 Elasticsearch geo实现地理位置查询
- 采用 RocketMQ作为消息服务中间件
- 采用 MongoDB进行海量数据的存储
- 采用 CDN技术加速静态资源以及小视频的加载
- 采用 Apache Dobbo作为微服务架构技术
- 采用 SpringBoot + Mybatis实现系统主架构
- 采用 Redis集群实现缓存的高可用