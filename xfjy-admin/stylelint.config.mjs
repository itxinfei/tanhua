/* xfjy-admin stylelint 配置
   目标：与 xfjy-app 对齐（禁硬编码颜色），并额外约束「裸数值写间距」。
   策略：存量一律 warning 分级，不阻断构建；新增代码应做到零 warning。
   真源豁免：src/style.css 是 Token 定义处，允许字面量。 */
export default {
  ignoreFiles: ['dist/**', 'node_modules/**', 'src/style.css'],
  extends: ['stylelint-config-standard-scss'],
  overrides: [
    { files: ['**/*.vue'], customSyntax: 'postcss-html' },
    { files: ['**/*.scss'], customSyntax: 'postcss-scss' },
  ],
  rules: {
    // 核心 1：禁止十六进制硬编码颜色
    'color-no-hex': [true, { severity: 'warning' }],

    // 核心 2：颜色属性禁止 rgb()/hsl() 直接写字面量（应走 Token 或 EP 变量）
    'declaration-property-value-disallowed-list': [
      {
        '/^(color|background|background-color|border|border-color|box-shadow|fill|stroke)$/': [
          '/rgb\\(/',
          '/hsl\\(/',
        ],
        // 间距：4px 栅格之外的值（5–39 中的非 4 倍数）视为魔法数值
        '/^(margin|margin-top|margin-right|margin-bottom|margin-left|padding|padding-top|padding-right|padding-bottom|padding-left|gap|row-gap|column-gap)$/': [
          '/\\b(5|6|7|9|10|11|13|14|15|17|18|19|21|22|23|25|26|27|29|30|31|33|34|35|37|38|39)px\\b/',
        ],
      },
      { severity: 'warning' },
    ],

    // Vue/SCSS 兼容
    'selector-pseudo-class-no-unknown': [
      true,
      { ignorePseudoClasses: ['deep', 'global', 'root'] },
    ],
    'selector-pseudo-element-no-unknown': [true, { ignorePseudoElements: ['v-deep', 'deep'] }],
    'at-rule-no-unknown': null,
    'scss/at-rule-no-unknown': true,

    // 与工程现状冲突的高噪点规则（沿用 xfjy-app 的关法）
    'no-descending-specificity': null,
    // 存量：渐变文字合法使用 -webkit-background-clip；break-word 待逐步替换为 overflow-wrap
    'property-no-vendor-prefix': null,
    'declaration-property-value-keyword-no-deprecated': [true, { severity: 'warning' }],
    'color-function-notation': null,
    'color-function-alias-notation': null,
    'color-hex-length': null,
    'alpha-value-notation': null,
    'import-notation': null,
    'declaration-block-no-redundant-longhand-properties': null,
    'shorthand-property-no-redundant-values': null,
    'media-feature-range-notation': null,
    'selector-class-pattern': null,
    'keyframes-name-pattern': null,
    'custom-property-pattern': null,
    'rule-empty-line-before': null,
    'at-rule-empty-line-before': null,
    'comment-empty-line-before': null,
    'declaration-block-single-line-max-declarations': null,
    'value-keyword-case': null,
    'length-zero-no-unit': null,
  },
}
