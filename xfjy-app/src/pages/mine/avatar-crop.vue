<template>
  <view class="crop-page">
    <!-- 舞台：全宽方形，图片铺满整屏，取景框居中，框外压暗 -->
    <view
      class="crop-stage"
      :style="stageStyle"
    >
      <canvas
        id="cropCanvas"
        canvas-id="cropCanvas"
        type="2d"
        class="crop-canvas"
        :style="stageStyle"
        @touchstart="onTouchStart"
        @touchmove.stop="onTouchMove"
        @touchend="onTouchEnd"
        @touchcancel="onTouchEnd"
        @mousedown="onMouseDown"
      />

      <!-- 四边遮罩：固定黑色，只压暗取景框外，框内保持原图亮度 -->
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

      <!-- 1:1 取景框 + 圆形辅助线 + 三分网格 -->
      <view
        class="crop-frame"
        :style="{ top: GAP + 'px', left: GAP + 'px', width: FRAME + 'px', height: FRAME + 'px' }"
        aria-hidden="true"
      >
        <view class="crop-circle" />
        <view class="crop-grid-line crop-grid-line--v1" />
        <view class="crop-grid-line crop-grid-line--v2" />
        <view class="crop-grid-line crop-grid-line--h1" />
        <view class="crop-grid-line crop-grid-line--h2" />
      </view>

      <!-- 缩放倍率反馈：常显，顶部居中 -->
      <view
        v-if="imageReady"
        class="crop-zoom"
        aria-live="polite"
      >
        <text class="crop-zoom-text">
          {{ zoomLabel }}×
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
          :disabled="!imageReady || exporting"
          @tap="rotateImage"
        >
          <text class="tool-text">
            旋转 90°
          </text>
        </a11y-button>
        <a11y-button
          variant="text"
          label="重置裁剪"
          :disabled="!imageReady || exporting"
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
          :disabled="exporting"
          @tap="cancel"
        >
          <text>取消</text>
        </a11y-button>
        <a11y-button
          class="action-btn"
          variant="primary"
          label="保存头像"
          :loading="exporting"
          :disabled="!imageReady || exporting"
          @tap="confirmCrop"
        >
          <text>完成</text>
        </a11y-button>
      </view>
    </view>

    <!-- 离屏导出画布：CSS 尺寸固定 400×400，保证 1:1 输出。
         H5 端不关闭 hidpi：uni 的 canvasToTempFilePath 内部按全局 dpr 换算源区域，
         只有保留框架默认背板倍率才能正确读取整块画布 -->
    <canvas
      id="exportCanvas"
      canvas-id="exportCanvas"
      type="2d"
      class="export-canvas"
      :style="exportStyle"
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
/**
 * dpr 封顶：3x 屏按原始 dpr 会让画布像素与重绘成本翻倍。
 * 预览背板统一按 min(dpr, 2) 控制，超出部分由 MutationObserver 归位。
 */
const DPR_MAX = 2;

const sys = uni.getSystemInfoSync();
const STAGE = Math.floor(
  Math.min(sys.windowWidth, sys.windowHeight * STAGE_HEIGHT_RATIO, uni.upx2px(750)),
);
const GAP = uni.upx2px(FRAME_GAP_RPX);
const FRAME = Math.max(0, STAGE - GAP * 2);
const OUT_W = AVATAR_OUTPUT_SIZE;
const OUT_H = Math.round(AVATAR_OUTPUT_SIZE / AVATAR_CROP_RATIO);

let devicePixelRatio = 1;
// #ifdef H5
devicePixelRatio = typeof window === "undefined" ? 1 : window.devicePixelRatio || 1;
// #endif
// #ifdef MP-WEIXIN
devicePixelRatio = Number(uni.getSystemInfoSync().pixelRatio || 1);
// #endif
const DPR = Math.min(DPR_MAX, Math.max(1, devicePixelRatio));
/** 预览 canvas 的实际像素边长：CSS 尺寸 × dpr */
const PREVIEW_PIXELS = Math.round(STAGE * DPR);

