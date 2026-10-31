import { confirmVipPay } from "@/api/mine";

/**
 * 支付渠道抽象层
 *
 * 当前为「模拟支付」实现（沙箱：调后端 confirmVipPay 直接开通）。
 * 接入真实支付时，只需实现对应渠道的 requestPayment 并在 PAYMENT_MODE 中切换，
 * 页面层（vip.vue）不感知渠道差异。
 *
 * 生产接入清单：
 * 1. 微信小程序：uni.requestPayment({ provider: 'wxpay', orderInfo: 后端统一下单返回的签名参数 })
 *    — 后端需新增「统一下单」接口，返回 wx.requestPayment 所需的 timeStamp/nonceStr/package/signType/paySign
 * 2. 支付宝小程序：uni.requestPayment({ provider: 'alipay', orderInfo: 后端返回的 orderStr })
 * 3. H5：跳转支付网关收银台 URL，支付结果通过后端异步回调 + 前端轮询订单状态确认
 * 4. 支付结果以「后端订单状态」为准（GET /users/vip/order/{orderNo}），不能只信前端回参
 */

/** 支付模式：mock=沙箱模拟支付；wechat/alipay=真实渠道（预留） */
export type PayMode = "mock" | "wechat" | "alipay";

/** 当前生效的支付模式（上线真实渠道时改这里，或改为构建时环境变量注入） */
const PAYMENT_MODE: PayMode = "mock";

/** 统一支付入参：后端创建的支付订单 */
export interface PaymentRequest {
  /** 订单号 */
  orderNo: string;
  /** 金额，单位：分 */
  amountFen: number;
  /** 商品名（拉起收银台时展示） */
  subject: string;
}

/**
 * 渠道支付接口：所有真实渠道都实现本接口，输入订单、输出支付是否成功
 */
export interface PayChannel {
  /** 渠道标识 */
  readonly mode: PayMode;
  /**
   * 发起支付
   * @returns true=支付成功（后端已确认）；false=用户取消或失败
   */
  requestPayment(req: PaymentRequest): Promise<boolean>;
}

/**
 * 模拟支付渠道（沙箱）
 * 直接调用后端 confirmVipPay 完成开通；取消由调用方在确认弹窗处理，不走本方法
 */
const mockChannel: PayChannel = {
  mode: "mock",
  async requestPayment(req: PaymentRequest): Promise<boolean> {
    await confirmVipPay(req.orderNo);
    return true;
  },
};

/**
 * 微信支付渠道（预留，未实现）
 * 实现步骤见文件头「生产接入清单」第 1 条
 */
const wechatChannel: PayChannel = {
  mode: "wechat",
  async requestPayment(_req: PaymentRequest): Promise<boolean> {
    // TODO(生产接入): 调后端统一下单 → uni.requestPayment({ provider: "wxpay", ... }) → 校验后端订单状态
    throw new Error("微信支付暂未接入");
  },
};

/**
 * 支付宝支付渠道（预留，未实现）
 */
const alipayChannel: PayChannel = {
  mode: "alipay",
  async requestPayment(_req: PaymentRequest): Promise<boolean> {
    // TODO(生产接入): 调后端统一下单 → uni.requestPayment({ provider: "alipay", ... }) → 校验后端订单状态
    throw new Error("支付宝支付暂未接入");
  },
};

/** 渠道注册表：新增渠道在此登记 */
const CHANNELS: Record<PayMode, PayChannel> = {
  mock: mockChannel,
  wechat: wechatChannel,
  alipay: alipayChannel,
};

/**
 * 获取当前生效的支付渠道
 */
export function getPayChannel(): PayChannel {
  return CHANNELS[PAYMENT_MODE];
}
