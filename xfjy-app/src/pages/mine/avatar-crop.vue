<template>
  <view class="crop-page">
    <!-- 舞台：全宽方形，图片铺满整屏，取景框居中，框外压暗 -->
    <view
      class="crop-stage"
      :style="{ width: STAGE + 'px', height: STAGE + 'px' }"
    >
      <canvas
        id="cropCanvas"
        canvas-id="cropCanvas"
        class="crop-canvas"
        :style="{ width: STAGE + 'px', height: STAGE + 'px' }"
        @touchstart="onTouchStart"
        @touchmove.stop="onTouchMove"
        @touchend="onTouchEnd"
        @touchcancel="onTouchEnd"
        @wheel="onWheel"
        @mousedown="onMouseDown"
      />

      <!-- 四边遮罩：只压暗取景框外，框内保持原图亮度 -->
      <view
        class="crop-mask"
        :style="{ top: '0px', left: '0px', width: STAGE + 'px', height: GAP + 'px' }"
        aria-hidden="true"
      />
      <view
        class="crop-mask"
        :style="{ top: GAP + FRAME + 'px', left: '0px', width: STAGE + 'px', height: GAP + 'px' }"
        aria-hidden="true"
      />
      <view
        class="crop-mask"
        :style="{ top: GAP + 'px', left: '0px', width: GAP + 'px', height: FRAME + 'px' }"
        aria-hidden="true"
      />
      <view
        class="crop-mask"
        :style="{ top: GAP + 'px', left: GAP + FRAME + 'px', width: GAP + 'px', height: FRAME + 'px' }"
        aria-hidden="true"
      />

      <!-- 1:1 取景框 + 三分网格 -->
      <view
        class="crop-frame"
        :style="{ top: GAP + 'px', left: GAP + 'px', width: FRAME + 'px', height: FRAME + 'px' }"
        aria-hidden="true"
      >
        <view class="crop-grid-line crop-grid-line--v1" />
        <view class="crop-grid-line crop-grid-line--v2" />
        <view class="crop-grid-line crop-grid-line--h1" />
        <view class="crop-grid-line crop-grid-line--h2" />
      </view>

      <!-- 缩放倍率反馈：跟随手势实时更新 -->
      <view
        v-if="imageReady"
        class="crop-zoom"
        aria-live="polite"
      >
        <text class="crop-zoom-text">
          ×{{ zoomLabel }}
        </text>
      </view>

      <view
        v-if="!imageReady"
        class="crop-loading"
        aria-hidden="true"
      >
        <view class="crop-spinner" />
        <text class="crop-loading-text">
          图片加载中
        </text>
      </view>
    </view>

    <view class="crop-panel">
      <text class="crop-hint">
        拖动调整位置 · 双指或滚轮缩放
      </text>
      <view class="crop-tools">
        <a11y-button
          variant="text"
          label="旋转头像 90 度"
          :disabled="!imageReady"
          @tap="rotateImage"
        >
          <text class="tool-text">
            旋转 90°
          </text>
        </a11y-button>
        <a11y-button
          variant="text"
          label="重置裁剪"
          :disabled="!imageReady"
          @tap="resetTransform"
        >
          <text class="tool-text">
            重置
          </text>
        </a11y-button>
      </view>
      <view class="crop-actions">
        <a11y-button
          class="action-btn"
          variant="ghost"
          label="取消裁剪"
          @tap="cancel"
        >
          <text>取消</text>
        </a11y-button>
        <a11y-button
          class="action-btn"
          variant="primary"
          label="保存头像"
          :loading="exporting"
          :disabled="!imageReady"
          @tap="confirmCrop"
        >
          <text>完成</text>
        </a11y-button>
      </view>
    </view>

    <!-- 离屏导出画布：固定 400×400，保证 1:1 输出 -->
    <canvas
      id="exportCanvas"
      canvas-id="exportCanvas"
      class="export-canvas"
      :style="{ width: OUT_W + 'px', height: OUT_H + 'px' }"
    />
  </view>
