/* stylelint 配置：uni-app + Vue3 + scss
   目标：拦截硬编码颜色与魔法数值，强制消费 Design Tokens。
   存量硬编码较多，color-no-hex 先设 warning 渐进治理；新增代码应做到零 warning。 */
module.exports = {
  ignoreFiles: [
    "dist/**",
    "node_modules/**",
    "src/uni.scss", // SCSS 变量真源，允许字面量
    "src/App.vue", // 全局令牌与基础样式真源，允许字面量
  ],
  extends: ["stylelint-config-standard-scss"],
  overrides: [
    {
      files: ["**/*.vue"],
      customSyntax: "postcss-html",
    },
    {
      files: ["**/*.scss"],
      customSyntax: "postcss-scss",
    },
  ],
  rules: {
    // 核心：禁止十六进制硬编码，先 warning 不阻断存量；稳定后可升级为 error
    "color-no-hex": [true, { severity: "warning" }],
    // rpx 是 uni-app 合法单位
    "unit-no-unknown": [true, { ignoreUnits: ["rpx"] }],
    // 兼容 Vue/SCSS 伪类与语法
    "selector-pseudo-class-no-unknown": [
      true,
      { ignorePseudoClasses: ["deep", "global", "root"] },
    ],
    "selector-pseudo-element-no-unknown": [
      true,
      { ignorePseudoElements: ["v-deep", "deep"] },
    ],
    "at-rule-no-unknown": null,
    "scss/at-rule-no-unknown": true,
    // uni-app 跨端需要厂商前缀，关闭相关限制
    "property-no-vendor-prefix": null,
    "selector-no-vendor-prefix": null,
    "value-no-vendor-prefix": null,
    // 降低与工程现状冲突的高噪点规则
    "no-descending-specificity": null,
    "color-function-notation": null,
    // stylelint 17 新增：要求 rgba()/hsla() 改写 rgb()/hsl()，存量大量使用 rgba 且写法合法，关闭
    "color-function-alias-notation": null,
    "color-hex-length": null,
    "alpha-value-notation": null,
    "import-notation": null,
    "declaration-block-no-redundant-longhand-properties": null,
    "shorthand-property-no-redundant-values": null,
    "media-feature-range-notation": null,
    "selector-class-pattern": null,
    "keyframes-name-pattern": null,
    "custom-property-pattern": null,
    // 以下为 Prettier 类纯格式规则，存量大量违反且与视觉一致性无关，关闭以免阻断 CI
    "rule-empty-line-before": null,
    "at-rule-empty-line-before": null,
    "comment-empty-line-before": null,
    "declaration-block-single-line-max-declarations": null,
    "value-keyword-case": null,
    "length-zero-no-unit": null,
  },
};
