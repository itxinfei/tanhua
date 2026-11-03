import js from '@eslint/js'
import pluginVue from 'eslint-plugin-vue'
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
  // Vue SFC 中 lang="ts" 使用 TS 解析器
  {
    files: ['**/*.vue'],
    languageOptions: {
      parserOptions: {
        parser: tseslint.parser,
      },
    },
  },
  // 项目自定义规则（warn 级别为主，避免存量代码阻塞）
  {
    rules: {
      'no-console': 'warn',
      'no-unused-vars': 'warn',
      // uni-app 页面/组件名单词命名常见（如 index.vue、me.vue）
      'vue/multi-word-component-names': 'off',
    },
  },
]