</template>

<script setup lang="ts">
import { ref, getCurrentInstance, nextTick } from "vue";
import { onLoad, onReady, onUnload } from "@dcloudio/uni-app";
import A11yButton from "@/components/a11y-button.vue";
import { fail } from "@/utils/ux";
import {
  AVATAR_CROP_RATIO,
  AVATAR_CROP_FILE_TYPE,
  AVATAR_CROP_QUALITY,
  AVATAR_MAX_ZOOM,
  AVATAR_MIN_SOURCE_SIZE,
  AVATAR_OUTPUT_SIZE,
} from "@/constants/avatar";

/** 舞台高度上限占视口比例：避免桌面端正方形舞台过高 */
const STAGE_HEIGHT_RATIO = 0.55;
/** 取景框与舞台的留白（对应 --space-xl = 48rpx；canvas 计算需要 px） */
const FRAME_GAP_RPX = 48;
/** 滚轮每档缩放倍率 */
const WHEEL_ZOOM_STEP = 1.1;

const sys = uni.getSystemInfoSync();
const STAGE = Math.floor(
  Math.min(sys.windowWidth, sys.windowHeight * STAGE_HEIGHT_RATIO, uni.upx2px(750)),
);
const GAP = uni.upx2px(FRAME_GAP_RPX);
const FRAME = Math.max(0, STAGE - GAP * 2);
const OUT_W = AVATAR_OUTPUT_SIZE;
const OUT_H = Math.round(AVATAR_OUTPUT_SIZE / AVATAR_CROP_RATIO);

const instance = getCurrentInstance();

const imagePath = ref("");
const imageW = ref(0);
const imageH = ref(0);
const imageReady = ref(false);
/** 旋转角度：0 / 90 / 180 / 270 */
const rotation = ref(0);
const zoom = ref(1);
/** 舞台坐标系下的位移（px），导出时按比例换算 */
const offsetX = ref(0);
const offsetY = ref(0);
const exporting = ref(false);
const pageReady = ref(false);

const zoomLabel = ref("1.0");

onReady(() => {
  pageReady.value = true;
  if (imagePath.value) drawPreview();
});

onUnload(() => onMouseUp());

onLoad((options) => {
  let src = "";
  try {
    src = decodeURIComponent(String(options?.src || ""));
  } catch {
    src = "";
  }
  if (!src) {
    fail("图片地址无效，请重新选择");
    uni.navigateBack();
    return;
  }
  loadImage(src);
});

function loadImage(src: string): void {
  uni.getImageInfo({
    src,
    success: (res) => {
      if (Math.min(res.width, res.height) < AVATAR_MIN_SOURCE_SIZE) {
        fail(`图片分辨率过低（需 ≥${AVATAR_MIN_SOURCE_SIZE}×${AVATAR_MIN_SOURCE_SIZE}），请重新选择`);
        uni.navigateBack();
        return;
      }
      imageW.value = res.width;
      imageH.value = res.height;
      imagePath.value = res.path || src;
      rotation.value = 0;
      zoom.value = 1;
      offsetX.value = 0;
      offsetY.value = 0;
      imageReady.value = true;
      nextTick(() => drawPreview());
    },
    fail: () => {
      fail("图片读取失败，请重新选择");
      // 与「src 无效」一致：无法读取时返回上级，避免停留在 loading 空页
      uni.navigateBack({
        fail: () => {
          // 无上级页面时保留页面，用户可手动关闭
        },
      });
    },
  });
}

/** 旋转 90° 后参与覆盖计算的宽高 */
function rotatedWidth(): number {
  return rotation.value % 180 === 0 ? imageW.value : imageH.value;
}

function rotatedHeight(): number {
  return rotation.value % 180 === 0 ? imageH.value : imageW.value;
}