const stageStyle = { width: `${STAGE}px`, height: `${STAGE}px` };
const exportStyle = { width: `${OUT_W}px`, height: `${OUT_H}px` };

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

/** 小程序 2d canvas 节点（H5 / App 走 canvas-id 旧版接口） */
interface CanvasImageLike {
  src: string;
  onload: (() => void) | null;
  onerror: ((error: unknown) => void) | null;
  width: number;
  height: number;
}

interface CanvasNodeLike {
  width: number;
  height: number;
  getContext: (type: "2d") => any;
  createImage: () => CanvasImageLike;
}

let mpPreviewNode: CanvasNodeLike | null = null;
let mpExportNode: CanvasNodeLike | null = null;
let mpPreviewImage: CanvasImageLike | null = null;
let mpExportImage: CanvasImageLike | null = null;
/** H5 预览用已解码图片：直接画到 DOM canvas，避免 uni 旧版 context 的异步图片加载 */
let h5PreviewImage: HTMLImageElement | null = null;

// #ifdef H5
function loadH5Image(src: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.onload = () => resolve(img);
    img.onerror = () => reject(new Error("image decode failed"));
    img.src = src;
  });
}
// #endif

onReady(async () => {
  pageReady.value = true;
  // #ifdef H5
  bindWheelListener();
  await nextTick();
  watchPreviewCanvasSize();
  // #endif
  // #ifdef MP-WEIXIN
  await initMpCanvases();
  if (imagePath.value) await prepareCanvasImages();
  // #endif
  if (imagePath.value) drawPreview();
});

onUnload(() => {
  onMouseUp();
  // #ifdef H5
  unbindWheelListener();
  previewObserver?.disconnect();
  previewObserver = null;
  // #endif
});

onLoad((options) => {
  let src = "";
  try {
    src = decodeURIComponent(String(options?.src || ""));
  } catch {
    src = "";
  }
  if (!src) {
    failAndBack("图片地址无效，请重新选择");
    return;
  }
  loadImage(src);
});

/** 失败提示后自动返回上一页，避免停在不可用状态 */
function failAndBack(message: string, delay = 1500): void {
  fail(message);
  setTimeout(() => uni.navigateBack(), delay);
}

function loadImage(src: string): void {
  uni.getImageInfo({
    src,
    success: async (res) => {
      if (Math.min(res.width, res.height) < AVATAR_MIN_SOURCE_SIZE) {
        failAndBack(`图片分辨率过低（需 ≥${AVATAR_MIN_SOURCE_SIZE}×${AVATAR_MIN_SOURCE_SIZE}），请重新选择`);
        return;
      }
      imageW.value = res.width;
      imageH.value = res.height;
      imagePath.value = res.path || src;
      rotation.value = 0;
      zoom.value = 1;
      offsetX.value = 0;
      offsetY.value = 0;
      zoomLabel.value = "1.0";
      // #ifdef H5
      try {
        h5PreviewImage = await loadH5Image(imagePath.value);
      } catch {
        failAndBack("图片读取失败，请重新选择");
        return;
      }
      // #endif
      // #ifdef MP-WEIXIN
      try {
        await prepareCanvasImages();
      } catch {
        failAndBack("图片读取失败，请重新选择");
        return;
      }
      // #endif
      imageReady.value = true;
      nextTick(() => drawPreview());
    },
    fail: () => failAndBack("图片读取失败，请重新选择"),
  });
}

/** 小程序端获取 2d canvas 节点并设置真实像素尺寸 */
async function initMpCanvases(): Promise<void> {
  try {
    mpPreviewNode = await queryCanvasNode("cropCanvas");
    mpPreviewNode.width = PREVIEW_PIXELS;
    mpPreviewNode.height = PREVIEW_PIXELS;
    mpExportNode = await queryCanvasNode("exportCanvas");
    mpExportNode.width = OUT_W;
    mpExportNode.height = OUT_H;
  } catch {
    failAndBack("裁剪画布初始化失败，请重试");
  }
}

