<template>
  <view class="profile-page">
    <!-- 头像：独立垂直居中块，脱离下方表单的左对齐体系 -->
    <view class="avatar-block anim-fade-down">
      <a11y-button
        class="avatar-trigger"
        label="更换头像"
        :disabled="avatarUploading"
        @tap="changeAvatar"
      >
        <view class="avatar-wrap">
          <view
            v-if="!form.avatar"
            class="avatar avatar-placeholder"
            aria-hidden="true"
          >
            <text
              v-if="avatarInitial"
              class="avatar-initial"
            >
              {{ avatarInitial }}
            </text>
            <uni-icons
              v-else
              type="person-filled"
              size="36"
              color="var(--text-hint)"
            />
          </view>
          <smart-image
            v-else
            lazy-load
            class="avatar"
            :src="form.avatar"
            fallback=""
            mode="aspectFill"
            :aria-hidden="true"
          />
          <view
            v-if="avatarUploading"
            class="avatar-loading"
            aria-hidden="true"
          >
            <view class="avatar-spinner" />
          </view>
          <view
            class="avatar-badge"
            aria-hidden="true"
          >
            <uni-icons
              type="camera-filled"
              size="16"
              color="var(--text-inverse)"
            />
          </view>
        </view>
      </a11y-button>
      <text class="avatar-name">
        {{ form.nickname || '未设置昵称' }}
      </text>
      <text class="avatar-account">
        ID: {{ userStore.userInfo?.id || '—' }}
      </text>
    </view>

    <!-- 照片墙 -->
    <view class="section anim-fade-up anim-delay-1">
      <text class="section-title">
        照片墙 ({{ form.photoAlbum.length }}/{{ MAX_ALBUM_COUNT }})
      </text>
      <view class="album-grid">
        <view
          v-for="(img, i) in form.photoAlbum"
          :key="i"
          class="album-item"
        >
          <smart-image
            lazy-load
            class="album-img"
            :src="img"
            mode="aspectFill"
            :aria-label="`照片墙第 ${i + 1} 张`"
          />
          <a11y-button
            class="remove"
            label="删除这张照片"
            @tap="removePhoto(i)"
          >
            <text class="remove-icon">
              ×
            </text>
          </a11y-button>
        </view>
        <a11y-button
          v-if="form.photoAlbum.length < MAX_ALBUM_COUNT"
          class="add-photo"
          label="添加照片"
          @tap="addPhoto"
        >
          <text class="plus">
            +
          </text>
          <text class="add-label">
            {{ form.photoAlbum.length }}/{{ MAX_ALBUM_COUNT }}
          </text>
        </a11y-button>
      </view>
    </view>

    <!-- 基本信息（每项独立保存） -->
    <view class="section anim-fade-up anim-delay-2">
      <!-- 昵称：文本输入 -->
      <a11y-button
        class="form-item"
        label="修改昵称"
        @tap="editField('nickname')"
      >
        <text class="label">
          昵称
        </text>
        <text
          class="value"
          :class="{ unset: !form.nickname }"
        >
          {{ form.nickname || '未设置' }}
        </text>
        <text
          class="arrow"
          aria-hidden="true"
        >
          ›
        </text>
      </a11y-button>

      <!-- 个性签名：文本输入 -->
      <a11y-button
        class="form-item"
        label="修改个性签名"
        @tap="editField('signature')"
      >
        <text class="label">
          签名
        </text>
        <text
          class="value ellipsis"
          :class="{ unset: !form.signature }"
        >
          {{ form.signature || '未设置' }}
        </text>
        <text
          class="arrow"
          aria-hidden="true"
        >
          ›
        </text>
      </a11y-button>

      <!-- 性别：行内 toggle（两个选项保留原位） -->
      <view class="form-item form-item-inline">
        <text class="label">
          性别
        </text>
        <view class="gender-flex">
          <a11y-radio-group
            label="性别"
            :options="GENDER_OPTIONS"
            :model-value="form.gender"
            option-class="gender-chip btn-feedback"
            @update:model-value="onGenderChange"
          >
            <template #option="{ option }">
              <text>{{ option.icon }}</text>
              <text class="option-label">
                {{ option.label }}
              </text>
            </template>
          </a11y-radio-group>
        </view>
      </view>

      <!-- 城市：底部选择器 -->
      <a11y-button
        class="form-item"
        label="修改城市"
        @tap="openPicker('city')"
      >
        <text class="label">
          城市
        </text>
        <text
          class="value"
          :class="{ unset: !form.city }"
        >
          {{ form.city || '未设置' }}
        </text>
        <text
          class="arrow"
          aria-hidden="true"
        >
          ›
        </text>
      </a11y-button>

      <!-- 身高：底部选择器 -->
      <a11y-button
        class="form-item"
        label="修改身高"
        @tap="openPicker('height')"
      >
        <text class="label">
          身高
        </text>
        <text
          class="value"
          :class="{ unset: !form.height }"
        >
          {{ form.height ? form.height + 'cm' : '未设置' }}
        </text>
        <text
          class="arrow"
          aria-hidden="true"
        >
          ›
        </text>
      </a11y-button>

      <!-- 学历：底部选择器 -->
      <a11y-button
        class="form-item"
        label="修改学历"
        @tap="openPicker('education')"
      >
        <text class="label">
          学历
        </text>
        <text
          class="value"
          :class="{ unset: !form.education }"
        >
          {{ form.education || '未设置' }}
        </text>
        <text
          class="arrow"
          aria-hidden="true"
        >
          ›
        </text>
      </a11y-button>

      <!-- 行业：底部选择器 -->
      <a11y-button
        class="form-item"
        label="修改行业"
        @tap="openPicker('industry')"
      >
        <text class="label">
          行业
        </text>
        <text
          class="value"
          :class="{ unset: !form.industry }"
        >
          {{ form.industry || '未设置' }}
        </text>
        <text
          class="arrow"
          aria-hidden="true"
        >
          ›
        </text>
      </a11y-button>
    </view>

    <!-- 底部选择器 -->
    <a11y-picker
      :visible="pickerVisible"
      :title="pickerTitle"
      :options="pickerOptions"
      :model-value="pickerValue"
      :show-input="pickerShowInput"
      :input-placeholder="pickerPlaceholder"
      @update:visible="pickerVisible = $event"
      @confirm="onPickerConfirm"
    />

    <!-- 昵称/签名编辑弹窗：替代 uni.showModal 单行输入，支持多行 + 字数统计 -->
    <a11y-dialog
      :visible="editDialogVisible"
      :title="editDialogTitle"
      :mask-closable="true"
      class="edit-dialog"
      @close="editDialogVisible = false"
    >
      <view class="edit-panel">
        <view class="sheet-grabber" aria-hidden="true" />
        <view class="edit-field-head">
          <text class="edit-field-title">
            {{ editDialogTitle }}
          </text>
          <text class="edit-field-count">
            {{ editDraft.length }} / {{ editMaxLength }}
          </text>
        </view>
        <textarea
          v-model="editDraft"
          class="edit-textarea"
          :placeholder="editPlaceholder"
          :maxlength="editMaxLength"
          aria-label="编辑内容"
        />
        <view class="edit-actions">
          <a11y-button
            variant="text"
            label="取消编辑"
            @tap="editDialogVisible = false"
          >
            <text>取消</text>
          </a11y-button>
          <a11y-button
            variant="primary"
            label="保存"
            @tap="confirmEdit"
          >
            <text>保存</text>
          </a11y-button>
        </view>
      </view>
    </a11y-dialog>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from "vue";