/** 铺满舞台的最小缩放（保证取景框内不露底） */
function baseScale(): number {
  const w = rotatedWidth();
  const h = rotatedHeight();
  if (!w || !h) return 0;
  return Math.max(STAGE / w, STAGE / h);
}

/** 位移上限：以取景框为界，避免把空白推进框内 */
function clampOffsetX(value: number): number {
  const max = Math.max(0, (rotatedWidth() * baseScale() * zoom.value - FRAME) / 2);
  return Math.min(max, Math.max(-max, value));
}

function clampOffsetY(value: number): number {
  const max = Math.max(0, (rotatedHeight() * baseScale() * zoom.value - FRAME) / 2);
  return Math.min(max, Math.max(-max, value));
}

function clampOffsets(): void {
  offsetX.value = clampOffsetX(offsetX.value);
  offsetY.value = clampOffsetY(offsetY.value);
}

function clampZoom(value: number): number {
  return Math.min(AVATAR_MAX_ZOOM, Math.max(1, value));
}

function updateZoomLabel(): void {
  zoomLabel.value = zoom.value.toFixed(1);
}

/**
 * 统一绘制：预览与导出共用同一套变换，
 * 导出画布按 W/STAGE 等比换算位移与缩放，保证所见即所得。
 */
function drawInto(ctx: any, W: number, H: number): void {
  const factor = W / STAGE;
  const scale = baseScale() * zoom.value * factor;
  ctx.clearRect(0, 0, W, H);
  ctx.save();
  ctx.translate(W / 2 + offsetX.value * factor, H / 2 + offsetY.value * factor);
  ctx.rotate((rotation.value * Math.PI) / 180);
  ctx.scale(scale, scale);
  ctx.drawImage(
    imagePath.value,
    -imageW.value / 2,
    -imageH.value / 2,
    imageW.value,
    imageH.value,
  );
  ctx.restore();
}

function drawPreview(): void {
  if (!pageReady.value || !imagePath.value) return;
  const ctx = uni.createCanvasContext("cropCanvas", instance?.proxy ?? undefined);
  drawInto(ctx, STAGE, STAGE);
  ctx.draw();
}

let dragStartX = 0;
let dragStartY = 0;
let dragOriginX = 0;
let dragOriginY = 0;
let pinchStart = 0;
let pinchZoom = 1;

/** H5 鼠标拖动（触屏端不会进入该状态） */
let mouseDragging = false;
let mouseStartX = 0;
let mouseStartY = 0;
let mouseOriginX = 0;
let mouseOriginY = 0;

function touchDistance(touches: any[]): number {
  const dx = touches[0].clientX - touches[1].clientX;
  const dy = touches[0].clientY - touches[1].clientY;
  return Math.hypot(dx, dy);
}

function onTouchStart(e: any): void {
  if (exporting.value) return;
  const touches = e.touches || [];
  if (touches.length >= 2) {
    // 双指落下即进入缩放，单指拖动立即失效，避免两种手势打架
    pinchStart = touchDistance(touches);
    pinchZoom = zoom.value;
    return;
  }
  if (touches.length === 1) {
    // 按下即跟手：不做阈值等待，位移直接作用到变换参数
    dragStartX = touches[0].clientX;
    dragStartY = touches[0].clientY;
    dragOriginX = offsetX.value;
    dragOriginY = offsetY.value;
  }
}

function onTouchMove(e: any): void {
  if (exporting.value) return;
  const touches = e.touches || [];
  if (touches.length >= 2 && pinchStart > 0) {
    zoom.value = clampZoom(pinchZoom * (touchDistance(touches) / pinchStart));
    updateZoomLabel();
    clampOffsets();
    drawPreview();
    return;
  }
  if (touches.length === 1) {
    // 像素级跟手：手指移动多少，图像位移多少
    offsetX.value = clampOffsetX(dragOriginX + (touches[0].clientX - dragStartX));
    offsetY.value = clampOffsetY(dragOriginY + (touches[0].clientY - dragStartY));
    drawPreview();
  }
}

