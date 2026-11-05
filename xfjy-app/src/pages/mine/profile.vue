<template>
  <view class="profile-page">
    <!-- 头像：整行作为可访问按钮 -->
    <a11y-button class="avatar-row anim-fade-down" label="更换头像" @tap="changeAvatar">
      <text class="label">头像</text>
      <view class="avatar-wrap">
        <image lazy-load class="avatar" :src="resolveAvatar(form.avatar)" mode="aspectFill" aria-hidden="true" />
        <view class="avatar-mask"><text>更换</text></view>
      </view>
      <text class="arrow" aria-hidden="true">›</text>
    </a11y-button>

    <!-- 照片墙 -->
    <view class="section anim-fade-up anim-delay-1">
      <text class="section-title">照片墙 ({{ form.photoAlbum.length }}/{{ MAX_ALBUM_COUNT }})</text>
      <view class="album-grid">
        <view v-for="(img, i) in form.photoAlbum" :key="i" class="album-item">
          <image lazy-load class="album-img" :src="img" mode="aspectFill" :aria-label="`照片墙第 ${i + 1} 张`" />
          <a11y-button class="remove" label="删除这张照片" @tap="removePhoto(i)">
            <text class="remove-icon">×</text>
          </a11y-button>
        </view>
        <a11y-button
          v-if="form.photoAlbum.length < MAX_ALBUM_COUNT"
          class="add-photo"
          label="添加照片"
          @tap="addPhoto"
        >
          <text class="plus">+</text>
          <text class="add-label">{{ form.photoAlbum.length }}/{{ MAX_ALBUM_COUNT }}</text>
        </a11y-button>
      </view>
    </view>

    <!-- 基本信息（每项独立保存） -->
    <view class="section anim-fade-up anim-delay-2">
      <!-- 昵称：文本输入 -->
      <a11y-button class="form-item" label="修改昵称" @tap="editField('nickname', '昵称')">
        <text class="label">昵称</text>
        <text class="value" :class="{ unset: !form.nickname }">{{ form.nickname || '未设置' }}</text>
        <text class="arrow" aria-hidden="true">›</text>
      </a11y-button>

      <!-- 个性签名：文本输入 -->
      <a11y-button class="form-item" label="修改个性签名" @tap="editField('signature', '个性签名')">
        <text class="label">签名</text>
        <text class="value ellipsis" :class="{ unset: !form.signature }">{{ form.signature || '未设置' }}</text>
        <text class="arrow" aria-hidden="true">›</text>
      </a11y-button>

      <!-- 性别：行内 toggle（两个选项保留原位） -->
      <view class="form-item form-item-inline">
        <text class="label">性别</text>
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
              <text class="option-label">{{ option.label }}</text>
            </template>
          </a11y-radio-group>
        </view>
      </view>

      <!-- 城市：底部选择器 -->
      <a11y-button class="form-item" label="修改城市" @tap="openPicker('city')">
        <text class="label">城市</text>
        <text class="value" :class="{ unset: !form.city }">{{ form.city || '未设置' }}</text>
        <text class="arrow" aria-hidden="true">›</text>
      </a11y-button>

      <!-- 身高：底部选择器 -->
      <a11y-button class="form-item" label="修改身高" @tap="openPicker('height')">
        <text class="label">身高</text>
        <text class="value" :class="{ unset: !form.height }">{{ form.height ? form.height + 'cm' : '未设置' }}</text>
        <text class="arrow" aria-hidden="true">›</text>
      </a11y-button>

      <!-- 学历：底部选择器 -->
      <a11y-button class="form-item" label="修改学历" @tap="openPicker('education')">
        <text class="label">学历</text>
        <text class="value" :class="{ unset: !form.education }">{{ form.education || '未设置' }}</text>
        <text class="arrow" aria-hidden="true">›</text>
      </a11y-button>

      <!-- 行业：底部选择器 -->
      <a11y-button class="form-item" label="修改行业" @tap="openPicker('industry')">
        <text class="label">行业</text>
        <text class="value" :class="{ unset: !form.industry }">{{ form.industry || '未设置' }}</text>
        <text class="arrow" aria-hidden="true">›</text>
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
  </view>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { onShow } from "@dcloudio/uni-app";
import { useUserStore } from "@/store/user";
import A11yRadioGroup from "@/components/a11y-radio-group.vue";
import A11yButton from "@/components/a11y-button.vue";
import A11yPicker from "@/components/a11y-picker.vue";
import { getUserProfile, updateUserProfile, uploadImage } from "@/api/mine";
import { success, fail, loading, hideLoading } from "@/utils/ux";
import { resolveAvatar } from "@/utils/avatar";

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

/** 选择器确认：即时保存该字段 */
async function onPickerConfirm(value: string): Promise<void> {
  const field = pickerConfig[pickerField.value].field;
  (form.value as any)[field] = value;
  await saveField(field, value);
}

/** 单个字段保存到后端（确认即提交，不含照片墙/昵称/签名的归一化字段） */
const savingFields = new Set<string>();

