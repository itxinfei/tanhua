import js from '@eslint/js'
import pluginVue from 'eslint-plugin-vue'
import tseslint from 'typescript-eslint'
import globals from 'globals'

export default [
  // 忽略构建产物与工具目录
  {
    ignores: [
      'dist/**',
      'node_modules/**',
      'playwright-report/**',
      'test-results/**',
    ],
  },
  // JS 基础规则
  js.configs.recommended,
  // TypeScript 规则
  ...tseslint.configs.recommended,
  // Vue 3 规则
  ...pluginVue.configs['flat/recommended'],
  // 全局语言选项：最新 ECMAScript + 浏览器全局变量
  {
    languageOptions: {
      ecmaVersion: 'latest',
      globals: {
        ...globals.browser,
        ...globals.node,
      },
    },
  },
  // Vue SFC 中使用 TS 解析器
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
      'no-unused-vars': 'off',
      '@typescript-eslint/no-unused-vars': 'warn',
      '@typescript-eslint/no-explicit-any': 'warn',
      'vue/multi-word-component-names': 'off',
    },
  },
]