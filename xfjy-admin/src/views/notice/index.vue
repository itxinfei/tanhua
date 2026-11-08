<template>
  <div class="page-container">
    <PageHeader
      title="公告管理"
      :subtitle="`共 ${total} 条记录`"
    >
      <template #actions>
        <el-button
          v-perm="['SUPER_ADMIN','ADMIN']"
          type="primary"
          :icon="Plus"
          @click="handleAdd"
        >
          新增公告
        </el-button>
      </template>
    </PageHeader>

    <SearchBar
      :model="query"
      :filter-meta="{
        type: { label: '类型', options: { SYSTEM: '系统公告', ACTIVITY: '活动公告', UPDATE: '更新公告' } },
        status: { label: '状态', options: { DRAFT: '草稿', PUBLISHED: '已发布', OFFLINE: '已下线' } },
      }"
      @search="handleSearch"
      @reset="handleReset"
    >
      <el-form-item label="标题">
        <el-input
          v-model="query.title"
          placeholder="公告标题"
          clearable
          style="width: 180px"
          @keyup.enter="handleSearch"
        />
      </el-form-item>
      <el-form-item label="类型">
        <el-select
          v-model="query.type"
          placeholder="全部"
          clearable
          style="width: 120px"
        >
          <el-option
            label="系统公告"
            value="SYSTEM"
          />
          <el-option
            label="活动公告"
            value="ACTIVITY"
          />
          <el-option
            label="更新公告"
            value="UPDATE"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select
          v-model="query.status"
          placeholder="全部"
          clearable
          style="width: 120px"
        >
          <el-option
            label="草稿"
            value="DRAFT"
          />
          <el-option
            label="已发布"
            value="PUBLISHED"
          />
          <el-option
            label="已下线"
            value="OFFLINE"
          />
        </el-select>
      </el-form-item>
    </SearchBar>

    <TableCard :skeleton="loading && !tableData.length">
      <el-table
        v-loading="loading"
        :data="tableData"
        stripe
        border
        style="width: 100%"
      >
        <el-table-column
          v-if="!isMobile"
          prop="id"
          label="ID"
          width="70"
          align="center"
        />
        <el-table-column
          prop="title"
          label="标题"
          min-width="200"
          show-overflow-tooltip
        />
        <el-table-column
          label="类型"
          width="100"
          align="center"
        >
          <template #default="{ row }">
            <el-tag
              :type="typeColor(row.type)"
              size="small"
            >
              {{ typeLabel(row.type) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          label="状态"
          width="90"
          align="center"
        >
          <template #default="{ row }">
            <StatusTag
              :status="row.status"
              :mapping="noticeStatusMapping"
            />
          </template>
        </el-table-column>
        <el-table-column
          v-if="!isMobile"
          prop="publisher"
          label="发布人"
          width="100"
        />
        <el-table-column
          v-if="!isMobile"
          prop="publishedAt"
          label="发布时间"
          width="170"
        >
          <template #default="{ row }">
            {{ formatDateTime(row.publishedAt) }}
          </template>
        </el-table-column>
        <el-table-column
          v-if="!isMobile"
          label="创建时间"
          width="170"
        >
          <template #default="{ row }">
            {{ formatDateTime(row.createdAt || row.created) }}
          </template>
        </el-table-column>
        <el-table-column
          label="操作"
          width="280"
          fixed="right"
          align="center"
        >
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              @click="handlePreview(row)"
            >
              预览
            </el-button>
            <el-button
              v-perm="['SUPER_ADMIN','ADMIN']"
              link
              type="primary"
              @click="handleEdit(row)"
            >
              编辑
            </el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              v-perm="['SUPER_ADMIN','ADMIN']"
              link
              type="success"
              @click="handlePublish(row)"
            >
              发布
            </el-button>
            <el-button
              v-if="row.status === 'OFFLINE'"
              v-perm="['SUPER_ADMIN','ADMIN']"
              link
              type="success"
              @click="handlePublish(row)"
            >
              重新发布
            </el-button>
            <el-button
              v-if="row.status === 'PUBLISHED'"
              v-perm="['SUPER_ADMIN','ADMIN']"
              link
              type="warning"
              @click="handleOffline(row)"
            >
              下线
            </el-button>
            <el-button
              v-perm="['SUPER_ADMIN','ADMIN']"
              link
              type="danger"
              @click="handleDelete(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <TableEmpty
            description="暂无公告数据"
            hint="点击右上角「新增公告」发布第一条公告"
          />
        </template>
      </el-table>
      <template #pagination>
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="loadData"
        />
      </template>
    </TableCard>

    <!-- 新增/编辑弹窗 -->
    <el-dialog
      v-model="formVisible"
      :title="editingId ? '编辑公告' : '新增公告'"
      width="600px"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="formRules"
        label-width="80px"
      >
        <el-form-item
          label="标题"
          prop="title"
        >
          <el-input
            v-model="form.title"
            placeholder="请输入公告标题"
            maxlength="50"
            show-word-limit
          />
        </el-form-item>
        <el-form-item
          label="类型"
          prop="type"
        >
          <el-select
            v-model="form.type"
            placeholder="请选择公告类型"
            style="width: 100%"
          >
            <el-option
              label="系统公告"
              value="SYSTEM"
            />
            <el-option
              label="活动公告"
              value="ACTIVITY"
            />
            <el-option
              label="更新公告"
              value="UPDATE"
            />
          </el-select>
        </el-form-item>
        <el-form-item
          label="内容"
          prop="content"
        >
          <div class="rich-editor">
            <div class="rich-editor__toolbar">
              <el-button-group>
                <el-button
                  size="small"
                  title="加粗"
                  @click="execCmd('bold')"
                >
                  <b>B</b>
                </el-button>
                <el-button
                  size="small"
                  title="斜体"
                  @click="execCmd('italic')"
                >
                  <i>I</i>
                </el-button>
              </el-button-group>
              <el-button-group>
                <el-button
                  size="small"
                  title="无序列表"
                  @click="execCmd('insertUnorderedList')"
                >
                  • 列表
                </el-button>
                <el-button
                  size="small"
                  title="有序列表"
                  @click="execCmd('insertOrderedList')"
                >
                  1. 列表
                </el-button>
              </el-button-group>
              <el-button
                size="small"
                title="插入链接"
                @click="insertLink"
              >
                链接
              </el-button>
              <el-button
                size="small"
                title="清除格式"
                @click="execCmd('removeFormat')"
              >
                清除
              </el-button>
            </div>
            <div
              ref="editorRef"
              class="rich-editor__body"
              contenteditable="true"
              data-placeholder="请输入公告内容"
              @input="syncEditorToForm"
            />
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="formSubmitting"
          @click="confirmSave"
        >
          保存
        </el-button>
      </template>
    </el-dialog>

    <!-- 内容预览弹窗 -->
    <el-dialog
      v-model="previewVisible"
      title="公告预览"
      width="640px"
    >
      <div
        v-if="previewData"
        class="preview-content"
      >
        <h2 class="preview-title">
          {{ previewData.title }}
        </h2>
        <div class="preview-meta">
          <el-tag
            :type="typeColor(previewData.type)"
            size="small"
          >
            {{ typeLabel(previewData.type) }}
          </el-tag>
          <el-tag
            :type="statusColor(previewData.status)"
            size="small"
          >
            {{ statusLabel(previewData.status) }}
          </el-tag>
          <span class="preview-publisher">{{ previewData.publisher }}</span>
          <span class="preview-time">{{ formatDateTime(previewData.publishedAt || previewData.createdAt || previewData.created) }}</span>
        </div>
        <el-divider />
        <div
          class="preview-body"
          v-html="previewData.content"
        />
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, watch, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { PageHeader, SearchBar, TableCard, TableEmpty, StatusTag } from '@/components'
import { useResponsive } from '@/composables/useResponsive'
import {
  getNoticePageApi,
  createNoticeApi,
  updateNoticeApi,
  publishNoticeApi,
  offlineNoticeApi,
  deleteNoticeApi,
} from '@/api/notice'
import type { NoticeItem, NoticeQuery, NoticeForm } from '@/api/notice'
import { formatDateTime } from '@/utils/format'

const loading = ref(false)
const tableData = ref<NoticeItem[]>([])
const total = ref(0)

const query = reactive<NoticeQuery>({
  page: 1,
  pageSize: 10,
  title: '',
  type: '',
  status: '',
})

// 响应式列可见性：移动端隐藏次要列（ID/发布人/发布时间/创建时间）
const { isMobile } = useResponsive()

// Mock 数据
function getMockNotices(page: number, pageSize: number): { total: number; list: NoticeItem[] } {
  const data: NoticeItem[] = [
    { id: 1, title: '心飞交友 v2.0 新版本上线公告', content: '全新匹配算法，更快找到心仪对象', type: 'UPDATE', status: 'PUBLISHED', publisher: '超级管理员', publishedAt: '2026-10-20 10:00:00', createdAt: '2026-10-19 15:00:00' },
    { id: 2, title: '双十一脱单活动开启', content: '活动期间开通VIP享7折优惠，快来参与', type: 'ACTIVITY', status: 'PUBLISHED', publisher: '超级管理员', publishedAt: '2026-10-18 09:00:00', createdAt: '2026-10-17 14:00:00' },
    { id: 3, title: '平台安全规范更新通知', content: '为保障用户安全，平台更新了安全规范', type: 'SYSTEM', status: 'PUBLISHED', publisher: '超级管理员', publishedAt: '2026-10-15 11:00:00', createdAt: '2026-10-14 16:00:00' },
    { id: 4, title: '国庆假期活动回顾', content: '感谢大家参与国庆活动', type: 'ACTIVITY', status: 'OFFLINE', publisher: '运营专员', publishedAt: '2026-10-08 10:00:00', createdAt: '2026-10-07 09:00:00' },
    { id: 5, title: '新功能预告：视频通话', content: '即将上线1v1视频通话功能', type: 'UPDATE', status: 'DRAFT', publisher: '超级管理员', publishedAt: undefined, createdAt: '2026-10-21 17:00:00' },
  ]
  const start = (page - 1) * pageSize
  return { total: data.length, list: data.slice(start, start + pageSize) }
}

async function loadData() {
  loading.value = true
  try {
    const res = await getNoticePageApi(query)
    tableData.value = res.list
    total.value = res.total
  } catch {
    // 拦截器已统一提示
    // 开发环境可用 mock 数据兜底，生产环境保留上次数据
    if (import.meta.env.DEV) {
      const mock = getMockNotices(query.page, query.pageSize)
      tableData.value = mock.list
      total.value = mock.total
    }
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.page = 1
  loadData()
}

function handleReset() {
  query.title = ''
  query.type = ''
  query.status = ''
  query.page = 1
  loadData()
}

/** 切换每页条数时回到第 1 页 */
function handleSizeChange() {
  query.page = 1
  loadData()
}

function typeLabel(type: string) {
  const map: Record<string, string> = { SYSTEM: '系统公告', ACTIVITY: '活动公告', UPDATE: '更新公告' }
  return map[type] || type
}

function typeColor(type: string) {
  const map: Record<string, string> = { SYSTEM: 'primary', ACTIVITY: 'warning', UPDATE: 'success' }
  return map[type] || 'info'
}

// 公告状态映射
const noticeStatusMapping = {
  DRAFT: { label: '草稿', type: 'info' as const },
  PUBLISHED: { label: '已发布', type: 'success' as const },
  OFFLINE: { label: '已下线', type: 'danger' as const },
}

function statusLabel(status: string) {
  const map: Record<string, string> = { DRAFT: '草稿', PUBLISHED: '已发布', OFFLINE: '已下线' }
  return map[status] || status
}

function statusColor(status: string) {
  const map: Record<string, string> = { DRAFT: 'info', PUBLISHED: 'success', OFFLINE: 'danger' }
  return map[status] || 'info'
}

// 新增/编辑
const formVisible = ref(false)
const formRef = ref<FormInstance>()
const editingId = ref<number | null>(null)
const formSubmitting = ref(false)

const form = reactive<NoticeForm>({
  title: '',
  content: '',
  type: 'SYSTEM',
})

const formRules: FormRules = {
  title: [{ required: true, message: '请输入公告标题', trigger: 'blur' }],
  type: [{ required: true, message: '请选择公告类型', trigger: 'change' }],
  content: [{ required: true, message: '请输入公告内容', trigger: 'blur' }],
}

// ---- 轻量富文本编辑器（contenteditable + execCommand，无第三方依赖） ----
const editorRef = ref<HTMLDivElement | null>(null)

function execCmd(cmd: string): void {
  document.execCommand(cmd)
  editorRef.value?.focus()
  syncEditorToForm()
}

function insertLink(): void {
  const url = window.prompt('请输入链接地址（含 https://）')
  if (url) {
  document.execCommand('createLink', false, url)
  syncEditorToForm()
  }
}

function syncEditorToForm(): void {
  if (editorRef.value) {
  form.content = editorRef.value.innerHTML
  }
}

// 弹窗打开时将 form.content 回填到编辑区
watch(formVisible, (visible) => {
  if (visible) {
  nextTick(() => {
    if (editorRef.value) {
    editorRef.value.innerHTML = form.content
    }
  })
  }
})

function handleAdd() {
  editingId.value = null
  form.title = ''
  form.content = ''
  form.type = 'SYSTEM'
  formVisible.value = true
}

function handleEdit(row: NoticeItem) {
  editingId.value = row.id
  form.title = row.title
  form.content = row.content
  form.type = row.type
  formVisible.value = true
}

async function confirmSave() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid || formSubmitting.value) return
    formSubmitting.value = true
    try {
      if (editingId.value) {
        await updateNoticeApi(editingId.value, form)
        ElMessage.success('更新成功')
      } else {
        await createNoticeApi(form)
        ElMessage.success('创建成功')
      }
      formVisible.value = false
      loadData()
    } catch {
      // 错误已由拦截器处理
    } finally {
      formSubmitting.value = false
    }
  })
}