import { onShow } from "@dcloudio/uni-app";
import { useUserStore } from "@/store/user";
import A11yRadioGroup from "@/components/a11y-radio-group.vue";
import A11yButton from "@/components/a11y-button.vue";
import SmartImage from "@/components/smart-image.vue";
import A11yPicker from "@/components/a11y-picker.vue";
import A11yDialog from "@/components/a11y-dialog.vue";
import { getUserProfile, updateUserProfile, uploadImage } from "@/api/mine";
import { success, fail, info, loading, hideLoading, confirm } from "@/utils/ux";

/** 照片墙最大数量 */
const MAX_ALBUM_COUNT = 6;

/** 性别选项配置（表驱动，value 与后端 SexEnum 一致：man/woman） */
const GENDER_OPTIONS = [
  { value: "man", label: "男", icon: "👨", cls: "male" },
  { value: "woman", label: "女", icon: "👩", cls: "female" },
] as const;

/** 城市选项配置（常见城市，支持自定义输入） */
const CITY_OPTIONS = [
  { value: "北京", label: "北京" },
  { value: "上海", label: "上海" },
  { value: "广州", label: "广州" },
  { value: "深圳", label: "深圳" },
  { value: "杭州", label: "杭州" },
  { value: "成都", label: "成都" },
  { value: "武汉", label: "武汉" },
  { value: "西安", label: "西安" },
  { value: "南京", label: "南京" },
  { value: "重庆", label: "重庆" },
  { value: "苏州", label: "苏州" },
  { value: "天津", label: "天津" },
  { value: "长沙", label: "长沙" },
  { value: "郑州", label: "郑州" },
  { value: "青岛", label: "青岛" },
  { value: "大连", label: "大连" },
  { value: "厦门", label: "厦门" },
  { value: "合肥", label: "合肥" },
  { value: "昆明", label: "昆明" },
  { value: "珠海", label: "珠海" },
] as const;

