<template>
  <div class="mine-page">
    <NavBar />
    <div class="mine-container">
      <h2 class="page-title">我的发布</h2>
      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <el-tab-pane label="全部" name="all" />
        <el-tab-pane label="寻物启事" name="LOST" />
        <el-tab-pane label="招领信息" name="FOUND" />
        <el-tab-pane label="已完结" name="closed" />
      </el-tabs>
      <div v-loading="loading" class="post-list">
        <el-empty v-if="list.length === 0 && !loading" description="还没有发布过信息" />
        <el-card v-for="item in list" :key="item.id" class="post-item" shadow="never">
          <div class="item-main" @click="goDetail(item.id)">
            <div class="item-thumb">
              <img v-if="coverUrl(item)" :src="coverUrl(item)" />
              <el-icon v-else size="32"><Picture /></el-icon>
            </div>
            <div class="item-info">
              <div class="item-title-row">
                <span class="item-title">{{ item.title }}</span>
                <el-tag :type="item.type === 'LOST' ? 'danger' : 'success'" size="small">
                  {{ item.type === 'LOST' ? '寻物' : '招领' }}
                </el-tag>
                <el-tag v-if="item.status === 'CLOSED' || item.status === 'ARCHIVED'" type="info" size="small">已完结</el-tag>
              </div>
              <p class="item-desc">{{ item.categoryName || '未分类' }} · {{ statusText(item.status) }}</p>
              <div class="item-meta">
                <span><el-icon><Location /></el-icon> {{ item.locationName || '未知地点' }}</span>
                <span><el-icon><Clock /></el-icon> {{ formatTime(item.eventTime) }}</span>
              </div>
            </div>
          </div>
          <div class="item-actions">
            <el-button size="small" @click.stop="goDetail(item.id)">查看</el-button>
            <el-button size="small" type="warning" @click.stop="goMatches(item)">匹配结果</el-button>
            <el-button size="small" type="primary" v-if="item.status !== 'CLOSED' && item.status !== 'ARCHIVED'" @click.stop="handleClose(item)">
              标记已找回/归还
            </el-button>
          </div>
        </el-card>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import NavBar from '@/components/NavBar.vue'
import { getMyPosts, closePost } from '@/api/post'
import { ElMessage, ElMessageBox } from 'element-plus'

const router = useRouter()
const activeTab = ref('all')
const allList = ref([])
const list = computed(() => {
  if (activeTab.value === 'all') return allList.value
  if (activeTab.value === 'closed') return allList.value.filter(i => i.status === 'CLOSED' || i.status === 'ARCHIVED')
  return allList.value.filter(i => i.type === activeTab.value)
})
const loading = ref(false)

const API_BASE = import.meta.env.VITE_API_BASE_URL || ''

function coverUrl(item) {
  const p = item.coverImage
  if (!p) return ''
  return p.startsWith('http') ? p : `${API_BASE}${p}`
}

function statusText(s) {
  const map = { OPEN: '进行中', MATCHED: '已匹配', CLAIMING: '认领中', CLOSED: '已完结', ARCHIVED: '已归档' }
  return map[s] || ''
}

function handleTabChange() {
  // 前端过滤
}

onMounted(() => {
  loadList()
})

async function loadList() {
  loading.value = true
  try {
    const res = await getMyPosts({})
    allList.value = res.data || []
  } catch (e) {
    allList.value = getMockData()
  } finally {
    loading.value = false
  }
}

function goDetail(id) {
  router.push(`/detail/${id}`)
}

function goMatches(item) {
  router.push({ path: '/matches', query: { itemId: item.id } })
}

async function handleClose(item) {
  await ElMessageBox.confirm('确认标记该物品已找回/归还吗？', '提示', { type: 'warning' })
  try {
    await closePost(item.id)
    ElMessage.success('已标记完结')
    loadList()
  } catch (e) {
    // 失败由拦截器提示
  }
}

function formatTime(time) {
  if (!time) return ''
  return new Date(time).toLocaleString('zh-CN')
}

function getMockData() {
  return [
    { id: 1, title: '黑色钱包', type: 'LOST', status: 'OPEN', categoryName: '证件卡片', locationName: '图书馆', eventTime: new Date().toISOString(), coverImage: '' },
    { id: 2, title: 'iPhone 14', type: 'FOUND', status: 'CLOSED', categoryName: '电子产品', locationName: '食堂二楼', eventTime: new Date().toISOString(), coverImage: '' }
  ]
}
</script>

<style scoped>
.mine-page {
  min-height: 100vh;
}

.mine-container {
  max-width: 900px;
  margin: 0 auto;
  padding: 24px 20px;
}

.page-title {
  font-size: 22px;
  margin-bottom: 20px;
}

.post-item {
  margin-bottom: 16px;
}

.item-main {
  display: flex;
  gap: 16px;
  cursor: pointer;
}

.item-thumb {
  width: 80px;
  height: 80px;
  border-radius: 6px;
  overflow: hidden;
  background: #f5f7fa;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #c0c4cc;
  flex-shrink: 0;
}

.item-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.item-info {
  flex: 1;
}

.item-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.item-title {
  font-size: 16px;
  font-weight: 500;
}

.item-desc {
  color: #909399;
  font-size: 13px;
  margin-bottom: 8px;
}

.item-meta {
  display: flex;
  gap: 16px;
  font-size: 12px;
  color: #c0c4cc;
}

.item-meta span {
  display: flex;
  align-items: center;
  gap: 4px;
}

.item-actions {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid #f0f0f0;
  display: flex;
  gap: 8px;
}
</style>
