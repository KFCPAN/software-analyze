<template>
  <div class="mine-page">
    <NavBar />
    <div class="mine-container">
      <h2 class="page-title">我的发布</h2>
      <el-tabs v-model="activeTab" @tab-change="loadList">
        <el-tab-pane label="全部" name="all" />
        <el-tab-pane label="寻物启事" name="lost" />
        <el-tab-pane label="招领信息" name="found" />
        <el-tab-pane label="已完结" name="resolved" />
      </el-tabs>
      <div v-loading="loading" class="post-list">
        <el-empty v-if="list.length === 0 && !loading" description="还没有发布过信息" />
        <el-card v-for="item in list" :key="item.id" class="post-item" shadow="never">
          <div class="item-main" @click="goDetail(item.id)">
            <div class="item-thumb">
              <img v-if="item.images && item.images.length" :src="item.images[0]" />
              <el-icon v-else size="32"><Picture /></el-icon>
            </div>
            <div class="item-info">
              <div class="item-title-row">
                <span class="item-title">{{ item.title }}</span>
                <el-tag :type="item.type === 'lost' ? 'danger' : 'success'" size="small">
                  {{ item.type === 'lost' ? '寻物' : '招领' }}
                </el-tag>
                <el-tag v-if="item.status === 'resolved'" type="info" size="small">已完结</el-tag>
              </div>
              <p class="item-desc">{{ item.description }}</p>
              <div class="item-meta">
                <span>{{ item.location }}</span>
                <span>{{ formatTime(item.time) }}</span>
                <span v-if="item.matchCount > 0" class="match-count">
                  <el-icon><Bell /></el-icon> {{ item.matchCount }}条匹配
                </span>
              </div>
            </div>
          </div>
          <div class="item-actions">
            <el-button size="small" @click.stop="goDetail(item.id)">查看</el-button>
            <el-button size="small" type="primary" v-if="item.status !== 'resolved'" @click.stop="handleResolve(item)">
              标记已找回
            </el-button>
            <el-button size="small" type="danger" @click.stop="handleDelete(item)">删除</el-button>
          </div>
        </el-card>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import NavBar from '@/components/NavBar.vue'
import { getMyPosts, deletePost, markResolved } from '@/api/post'
import { ElMessage, ElMessageBox } from 'element-plus'

const router = useRouter()
const activeTab = ref('all')
const list = ref([])
const loading = ref(false)

onMounted(() => {
  loadList()
})

async function loadList() {
  loading.value = true
  try {
    const res = await getMyPosts({ tab: activeTab.value })
    list.value = res.data?.list || []
  } catch (e) {
    list.value = getMockData()
  } finally {
    loading.value = false
  }
}

function goDetail(id) {
  router.push(`/detail/${id}`)
}

async function handleResolve(item) {
  try {
    await markResolved(item.id)
    ElMessage.success('已标记为找回')
    loadList()
  } catch (e) {
    item.status = 'resolved'
    ElMessage.success('已标记（演示模式）')
  }
}

async function handleDelete(item) {
  await ElMessageBox.confirm('确定删除这条发布吗？', '提示', { type: 'warning' })
  try {
    await deletePost(item.id)
    ElMessage.success('已删除')
    loadList()
  } catch (e) {
    list.value = list.value.filter(i => i.id !== item.id)
    ElMessage.success('已删除（演示模式）')
  }
}

function formatTime(time) {
  if (!time) return ''
  return new Date(time).toLocaleString('zh-CN')
}

function getMockData() {
  return [
    { id: 1, title: '黑色钱包', type: 'lost', status: 'active', description: '内有身份证和校园卡', location: '图书馆', time: new Date().toISOString(), matchCount: 2, images: [] },
    { id: 2, title: 'iPhone 14', type: 'found', status: 'resolved', description: '屏幕有裂痕', location: '食堂二楼', time: new Date().toISOString(), matchCount: 0, images: [] }
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

.match-count {
  color: #e6a23c;
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