function onTouchEnd(e: any): void {
  const touches = e?.touches || [];
  pinchStart = 0;
  if (touches.length === 1) {
    // 双指松开一指后继续拖动：重置起点，避免位移跳变
    dragStartX = touches[0].clientX;
    dragStartY = touches[0].clientY;
    dragOriginX = offsetX.value;
    dragOriginY = offsetY.value;
  }
}

/** 以锚点为中心缩放：保持锚点下的图像内容不动（滚轮用鼠标位置） */
function zoomAtPoint(nextZoom: number, anchorX: number, anchorY: number): void {
  const next = clampZoom(nextZoom);
  const ratio = next / zoom.value;
  const cx = anchorX - STAGE / 2;
  const cy = anchorY - STAGE / 2;
  offsetX.value = clampOffsetX(cx - ratio * (cx - offsetX.value));
  offsetY.value = clampOffsetY(cy - ratio * (cy - offsetY.value));
  zoom.value = next;
  updateZoomLabel();
  drawPreview();
}

/** H5 桌面端：鼠标按下即拖，滚轮以鼠标位置为锚点缩放 */
function onMouseDown(e: any): void {
  if (exporting.value || !imageReady.value) return;
  const ev = e as MouseEvent;
  if (typeof ev.button === "number" && ev.button !== 0) return;
  mouseDragging = true;
  mouseStartX = ev.clientX;
  mouseStartY = ev.clientY;
  mouseOriginX = offsetX.value;
  mouseOriginY = offsetY.value;
  // #ifdef H5
  window.addEventListener("mousemove", onMouseMove);
  window.addEventListener("mouseup", onMouseUp);
  // #endif
}

function onMouseMove(e: any): void {
  if (!mouseDragging || exporting.value) return;
  const ev = e as MouseEvent;
  offsetX.value = clampOffsetX(mouseOriginX + (ev.clientX - mouseStartX));
  offsetY.value = clampOffsetY(mouseOriginY + (ev.clientY - mouseStartY));
  drawPreview();
}

function onMouseUp(): void {
  mouseDragging = false;
  // #ifdef H5
  window.removeEventListener("mousemove", onMouseMove);
  window.removeEventListener("mouseup", onMouseUp);
  // #endif
}

/** H5 桌面端滚轮缩放（移动端由双指手势覆盖） */
function onWheel(e: any): void {
  if (exporting.value || !imageReady.value) return;
  e?.preventDefault?.();
  const delta = Number(e?.deltaY || e?.wheelDelta || 0);
  if (!delta) return;
  const step = delta > 0 ? 1 / WHEEL_ZOOM_STEP : WHEEL_ZOOM_STEP;
  let anchorX = STAGE / 2;
  let anchorY = STAGE / 2;
  if (typeof document !== "undefined") {
    // 用组件实例 $el 查询而非全局 id，避免 uni-app H5 渲染后 id 带前缀导致选择器失效
    const root = instance?.proxy?.$el as HTMLElement | null;
    const rect = root?.querySelector("#cropCanvas")?.getBoundingClientRect();
    if (rect) {
      anchorX = e.clientX - rect.left;
      anchorY = e.clientY - rect.top;
    }
  }
  zoomAtPoint(zoom.value * step, anchorX, anchorY);
}

function resetTransform(): void {
  rotation.value = 0;
  zoom.value = 1;
  offsetX.value = 0;
  offsetY.value = 0;
  updateZoomLabel();
  drawPreview();
}

function rotateImage(): void {
  rotation.value = (rotation.value + 90) % 360;
  offsetX.value = 0;
  offsetY.value = 0;
  clampOffsets();
  drawPreview();
}

function cancel(): void {
  onMouseUp();
  uni.navigateBack();
}

