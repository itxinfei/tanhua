import { defineStore } from "pinia";
import { ref, computed } from "vue";
import { getToken, setToken, removeToken } from "@/utils/request";
import { login, sendSmsCode } from "@/api/auth";
import { getUserProfile } from "@/api/mine";

// 用户信息类型
export interface UserInfo {
  id: number | string;
  nickname: string;
  avatar: string;
  gender: string;
  age: number;
  city: string;
  signature: string;
  verified: boolean; // 实名认证
  vip: boolean; // 会员
  photoAlbum: string[]; // 照片墙
  completeness: number; // 资料完整度 0-100
}

export const useUserStore = defineStore("user", () => {
  // ===== state =====
  const token = ref<string>(getToken());
  const userInfo = ref<UserInfo | null>(null);
  const isLogin = computed(() => !!token.value);

  // ===== actions =====
  /** 发送短信验证码 */
  async function sendCode(phone: string): Promise<void> {
    await sendSmsCode(phone);
  }

  /** 手机号+验证码登录 */
  async function doLogin(phone: string, code: string): Promise<void> {
    const res = await login(phone, code);
    token.value = res.token;
    setToken(res.token);
    userInfo.value = res.user;
  }

  /** 退出登录 */
  function logout(): void {
    token.value = "";
    userInfo.value = null;
    removeToken();
    uni.reLaunch({ url: "/pages/auth/login" });
  }

  /**
   * 全量登出（退出登录/注销账号统一入口）：
   * 清空各业务 store + 断开 WuKongIM 连接 + 清 token 并回登录页。
   * 缺一步都会造成换账号后残留旧账号的会话/数据
   */
  async function signOutAll(): Promise<void> {
    const [{ useMatchStore }, { useChatStore }, { useCircleStore }, { useSystemStore }, im] =
      await Promise.all([
        import("@/store/match"),
        import("@/store/chat"),
        import("@/store/circle"),
        import("@/store/system"),
        import("@/utils/wukongim"),
      ]);
    useMatchStore().clear();
    useChatStore().clear();
    useCircleStore().clear();
    useSystemStore().clear();
    try {
      await im.logoutIM();
    } catch {
      // IM 登出失败不阻断本地清理
    }
    logout();
  }

  /** 更新本地用户信息（编辑资料后同步） */
  function updateUserInfo(partial: Partial<UserInfo>): void {
    if (userInfo.value) {
      userInfo.value = { ...userInfo.value, ...partial };
    }
  }

  /** 从后端拉取最新用户信息（保留本地无后端字段的旧值） */
  async function fetchUserInfo(): Promise<void> {
    const profile = await getUserProfile();
    if (!profile) return;
    userInfo.value = {
      id: profile.id ?? userInfo.value?.id ?? 0,
      nickname: profile.nickname || userInfo.value?.nickname || "",
      avatar: profile.avatar || userInfo.value?.avatar || "",
      gender: profile.gender || userInfo.value?.gender || "",
      age: Number(profile.age) || userInfo.value?.age || 0,
      city: profile.city || userInfo.value?.city || "",
      signature: profile.signature || userInfo.value?.signature || "",
      verified: userInfo.value?.verified || false,
      vip: userInfo.value?.vip || false,
      photoAlbum: userInfo.value?.photoAlbum || [],
      completeness: userInfo.value?.completeness || 0,
    };
  }

  return {
    token,
    userInfo,
    isLogin,
    sendCode,
    doLogin,
    logout,
    signOutAll,
    updateUserInfo,
    fetchUserInfo,
  };
});