/** 身高选项配置（cm，5cm 一档） */
const HEIGHT_OPTIONS = [
  { value: "150", label: "150cm" },
  { value: "155", label: "155cm" },
  { value: "160", label: "160cm" },
  { value: "165", label: "165cm" },
  { value: "170", label: "170cm" },
  { value: "175", label: "175cm" },
  { value: "180", label: "180cm" },
  { value: "185", label: "185cm" },
  { value: "190", label: "190cm" },
  { value: "195", label: "195cm" },
] as const;

/** 学历选项配置 */
const EDUCATION_OPTIONS = [
  { value: "高中", label: "高中" },
  { value: "大专", label: "大专" },
  { value: "本科", label: "本科" },
  { value: "硕士", label: "硕士" },
  { value: "博士", label: "博士" },
] as const;

/** 行业选项配置 */
const INDUSTRY_OPTIONS = [
  { value: "IT/互联网", label: "IT/互联网" },
  { value: "金融", label: "金融" },
  { value: "教育", label: "教育" },
  { value: "医疗", label: "医疗" },
  { value: "制造业", label: "制造业" },
  { value: "服务业", label: "服务业" },
  { value: "文化传媒", label: "文化传媒" },
  { value: "房地产", label: "房地产" },
  { value: "公务员", label: "公务员" },
  { value: "自由职业", label: "自由职业" },
  { value: "学生", label: "学生" },
  { value: "其他", label: "其他" },
] as const;

type PickerField = "city" | "height" | "education" | "industry";

const pickerConfig: Record<PickerField, { title: string; options: readonly { value: string; label: string }[]; showInput: boolean; placeholder: string; field: string }> = {
  city: { title: "选择城市", options: CITY_OPTIONS, showInput: true, placeholder: "请输入城市名称", field: "city" },
  height: { title: "选择身高", options: HEIGHT_OPTIONS, showInput: false, placeholder: "", field: "height" },
  education: { title: "选择学历", options: EDUCATION_OPTIONS, showInput: false, placeholder: "", field: "education" },
  industry: { title: "选择行业", options: INDUSTRY_OPTIONS, showInput: false, placeholder: "", field: "industry" },
};

const userStore = useUserStore();
const form = ref({
  avatar: "", nickname: "", signature: "", gender: "man",
  city: "", height: "", education: "", industry: "",
  photoAlbum: [] as string[],
});

/** 头像首次上传中：行内 spinner + 阻断重复选择 */
const avatarUploading = ref(false);

/** 空态首字母：无昵称时返回空串，模板回退到中性人像图标 */
const avatarInitial = computed(() => {
  const nickname = String(form.value.nickname || "").trim();
  return nickname ? Array.from(nickname)[0].toUpperCase() : "";
});

// ===== 底部选择器状态 =====
const pickerVisible = ref(false);
const pickerField = ref<PickerField>("city");
const pickerTitle = ref("");
const pickerOptions = ref<readonly { value: string; label: string }[]>([]);
const pickerShowInput = ref(false);
const pickerPlaceholder = ref("");
const pickerValue = ref("");

function openPicker(field: PickerField): void {
  const cfg = pickerConfig[field];
  pickerField.value = field;
  pickerTitle.value = cfg.title;
  pickerOptions.value = cfg.options;
  pickerShowInput.value = cfg.showInput;
  pickerPlaceholder.value = cfg.placeholder;
  pickerValue.value = (form.value as any)[cfg.field] || "";
  pickerVisible.value = true;
}