function confirmCrop(): void {
  if (exporting.value || !imageReady.value) return;
  exporting.value = true;
  nextTick(() => {
    const ctx = uni.createCanvasContext("exportCanvas", instance?.proxy ?? undefined);
    drawInto(ctx, OUT_W, OUT_H);
    ctx.draw(false, () => {
      uni.canvasToTempFilePath(
        {
          canvasId: "exportCanvas",
          x: 0,
          y: 0,
          width: OUT_W,
          height: OUT_H,
          destWidth: OUT_W,
          destHeight: OUT_H,
          fileType: AVATAR_CROP_FILE_TYPE,
          quality: AVATAR_CROP_QUALITY,
          success: (res) => {
            uni.$emit("avatar:cropped", res.tempFilePath);
            uni.navigateBack({
              fail: () => {
                // 无上级页面时 navigateBack 失败，复位 exporting 避免按钮永久 loading
                exporting.value = false;
                fail("无法返回，请手动关闭页面");
              },
            });
          },
          fail: () => {
            exporting.value = false;
            fail("裁剪失败，请重试");
          },
        },
        instance?.proxy ?? undefined,
      );
    });
  });
}
</script>

<style lang="scss" scoped>
$crop-mask-opacity: 0.9;

.crop-page { min-height: 100vh; display: flex; flex-direction: column; background: var(--bg-sub); }

.crop-stage { position: relative; flex: none; overflow: hidden; background: var(--bg-sub); }

.crop-canvas { display: block; }

/* 框外压暗：页面底色 + 透明度，随主题切换 */
.crop-mask { position: absolute; pointer-events: none; background: var(--bg-page); opacity: $crop-mask-opacity; }

.crop-frame { position: absolute; box-sizing: border-box; pointer-events: none; border: 2rpx solid var(--bg-card); box-shadow: var(--shadow-float); }

.crop-grid-line { position: absolute; background: var(--bg-card); opacity: 0.6; }
.crop-grid-line--v1 { top: 0; bottom: 0; left: 33.33%; width: 1px; }
.crop-grid-line--v2 { top: 0; bottom: 0; left: 66.66%; width: 1px; }
.crop-grid-line--h1 { left: 0; right: 0; top: 33.33%; height: 1px; }
.crop-grid-line--h2 { left: 0; right: 0; top: 66.66%; height: 1px; }

.crop-zoom { position: absolute; top: var(--space-sm); right: var(--space-sm); padding: 0 var(--space-xs); border-radius: var(--radius-pill); background: var(--bg-card); box-shadow: var(--shadow-card); }
.crop-zoom-text { font-size: var(--fs-sm); line-height: 44rpx; color: var(--text-hint); }

.crop-loading { position: absolute; top: 0; left: 0; right: 0; bottom: 0; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: var(--space-sm); background: var(--bg-card); }
.crop-spinner { width: 48rpx; height: 48rpx; border: 4rpx solid var(--border); border-top-color: var(--color-primary); border-radius: var(--radius-pill); animation: crop-spin 0.8s linear infinite; }
.crop-loading-text { font-size: var(--fs-sm); color: var(--text-hint); }

.crop-panel { flex: 1; display: flex; flex-direction: column; padding: var(--space-lg) var(--space-lg) calc(var(--space-lg) + env(safe-area-inset-bottom)); border-radius: var(--radius-lg) var(--radius-lg) 0 0; background: var(--bg-card); }

.crop-hint { font-size: var(--fs-sm); color: var(--text-hint); text-align: center; }

.crop-tools { display: flex; justify-content: center; gap: var(--space-xl); margin-top: var(--space-xs);
  .tool-text { font-size: var(--fs-base); }
}

.crop-actions { display: flex; gap: var(--space-md); margin-top: var(--space-xl);
  .action-btn { flex: 1; }
}

/* 导出画布放到视口外：保留真实像素尺寸，不参与布局与交互 */
.export-canvas { position: fixed; top: 0; left: -10000px; pointer-events: none; }

@keyframes crop-spin { to { transform: rotate(360deg); } }
</style>