/** fields 在部分平台的类型里不含 node 字段，这里做一次结构化读取 */
function readCanvasNode(result: unknown): CanvasNodeLike | null {
  const info = Array.isArray(result) ? result[0] : result;
  if (!info || typeof info !== "object") return null;
  return (info as { node?: CanvasNodeLike }).node ?? null;
}

function queryCanvasNode(id: string): Promise<CanvasNodeLike> {
  return new Promise((resolve, reject) => {
    uni
      .createSelectorQuery()
      .select(`#${id}`)
      .fields({ node: true, size: true }, (res: unknown) => {
        const node = readCanvasNode(res);
        if (node) resolve(node);
        else reject(new Error("canvas node missing"));
      })
      .exec();
  });
}

/** 小程序 2d canvas 不支持直接传图片路径，必须用 node.createImage() */
function loadCanvasImage(node: CanvasNodeLike, src: string): Promise<CanvasImageLike> {
  return new Promise((resolve, reject) => {
    const image = node.createImage();
    image.onload = () => resolve(image);
    image.onerror = () => reject(new Error("image decode failed"));
    image.src = src;
  });
}

async function prepareCanvasImages(): Promise<void> {
  // #ifdef MP-WEIXIN
  if (!imagePath.value) return;
  if (mpPreviewNode && !mpPreviewImage) {
    mpPreviewImage = await loadCanvasImage(mpPreviewNode, imagePath.value);
  }
  if (mpExportNode && !mpExportImage) {
    mpExportImage = await loadCanvasImage(mpExportNode, imagePath.value);
  }
  // #endif
}

// #ifdef H5
let previewObserver: MutationObserver | null = null;

/** H5：定位真实 canvas 元素（非 H5 端返回 null） */
function getPreviewCanvasEl(): HTMLCanvasElement | null {
  // #ifdef H5
  if (typeof document === "undefined") return null;
  const host = document.querySelector("#cropCanvas");
  if (!host) return null;
  return (host.tagName === "CANVAS" ? host : host.querySelector("canvas")) as HTMLCanvasElement | null;
  // #endif
  // #ifndef H5
  return null;
  // #endif
}

/**
 * uni 的 ResizeSensor 会在挂载后按它自己认定的倍率重置 canvas 背板，
 * 可能把 dpr 封顶后的尺寸改回去。监听 width/height 属性，
 * 被改动时立即恢复目标像素并重绘；尺寸已一致时直接跳过。
 */
function watchPreviewCanvasSize(): void {
  if (previewObserver || typeof MutationObserver === "undefined") return;
  const el = getPreviewCanvasEl();
  if (!el) return;
  previewObserver = new MutationObserver(() => {
    if (el.width === PREVIEW_PIXELS && el.height === PREVIEW_PIXELS) return;
    el.width = PREVIEW_PIXELS;
    el.height = PREVIEW_PIXELS;
    drawPreview();
  });
  previewObserver.observe(el, { attributes: true, attributeFilter: ["width", "height"] });
}
// #endif

// #ifdef APP-PLUS
/** APP 端：沿用 uni 旧版 canvas context（H5 已改走 DOM 直绘） */
function drawPreviewByLegacyContext(): void {
  const ctx = uni.createCanvasContext("cropCanvas", instance?.proxy ?? undefined);
  drawInto(ctx, PREVIEW_PIXELS, PREVIEW_PIXELS);
  ctx.draw();
}
// #endif

/** 旋转 90° 后参与覆盖计算的宽高 */
function rotatedWidth(): number {
  return rotation.value % 180 === 0 ? imageW.value : imageH.value;
}

function rotatedHeight(): number {
  return rotation.value % 180 === 0 ? imageH.value : imageW.value;
}

