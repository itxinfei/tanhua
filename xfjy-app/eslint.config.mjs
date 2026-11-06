import js from '@eslint/js'
import pluginVue from 'eslint-plugin-vue'
import vueParser from 'vue-eslint-parser'
import tseslint from 'typescript-eslint'
import globals from 'globals'

export default [
  // 忽略构建产物与工具目录
  {
    ignores: ['dist/**', 'unpackage/**', 'node_modules/**', '.playwright-*/**'],
  },
  // JS 基础规则
  js.configs.recommended,
  // Vue 3 规则
  ...pluginVue.configs['flat/recommended'],
  // TS 规则（此前未覆盖 .ts 文件，导致 utils/api 层不受 lint 约束）
  ...tseslint.configs.recommended,
  // 全局语言选项：最新 ECMAScript + uni-app 全局变量
  {
    languageOptions: {
      ecmaVersion: 'latest',
      globals: {
        ...globals.browser,
        ...globals.node,
        // uni-app 平台全局 API
        uni: 'readonly',
        getCurrentPages: 'readonly',
        plus: 'readonly',
        wx: 'readonly',
        uniCloud: 'readonly',
      },
    },
  },
  // Vue SFC 外层用 vue-eslint-parser，<script lang="ts"> 内层交给 TS 解析器
  {
    files: ['**/*.vue'],
    languageOptions: {
      parser: vueParser,
      parserOptions: {
        parser: tseslint.parser,
      },
    },
  },
  // 声明文件：模块增强允许空接口（等价父类型），关闭该 TS 规则
  {
    files: ['**/*.d.ts'],
    rules: {
      '@typescript-eslint/no-empty-object-type': 'off',
    },
  },
  // 项目自定义规则（本项目存量大量 any / 未使用变量，故 TS 规则以 warn 为主）
  {
    rules: {
      'no-console': 'warn',
      // TS 项目关掉 no-undef（由 tsc 负责），避免 uni / 类型名误报
      'no-undef': 'off',
      'no-unused-vars': 'off',
      '@typescript-eslint/no-unused-vars': 'warn',
      '@typescript-eslint/no-explicit-any': 'off',
      // uni-app 页面/组件名单词命名常见（如 index.vue、me.vue）
      'vue/multi-word-component-names': 'off',
    },
  },
]