async function saveField(field: string, value: string): Promise<void> {
  // 同字段在途锁：快速连点（如性别切换）不发并发 PUT
  if (savingFields.has(field)) return;
  savingFields.add(field);
  // 后端字段映射：前端 industry → 后端 profession
  const apiField = field === "industry" ? "profession" : field;
  const payload: Record<string, any> = { [apiField]: value };
  try {
    await updateUserProfile(payload);
    // 保存成功才同步 store（失败保留旧值，避免界面与后端不一致）
    const storeField = field === "industry" ? "profession" : field;
    userStore.updateUserInfo({ [storeField]: value } as any);
  } catch {
    // request 层统一 toast 错误
  } finally {
    savingFields.delete(field);
  }
}

/** 性别变更：即时保存 */
async function onGenderChange(value: string): Promise<void> {
  if (form.value.gender === value) return;
  form.value.gender = value;
  await saveField("gender", value);
}

/** 更换头像：选图后上传 MinIO，成功写入 MinIO URL（随保存提交后端） */
function changeAvatar(): void {
  uni.chooseImage({
    count: 1,
    sizeType: ["compressed"],
    success: async (res) => {
      const tempPath = res.tempFilePaths[0];
      if (!tempPath) return; // 用户取消
      loading("上传中...");
      try {
        const result = await uploadImage(tempPath);
        hideLoading();
        if (result.status === "done" && result.name) {
          form.value.avatar = result.name;
          // 先保存成功再提示，避免保存失败却已告知成功
          await saveField("avatar", result.name);
          success("头像已更新");
        } else {
          fail("上传失败，请重试");
        }
      } catch (e) {
        hideLoading();
        fail("上传失败，请重试");
      }
    },
    fail: () => {
      // 用户取消或选择失败，不显示 toast
    },
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
        for (const tempPath of tempPaths) {
          try {
            const result = await uploadImage(tempPath);
            if (result.status === "done" && result.name) {
              form.value.photoAlbum.push(result.name);
            } else {
              failed++;
            }
          } catch {
            failed++;
          }
        }
        // 仅在有图片入墙时提交
        if (form.value.photoAlbum.length > 0 && failed < tempPaths.length) {
          await saveAlbum();
        }
        if (failed > 0) fail(`${failed} 张照片上传失败，可重新添加`);
      } finally {
        hideLoading();
      }
    },
    fail: () => {
      // 用户取消或选择失败，不显示 toast
    },
  });
}

/** 删除照片墙图片 */
function removePhoto(i: number): void {
  form.value.photoAlbum.splice(i, 1);
  saveAlbum();
}

/** 保存照片墙（JSON 字符串），成功后同步 store，避免返回再进读到旧照片墙 */
async function saveAlbum(): Promise<void> {
  try {
    await updateUserProfile({ photoAlbum: JSON.stringify(form.value.photoAlbum) });
    userStore.updateUserInfo({ photoAlbum: [...form.value.photoAlbum] });
  } catch {
    // request 层统一 toast 错误
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

function editField(field: string, label: string): void {
  uni.showModal({
    title: `修改${label}`,
    editable: true,
    placeholderText: `请输入${label}（最多 ${FIELD_MAX_LENGTH[field]} 字）`,
    content: (form.value as any)[field] || "",
    success: async (res) => {
      if (!res.confirm) return;
      const text = (res.content || "").trim();
      // 昵称/签名不允许置空：静默 return 会让用户以为「点了没反应」，
      // 必须给出明确提示（BUG-04 收口）
      if (!text) {
        fail(`${label}不能为空`);
        return;
      }
      const max = FIELD_MAX_LENGTH[field];
      if (text.length > max) {
        fail(`${label}最多 ${max} 个字符`);
        return;
      }
      (form.value as any)[field] = text;
      await saveField(field, text);
    },
  });
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

/* ===== 头像行 ===== */
.avatar-row { display: flex; align-items: center; padding: 32rpx; background: var(--bg-card);
  .label { flex: 1; font-size: 28rpx; color: var(--text-primary); font-weight: 500; }
  .avatar-wrap { position: relative; width: 120rpx; height: 120rpx;
    .avatar { width: 100%; height: 100%; border-radius: 50%; border: 4rpx solid var(--bg-icon-soft); }
    .avatar-mask { position: absolute; bottom: 0; left: 0; right: 0; height: 40rpx; background: rgba(0, 0, 0, 0.45); border-radius: 0 0 60rpx 60rpx; display: flex; align-items: center; justify-content: center;
      text { font-size: 20rpx; color: var(--text-inverse); }
    }
  }
  .arrow { font-size: 36rpx; color: var(--text-hint); margin-left: 16rpx; }
}

/* ===== 分组 ===== */
.section { background: var(--bg-card); margin-top: 16rpx; padding: 8rpx 32rpx;
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
    &.female.active { background: var(--gradient-brand); border-color: var(--color-primary); box-shadow: 0 4rpx 14rpx rgba(254, 60, 114, 0.3); color: var(--text-inverse); }
  }
}

</style>