/** 铺满舞台的最小缩放：等价 cover 取景框并每边多留 GAP 余量 */
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
 * 统一绘制：预览与导出共用同一套变换。
 * W 为画布实际像素边长，factor = W / STAGE 同时承担 dpr 与导出比例换算。
 */
function drawInto(ctx: any, W: number, H: number, source: any = imagePath.value): void {
  const factor = W / STAGE;
  const scale = baseScale() * zoom.value * factor;
  ctx.clearRect(0, 0, W, H);
  ctx.save();
  ctx.translate(W / 2 + offsetX.value * factor, H / 2 + offsetY.value * factor);
  ctx.rotate((rotation.value * Math.PI) / 180);
  ctx.scale(scale, scale);
  ctx.drawImage(
    source,
    -imageW.value / 2,
    -imageH.value / 2,
    imageW.value,
    imageH.value,
  );
  ctx.restore();
}

function drawPreview(): void {
  if (!pageReady.value || !imagePath.value) return;
  // #ifdef MP-WEIXIN
  if (!mpPreviewNode || !mpPreviewImage) return;
  drawInto(
    mpPreviewNode.getContext("2d"),
    PREVIEW_PIXELS,
    PREVIEW_PIXELS,
    mpPreviewImage,
  );
  // #endif
  // #ifdef H5
  const el = getPreviewCanvasEl();
  if (!el || !h5PreviewImage) return;
  if (el.width !== PREVIEW_PIXELS) el.width = PREVIEW_PIXELS;
  if (el.height !== PREVIEW_PIXELS) el.height = PREVIEW_PIXELS;
  const ctx = el.getContext("2d");
  if (!ctx) return;
  // 关闭 uni 的 hidpi 包装：坐标已按实际像素换算，不能再被乘以 dpr
  (ctx as any).__hidpi__ = false;
  drawInto(ctx, PREVIEW_PIXELS, PREVIEW_PIXELS, h5PreviewImage);
  // #endif
  // #ifdef APP-PLUS
  drawPreviewByLegacyContext();
  // #endif
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

/**
 * H5 滚轮必须走原生监听：uni 组件事件会把 DOM 事件裁剪成只含 type/target 的对象，
 * deltaY 丢失，模板里的 @wheel 拿不到滚动量。
 */
// #ifdef H5
let wheelHost: HTMLElement | null = null;

function bindWheelListener(): void {
  if (typeof document === "undefined") return;
  const host = document.querySelector("#cropCanvas") as HTMLElement | null;
  if (!host) return;
  wheelHost = host;
  host.addEventListener("wheel", onWheel, { passive: false });
}

function unbindWheelListener(): void {
  if (!wheelHost) return;
  wheelHost.removeEventListener("wheel", onWheel);
  wheelHost = null;
}
// #endif

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
    const rect = document.querySelector("#cropCanvas")?.getBoundingClientRect();
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
  if (exporting.value) return;
  onMouseUp();
  uni.navigateBack();
}

/** 两端统一：H5/App 走 canvas-id，微信小程序走 2d canvas 节点 */
function finishExport(): void {
  const payload = {
    x: 0,
    y: 0,
    width: OUT_W,
    height: OUT_H,
    destWidth: OUT_W,
    destHeight: OUT_H,
    fileType: AVATAR_CROP_FILE_TYPE,
    quality: AVATAR_CROP_QUALITY,
    success: (res: { tempFilePath: string }) => {
      uni.$emit("avatar:cropped", res.tempFilePath);
      uni.navigateBack();
    },
    fail: () => {
      exporting.value = false;
      fail("裁剪失败，请重试");
    },
  };
  // #ifdef MP-WEIXIN
  uni.canvasToTempFilePath(
    { ...payload, canvasId: "exportCanvas", canvas: mpExportNode ?? undefined },
    instance?.proxy ?? undefined,
  );
  // #endif
  // #ifndef MP-WEIXIN
  uni.canvasToTempFilePath(
    { ...payload, canvasId: "exportCanvas" },
    instance?.proxy ?? undefined,
  );
  // #endif
}