/** 选择器确认：先提交后端，成功才写本地表单，失败保留旧值（避免界面与后端不一致） */
async function onPickerConfirm(value: string): Promise<void> {
  const field = pickerConfig[pickerField.value].field;
  await saveField(field, value);
  // 仅在保存成功后才同步本地表单；saveField 内部失败时不会抛出，
  // 因此用返回值区分成败
}

/**
 * 单个字段保存到后端（确认即提交，不含照片墙/昵称/签名的归一化字段）
 * @returns true=保存成功（调用方据此同步本地表单）；false=失败（保留旧值）
 */
const savingFields = new Set<string>();

async function saveField(field: string, value: string): Promise<boolean> {
  // 同字段在途锁：快速连点（如性别切换）不发并发 PUT
  if (savingFields.has(field)) return false;
  savingFields.add(field);
  // 后端字段映射：前端 industry → 后端 profession
  const apiField = field === "industry" ? "profession" : field;
  const payload: Record<string, any> = { [apiField]: value };
  try {
    await updateUserProfile(payload);
    // 保存成功才同步 store（失败保留旧值，避免界面与后端不一致）
    const storeField = field === "industry" ? "profession" : field;
    userStore.updateUserInfo({ [storeField]: value } as any);
    (form.value as any)[field] = value;
    return true;
  } catch {
    // request 层统一 toast 错误；本地表单保持旧值
    return false;
  } finally {
    savingFields.delete(field);
  }
}

/** 性别变更：先提交后端，成功才更新本地表单 */
async function onGenderChange(value: string): Promise<void> {
  if (form.value.gender === value) return;
  const ok = await saveField("gender", value);
  if (ok) form.value.gender = value;
}

/**
 * chooseImage fail 统一处理（changeAvatar/addPhoto 共用，禁止各写一份）：
 * errMsg 含 cancel 是用户主动取消，静默不打扰；其余多为相册授权被拒，
 * 走 ux.info 提示，并在 MP 端（openSetting 存在时）引导跳转系统设置页重新授权。
 * 范式对齐 pages/circle/publish.vue 的 handleMediaFail。
 */
function handleChooseImageFail(err: any): void {
  const msg = String(err?.errMsg || "");
  if (msg.includes("cancel")) return;
  info("需要相册权限才能选择图片");
  const openSetting = (uni as any).openSetting;
  if (typeof openSetting === "function") {
    openSetting({});
  }
}

/** 头像上限 2MB（与需求一致；超大图先拦截，避免无效上传） */
const AVATAR_MAX_SIZE = 2 * 1024 * 1024;
const AVATAR_TYPES = ["image/jpeg", "image/png", "image/webp"];
const AVATAR_EXTS = ["jpg", "jpeg", "png", "webp"];

/** 选图后校验：返回错误文案，通过返回 null（部分平台无 type 时按扩展名兜底） */
function validateAvatarFile(file: { size?: number; type?: string }, path: string): string | null {
  if (Number(file.size || 0) > AVATAR_MAX_SIZE) return "图片不能超过 2MB";
  const type = String(file.type || "").toLowerCase();
  const ext = path.split(".").pop()?.toLowerCase() || "";
  const typeOk = type ? AVATAR_TYPES.includes(type) : AVATAR_EXTS.includes(ext);
  return typeOk ? null : "仅支持 JPG/PNG/WebP 图片";
}

/** 更换头像：选图 → 校验 → 上传 MinIO → 保存成功后写入 form */
function changeAvatar(): void {
  if (avatarUploading.value) return;
  uni.chooseImage({
    count: 1,
    sizeType: ["compressed"],
    success: async (res) => {
      const tempPath = res.tempFilePaths[0];
      if (!tempPath) return; // 用户取消
      const file = (res.tempFiles?.[0] || {}) as { size?: number; type?: string };
      const invalid = validateAvatarFile(file, tempPath);
      if (invalid) {
        fail(invalid);
        return;
      }
      avatarUploading.value = true;
      try {
        const result = await uploadImage(tempPath);
        if (result.status === "done" && result.name) {
          // 交由 saveField 在保存成功后写入 form：失败时头像保持旧图，
          // 避免「界面显示新头像但后端仍是旧图」的不一致
          const ok = await saveField("avatar", result.name);
          if (ok) success("头像已更新");
        } else {
          fail("上传失败，请检查网络后重试");
        }
      } catch {
        fail("网络异常，头像未更新，请重试");
      } finally {
        avatarUploading.value = false;
      }
    },
    fail: handleChooseImageFail,
  });
}