async function handlePublish(row: NoticeItem) {
  try {
    await ElMessageBox.confirm(`确定发布公告「${row.title}」吗？`, '提示', { type: 'success' })
    await publishNoticeApi(row.id)
    ElMessage.success('发布成功')
    // 重新拉取列表，保证发布时间/状态与服务端一致
    loadData()
  } catch {
    // 取消
  }
}

async function handleOffline(row: NoticeItem) {
  try {
    await ElMessageBox.confirm(`确定下线公告「${row.title}」吗？`, '提示', { type: 'warning' })
    await offlineNoticeApi(row.id)
    ElMessage.success('已下线')
    loadData()
  } catch {
    // 取消
  }
}

async function handleDelete(row: NoticeItem) {
  try {
    await ElMessageBox.confirm(`确定删除公告「${row.title}」吗？删除后不可恢复`, '警告', { type: 'error' })
    await deleteNoticeApi(row.id)
    ElMessage.success('已删除')
    loadData()
  } catch {
    // 取消
  }
}

// 预览
const previewVisible = ref(false)
const previewData = ref<NoticeItem | null>(null)

function handlePreview(row: NoticeItem) {
  previewData.value = row
  previewVisible.value = true
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.preview-title {
  font-size: var(--xfjy-font-size-lg);
  font-weight: 600;
  margin: 0 0 var(--xfjy-space-3);
  color: var(--xfjy-text-primary);
}

.preview-meta {
  display: flex;
  align-items: center;
  gap: var(--xfjy-space-2);
  font-size: var(--xfjy-font-size-sm);
  color: var(--xfjy-text-secondary);
}

.preview-publisher {
  color: var(--xfjy-text-regular);
}

.preview-time {
  margin-left: auto;
}

.preview-body {
  font-size: var(--xfjy-font-size-base);
  line-height: 1.8;
  color: var(--xfjy-text-primary);
  word-break: break-all;
  min-height: 120px;
}

/* 轻量富文本编辑器 */
.rich-editor {
  width: 100%;
  border: 1px solid var(--el-border-color);
  border-radius: var(--el-border-radius-base);
  overflow: hidden;
}
.rich-editor__toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: var(--xfjy-space-2);
  padding: var(--xfjy-space-1) var(--xfjy-space-2);
  border-bottom: 1px solid var(--el-border-color-light);
  background: var(--xfjy-bg-hover);
}
.rich-editor__body {
  min-height: 160px;
  max-height: 320px;
  overflow-y: auto;
  padding: var(--xfjy-space-3);
  font-size: var(--xfjy-font-size-base);
  line-height: 1.6;
  outline: none;
  &:empty::before {
    content: attr(data-placeholder);
    color: var(--xfjy-text-disabled);
    pointer-events: none;
  }
  &:focus {
    border-color: var(--xfjy-primary);
  }
  /* 富文本内容排版 */
  :deep(ul), :deep(ol) {
    padding-left: 1.5em;
  }
  :deep(a) {
    color: var(--xfjy-primary);
  }
}
</style>