function confirmCrop(): void {
  if (exporting.value || !imageReady.value) return;
  exporting.value = true;
  nextTick(() => {
    // #ifdef MP-WEIXIN
    if (!mpExportNode || !mpExportImage) {
      exporting.value = false;
      fail("裁剪失败，请重试");
      return;
    }
    drawInto(mpExportNode.getContext("2d"), OUT_W, OUT_H, mpExportImage);
    finishExport();
    // #endif
    // #ifndef MP-WEIXIN
    const ctx = uni.createCanvasContext("exportCanvas", instance?.proxy ?? undefined);
    drawInto(ctx, OUT_W, OUT_H);
    ctx.draw(false, () => finishExport());
    // #endif
  });
}
</script>

<style lang="scss" scoped>
/* 规范固定值：遮罩/描边必须压在任何照片上成立，故不随主题 token 变化 */
$crop-mask-color: rgba(0, 0, 0, .55);
$crop-edge-color: rgba(255, 255, 255, .9);
$crop-grid-color: rgba(255, 255, 255, .25);
$crop-circle-color: rgba(255, 255, 255, .35);
$crop-badge-bg: rgba(0, 0, 0, .45);
$crop-badge-text: rgba(255, 255, 255, .95);

.crop-page { min-height: 100vh; display: flex; flex-direction: column; background: var(--bg-sub); }

.crop-stage { position: relative; flex: none; overflow: hidden; background: var(--bg-sub); }

.crop-canvas { display: block; }

/* 框外压暗：固定黑 55%，框内保持原图亮度 */
.crop-mask { position: absolute; pointer-events: none; background: $crop-mask-color; }

/* 取景框：1px 内描边（box-sizing: border-box），保证外尺寸仍是 FRAME */
.crop-frame { position: absolute; box-sizing: border-box; pointer-events: none; border: 1px solid $crop-edge-color; box-shadow: var(--shadow-float); }

.crop-circle { position: absolute; top: 0; left: 0; right: 0; bottom: 0; box-sizing: border-box; border: 1px solid $crop-circle-color; border-radius: 50%; }

.crop-grid-line { position: absolute; background: $crop-grid-color; }
.crop-grid-line--v1 { top: 0; bottom: 0; left: 33.33%; width: 1px; }
.crop-grid-line--v2 { top: 0; bottom: 0; left: 66.66%; width: 1px; }
.crop-grid-line--h1 { left: 0; right: 0; top: 33.33%; height: 1px; }
.crop-grid-line--h2 { left: 0; right: 0; top: 66.66%; height: 1px; }

/* 倍率徽标：舞台上沿居中，常显，格式 `1.0×` */
.crop-zoom { position: absolute; top: 2px; left: 0; right: 0; display: flex; justify-content: center; pointer-events: none; }
.crop-zoom-text { padding: 0 var(--space-sm); border-radius: var(--radius-pill); background: $crop-badge-bg; color: $crop-badge-text; font-size: var(--fs-sm); line-height: 20px; }

.crop-loading { position: absolute; top: 0; left: 0; right: 0; bottom: 0; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: var(--space-sm); background: var(--bg-card); }
.crop-spinner { width: 48rpx; height: 48rpx; border: 4rpx solid var(--border-light); border-top-color: var(--color-primary); border-radius: var(--radius-pill); animation: crop-spin 0.8s linear infinite; }
.crop-loading-text { font-size: var(--fs-sm); color: var(--text-hint); }

/* 操作面板吸底，底部叠加安全区 */
.crop-panel { position: fixed; left: 0; right: 0; bottom: 0; display: flex; flex-direction: column; padding: var(--space-lg) var(--space-lg) calc(var(--space-lg) + env(safe-area-inset-bottom)); border-radius: var(--radius-lg) var(--radius-lg) 0 0; background: var(--bg-card); }

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