/** 添加照片墙图片：逐张上传 MinIO，成功 URL 入数组 */
function addPhoto(): void {
  const remain = MAX_ALBUM_COUNT - form.value.photoAlbum.length;
  if (remain <= 0) { fail("照片墙已满"); return; }
  uni.chooseImage({
    count: remain,
    sizeType: ["compressed"],
    success: async (res) => {
      const tempPaths = res.tempFilePaths || [];
      if (tempPaths.length === 0) return; // 用户取消
      loading("上传中...");
      try {
        let failed = 0;
        const uploaded: string[] = [];
        for (const tempPath of tempPaths) {
          try {
            const result = await uploadImage(tempPath);
            if (result.status === "done" && result.name) {
              uploaded.push(result.name);
            } else {
              failed++;
            }
          } catch {
            failed++;
          }
        }
        // 先提交后端，成功才写入本地数组：与 removePhoto 同口径，
        // 避免保存失败时本地多出「后端没有」的照片
        if (uploaded.length > 0) {
          const next = [...form.value.photoAlbum, ...uploaded];
          try {
            await updateUserProfile({ photoAlbum: JSON.stringify(next) });
            form.value.photoAlbum = next;
            userStore.updateUserInfo({ photoAlbum: [...next] });
          } catch {
            // request 层统一 toast；本地数组保持不变
          }
        }
        if (failed > 0) fail(`${failed} 张照片上传失败，可重新添加`);
      } finally {
        hideLoading();
      }
    },
    fail: handleChooseImageFail,
  });
}

/**
 * 删除照片墙图片：先提交后端，成功才从本地数组移除。
 * 原实现在 splice 后才异步 saveAlbum，失败时本地已删而后端仍在，
 * 且用户返回再进入会看到被删的照片「复活」体验混乱。
 */
async function removePhoto(i: number): Promise<void> {
  const removed = form.value.photoAlbum[i];
  if (!removed) return;
  // 删除是破坏性操作：先确认再提交（对齐 blacklist 移除的确认交互，M-A2）
  const ok = await confirm("确认删除这张照片？");
  if (!ok) return;
  const next = form.value.photoAlbum.filter((_, idx) => idx !== i);
  try {
    await updateUserProfile({ photoAlbum: JSON.stringify(next) });
    form.value.photoAlbum = next;
    userStore.updateUserInfo({ photoAlbum: [...next] });
  } catch {
    // request 层统一 toast 错误；本地数组保持不变
  }
}

/** photoAlbum 兼容 string(JSON)/string[] 两种来源 */
function normalizeAlbum(raw: unknown): string[] {
  if (Array.isArray(raw)) return raw.filter((u): u is string => typeof u === "string");
  if (typeof raw === "string" && raw) {
    try {
      const arr = JSON.parse(raw);
      return Array.isArray(arr) ? arr.filter((u): u is string => typeof u === "string") : [];
    } catch { return []; }
  }
  return [];
}

/** 弹窗编辑文本字段（仅昵称/签名保留） */
const FIELD_MAX_LENGTH: Record<string, number> = { nickname: 20, signature: 50 };
/** 字段显示名（用于弹窗标题/占位） */
const FIELD_LABEL: Record<string, string> = { nickname: "昵称", signature: "个性签名" };

// ===== 编辑弹窗状态 =====
const editDialogVisible = ref(false);
const editFieldKey = ref<string>("");
const editDraft = ref("");
const editSaving = ref(false);

const editDialogTitle = computed(() => FIELD_LABEL[editFieldKey.value] || "编辑");
const editMaxLength = computed(() => FIELD_MAX_LENGTH[editFieldKey.value] || 50);
const editPlaceholder = computed(() => `请输入${editDialogTitle.value}`);

