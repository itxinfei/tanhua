<template>
  <view class="chat-room">
    <!-- 消息列表 -->
    <scroll-view scroll-y class="msg-list" :scroll-into-view="scrollToId">
      <view v-for="msg in messageList" :key="msg.id" :id="'msg-' + msg.id" class="msg-item" :class="{ mine: msg.isMine }">
        <image v-if="!msg.isMine" class="avatar" :src="msg.avatar" mode="aspectFill" />
        <view class="bubble" :class="{ mine: msg.isMine }">
          <text v-if="msg.type === 'text'">{{ msg.content }}</text>
          <image v-else-if="msg.type === 'image'" class="msg-image" :src="msg.content" mode="widthFix" @click="previewImg(msg.content)" />
        </view>
        <image v-if="msg.isMine" class="avatar" :src="myAvatar" mode="aspectFill" />
      </view>
    </scroll-view>

    <!-- 输入栏 -->
    <view class="input-bar safe-area-bottom">
      <input v-model="inputText" class="text-input" placeholder="说点什么..." confirm-type="send" @confirm="sendText" />
      <view class="send-btn" @click="sendText"><text>发送</text></view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { onLoad } from "@dcloudio/uni-app";

const userId = ref("");
const nickname = ref("");
const myAvatar = ref("/static/logo/logo-icon-transparent.png");
const inputText = ref("");
const scrollToId = ref("");

interface Message { id: string; type: string; content: string; isMine: boolean; avatar: string; }
const messageList = ref<Message[]>([]);

function sendText() {
  if (!inputText.value.trim()) return;
  // TODO: 环信 SDK 发送消息
  const msg: Message = { id: Date.now().toString(), type: "text", content: inputText.value, isMine: true, avatar: myAvatar.value };
  messageList.value.push(msg);
  inputText.value = "";
  scrollToId.value = "msg-" + msg.id;
}

function previewImg(url: string) {
  uni.previewImage({ urls: [url] });
}

onLoad((options: any) => {
  userId.value = options?.userId || "";
  nickname.value = options?.nickname || "聊天";
  uni.setNavigationBarTitle({ title: nickname.value });
  // TODO: 环信 SDK 初始化会话，拉取历史消息
});
</script>

<style lang="scss" scoped>
.chat-room { height: 100vh; display: flex; flex-direction: column; background: #f5f5f5; }

.msg-list { flex: 1; padding: 24rpx; }
.msg-item { display: flex; margin-bottom: 24rpx; gap: 16rpx;
  &.mine { flex-direction: row-reverse; }
  .avatar { width: 72rpx; height: 72rpx; border-radius: 12rpx; flex-shrink: 0; }
  .bubble { max-width: 480rpx; padding: 20rpx 24rpx; border-radius: 20rpx; background: #fff; font-size: 28rpx; line-height: 1.5;
    &.mine { background: #ff5e8e; color: #fff; }
    .msg-image { max-width: 400rpx; border-radius: 12rpx; }
  }
}

.input-bar { display: flex; align-items: center; gap: 16rpx; padding: 16rpx 24rpx; background: #fff;
  .text-input { flex: 1; height: 72rpx; background: #f5f5f5; border-radius: 36rpx; padding: 0 28rpx; font-size: 28rpx; }
  .send-btn { background: #ff5e8e; color: #fff; font-size: 26rpx; padding: 12rpx 28rpx; border-radius: 28rpx; }
}
</style>