/** 打开编辑弹窗（替代 uni.showModal 单行输入） */
function editField(field: string): void {
  editFieldKey.value = field;
  editDraft.value = String((form.value as any)[field] || "");
  editDialogVisible.value = true;
}

/** 保存编辑内容：先校验，再提交后端，成功才写本地表单 */
async function confirmEdit(): Promise<void> {
  if (editSaving.value) return;
  const field = editFieldKey.value;
  const text = editDraft.value.trim();
  // 昵称/签名不允许置空：静默 return 会让用户以为「点了没反应」，
  // 必须给出明确提示（BUG-04 收口）
  if (!text) {
    fail(`${editDialogTitle.value}不能为空`);
    return;
  }
  const max = editMaxLength.value;
  if (text.length > max) {
    fail(`${editDialogTitle.value}最多 ${max} 个字符`);
    return;
  }
  editSaving.value = true;
  try {
    const ok = await saveField(field, text);
    if (ok) {
      editDialogVisible.value = false;
      success("已保存");
    }
  } finally {
    editSaving.value = false;
  }
}

onShow(async () => {
  if (userStore.userInfo) {
    Object.assign(form.value, userStore.userInfo);
    // store 字段为 profession，表单字段为 industry，Object.assign 不会自动映射
    form.value.industry = userStore.userInfo.profession || "";
    form.value.photoAlbum = normalizeAlbum(userStore.userInfo.photoAlbum);
    return;
  }
  try {
    const profile = await getUserProfile();
    if (profile) {
      form.value.avatar = profile.avatar || form.value.avatar;
      form.value.nickname = profile.nickname || form.value.nickname;
      form.value.signature = profile.signature || form.value.signature;
      form.value.gender = profile.gender || form.value.gender;
      form.value.city = profile.city || form.value.city;
      form.value.height = profile.height || form.value.height;
      form.value.education = profile.education || form.value.education;
      form.value.industry = profile.profession || form.value.industry;
      form.value.photoAlbum = normalizeAlbum(profile.photoAlbum);
    }
  } catch {
    // request 层统一 toast 错误
  }
});
</script>

<style lang="scss" scoped>
.profile-page { min-height: calc(100vh - var(--tab-bar-height, 0px)); background: var(--bg-sub); padding-bottom: calc(var(--tab-bar-height, 50px) + 60rpx); }

/* ===== 头像块：独立垂直居中，与下方左对齐表单解耦 ===== */
.avatar-block { display: flex; flex-direction: column; align-items: center; padding: var(--space-xl) var(--space-lg); background: var(--bg-card);
  .avatar-name { margin-top: var(--space-md); font-size: var(--fs-lg); font-weight: 600; color: var(--text-primary); }
  .avatar-account { margin-top: var(--space-xs); font-size: var(--fs-sm); color: var(--text-hint); }
  .avatar-wrap { position: relative; width: 176rpx; height: 176rpx;
    .avatar { width: 100%; height: 100%; border-radius: var(--radius-pill); }
    .avatar-placeholder { display: flex; align-items: center; justify-content: center; background: var(--bg-icon-soft); }
    .avatar-initial { font-size: 56rpx; font-weight: 600; color: var(--text-secondary); }
    .avatar-loading { position: absolute; top: 0; left: 0; right: 0; bottom: 0; display: flex; align-items: center; justify-content: center; background: rgba(0, 0, 0, 0.35); border-radius: var(--radius-pill); }
    .avatar-spinner { width: 40rpx; height: 40rpx; border: 4rpx solid rgba(255, 255, 255, 0.35); border-top-color: var(--text-inverse); border-radius: var(--radius-pill); animation: avatar-spin 0.8s linear infinite; }
    .avatar-badge { position: absolute; right: 4rpx; bottom: 4rpx; width: 44rpx; height: 44rpx; display: flex; align-items: center; justify-content: center; background: rgba(0, 0, 0, 0.55); border-radius: var(--radius-pill); }
  }
}

@keyframes avatar-spin { to { transform: rotate(360deg); } }

/* ===== 分组 ===== */
.section { background: var(--bg-card); margin-top: var(--space-sm); padding: 8rpx 32rpx;
  .section-title { font-size: 26rpx; color: var(--text-hint); display: block; margin: 20rpx 0; }
}

/* ===== 照片墙 ===== */
.album-grid { display: flex; flex-wrap: wrap; gap: 16rpx; padding-bottom: 24rpx;
  .album-item { position: relative; width: 180rpx; height: 180rpx; border-radius: 16rpx; overflow: visible;
    .album-img { width: 100%; height: 100%; border-radius: 16rpx; border: 2rpx solid var(--border-light); }
    .remove { position: absolute; top: -16rpx; right: -16rpx; width: 56rpx; height: 56rpx; background: rgba(0, 0, 0, 0.55); color: var(--bg-card); border-radius: 50%; display: flex; align-items: center; justify-content: center;
      /* 热区 56rpx：行内紧凑操作（§7.1 sm 例外），照片卡片 180rpx 为主热区 */
      .remove-icon { font-size: 32rpx; line-height: 1; color: var(--text-inverse); }
    }
  }
  .add-photo { width: 180rpx; height: 180rpx; background: var(--bg-sub); border-radius: 16rpx; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 8rpx; border: 2rpx dashed var(--border);
    .plus { font-size: 56rpx; color: var(--text-hint); line-height: 1; }
    .add-label { font-size: 22rpx; color: var(--text-hint); }
  }
}

/* ===== 表单项 ===== */
.form-item { display: flex; align-items: center; padding: 28rpx 0; border-bottom: 1rpx solid var(--border-faint);
  &:last-child { border-bottom: none; }
  .label { width: 120rpx; font-size: 28rpx; color: var(--text-secondary); flex-shrink: 0; }
  .value { flex: 1; font-size: 28rpx; color: var(--text-primary); text-align: right;
    &.unset { color: var(--text-hint); }
  }
  .arrow { font-size: 32rpx; color: var(--text-hint); margin-left: 8rpx; flex-shrink: 0; }
}

/* 性别行内 toggle */
.form-item-inline { align-items: center;
  .label { width: 120rpx; margin-bottom: 0; flex-shrink: 0; }
}

/* 性别 toggle 容器（flex 右对齐） */
.gender-flex { flex: 1; display: flex; justify-content: flex-end; gap: 12rpx;
  :deep(.gender-chip) { display: inline-flex; align-items: center; gap: 8rpx; padding: 12rpx 28rpx; border-radius: 32rpx; font-size: 26rpx; border: 2rpx solid var(--border-light); background: var(--bg-sub); color: var(--text-secondary);
    .option-label { color: inherit; }
    &.male.active { background: linear-gradient(135deg, var(--info-blue), var(--info-blue-soft)); border-color: var(--info-blue); box-shadow: 0 4rpx 14rpx rgba(74, 144, 217, 0.3); color: var(--text-inverse); }
    &.female.active { background: var(--gradient-brand); border-color: var(--color-primary); box-shadow: 0 4rpx 14rpx rgba(var(--color-primary-rgb), 0.3); color: var(--text-inverse); }
  }
}

/* ===== 昵称/签名编辑弹窗 ===== */
.edit-dialog {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: flex;
  align-items: flex-end;
  background: rgba(0, 0, 0, 0.5);
}

.edit-panel {
  width: 100%;
  background: var(--bg-card);
  border-radius: var(--radius-lg) var(--radius-lg) 0 0;
  padding: 16rpx 32rpx calc(32rpx + env(safe-area-inset-bottom, 0px));
  box-sizing: border-box;
}

/* 顶部抓手（纯装饰） */
.sheet-grabber {
  width: 64rpx;
  height: 8rpx;
  border-radius: var(--radius-pill);
  background: var(--border-light);
  margin: 0 auto 24rpx;
}

.edit-field-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 16rpx;
}

.edit-field-title {
  font-size: 30rpx;
  font-weight: 600;
  color: var(--text-primary);
}

.edit-field-count {
  font-size: 24rpx;
  color: var(--text-hint);
  font-variant-numeric: tabular-nums;
}

.edit-textarea {
  width: 100%;
  height: 200rpx;
  box-sizing: border-box;
  padding: 20rpx 24rpx;
  background: var(--bg-sub);
  border: 2rpx solid var(--border-light);
  border-radius: var(--radius-md);
  font-size: 28rpx;
  color: var(--text-primary);
  line-height: 1.5;
}

.edit-actions {
  display: flex;
  gap: 16rpx;
  margin-top: 24rpx;
  justify-content: flex-end;
}

</style>
