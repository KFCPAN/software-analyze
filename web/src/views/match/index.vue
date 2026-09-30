<template>
  <div class="match-page">
    <NavBar />
    <div class="match-container">
      <div class="page-header">
        <h2 class="page-title">智能匹配结果</h2>
        <el-button text @click="$router.push('/post/mine')">← 返回我的发布</el-button>
      </div>
      <p class="page-sub">基于「文本 + 时间 + 地点 + 图像」多因子综合打分，按总分降序排列</p>

      <div v-loading="loading" class="match-list">
        <el-empty v-if="list.length === 0 && !loading" description="暂无匹配候选，发布新信息后将自动匹配" />

        <el-card v-for="m in list" :key="m.matchId" class="match-card" shadow="never">
          <div class="match-main">
            <div class="candidate">
              <el-image v-if="m.item?.coverImage" :src="fullUrl(m.item.coverImage)" fit="cover" class="cand-img" />
              <div v-else class="cand-img cand-placeholder">
                <el-icon size="24"><Picture /></el-icon>
              </div>
              <div class="cand-info">
                <div class="cand-title">{{ m.item?.title || '未命名物品' }}</div>
                <div class="cand-meta">
                  <el-tag size="small" type="info">{{ m.item?.locationName || '未知地点' }}</el-tag>
                  <span class="cand-time">{{ formatTime(m.item?.eventTime) }}</span>
                </div>
              </div>
            </div>
            <div class="score-panel">
              <div class="total-score">
                <span class="score-num">{{ (m.totalScore * 100).toFixed(1) }}</span>
                <span class="score-unit">综合分</span>
              </div>
            </div>
          </div>

          <!-- 各因子得分 -->
          <div class="factors">
            <div class="factor" v-for="f in factorsOf(m)" :key="f.key">
              <span class="f-label">{{ f.label }}</span>
              <el-progress :percentage="f.value" :stroke-width="8" :color="f.color" />
            </div>
          </div>

          <!-- 反馈 -->
          <div class="match-actions">
            <template v-if="m.status === 'PENDING'">
              <el-button type="success" plain @click="feedback(m, true)">就是它，我要认领</el-button>
              <el-button type="danger" plain @click="feedback(m, false)">不是它</el-button>
            </template>
            <template v-else>
              <el-tag :type="m.status === 'CONFIRMED' ? 'success' : 'info'" size="small">
                {{ m.status === 'CONFIRMED' ? '已确认' : '已排除' }}
              </el-tag>
            </template>
          </div>
        </el-card>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import NavBar from '@/components/NavBar.vue'
import { getMatches, feedbackMatch } from '@/api/match'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()
const list = ref([])
const loading = ref(false)

// 图片地址补全（后端返回 /files/xxx 相对路径）
const API_BASE = import.meta.env.VITE_API_BASE_URL || ''
function fullUrl(path) {
  if (!path) return ''
  return path.startsWith('http') ? path : `${API_BASE}${path}`
}

function factorsOf(m) {
  return [
    { key: 'text', label: '文本相似', value: Math.round((m.textScore || 0) * 100), color: '#409eff' },
    { key: 'time', label: '时间吻合', value: Math.round((m.timeScore || 0) * 100), color: '#67c23a' },
    { key: 'location', label: '地点吻合', value: Math.round((m.locationScore || 0) * 100), color: '#e6a23c' },
    { key: 'image', label: '图像相似', value: Math.round((m.imageScore || 0) * 100), color: '#f56c6c' }
  ]
}

function formatTime(t) {
  if (!t) return ''
  return new Date(t).toLocaleString('zh-CN', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}

onMounted(() => {
  loadMatches()
})

async function loadMatches() {
  const itemId = route.query.itemId
  if (!itemId) {
    ElMessage.warning('缺少 itemId 参数')
    return
  }
  loading.value = true
  try {
    const res = await getMatches(itemId)
    list.value = res.data || []
  } catch (e) {
    // 兜底演示数据（接口不可达时）
    list.value = [
      {
        matchId: 1,
        item: { id: 1, title: '黑色长款钱包', coverImage: '', locationName: '图书馆三层', eventTime: new Date().toISOString() },
        textScore: 0.92, imageScore: 0.85, timeScore: 0.78, locationScore: 0.9, totalScore: 0.89, status: 'PENDING'
      },
      {
        matchId: 2,
        item: { id: 2, title: '黑色零钱包', coverImage: '', locationName: '图书馆一层', eventTime: new Date().toISOString() },
        textScore: 0.71, imageScore: 0.0, timeScore: 0.6, locationScore: 0.65, totalScore: 0.62, status: 'PENDING'
      }
    ]
  } finally {
    loading.value = false
  }
}

async function feedback(m, confirm) {
  try {
    await feedbackMatch(m.matchId, confirm)
  } catch (e) {}
  m.status = confirm ? 'CONFIRMED' : 'REJECTED'
  ElMessage.success(confirm ? '已确认，请前往详情页发起认领' : '已记录反馈，将优化匹配结果')
  if (confirm) {
    router.push(`/detail/${m.item.id}`)
  }
}
</script>

<style scoped>
.match-page {
  min-height: 100vh;
  background: #f5f7fa;
}

.match-container {
  max-width: 760px;
  margin: 0 auto;
  padding: 24px 20px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.page-title {
  font-size: 22px;
  margin: 0;
}

.page-sub {
  color: #909399;
  font-size: 13px;
  margin: 8px 0 20px;
}

.match-card {
  margin-bottom: 16px;
  border-radius: 10px;
}

.match-main {
  display: flex;
  justify-content: space-between;
  gap: 16px;
}

.candidate {
  display: flex;
  gap: 12px;
  flex: 1;
  min-width: 0;
}

.cand-img {
  width: 72px;
  height: 72px;
  border-radius: 8px;
  flex-shrink: 0;
}

.cand-placeholder {
  background: #f0f2f5;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #c0c4cc;
}

.cand-info {
  min-width: 0;
}

.cand-title {
  font-size: 16px;
  font-weight: 500;
  margin-bottom: 8px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cand-meta {
  display: flex;
  align-items: center;
  gap: 8px;
}

.cand-time {
  font-size: 12px;
  color: #909399;
}

.score-panel {
  flex-shrink: 0;
  text-align: center;
}

.total-score {
  display: flex;
  align-items: baseline;
  gap: 4px;
}

.score-num {
  font-size: 28px;
  font-weight: bold;
  color: #409eff;
}

.score-unit {
  font-size: 12px;
  color: #909399;
}

.factors {
  margin-top: 14px;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px 20px;
}

.factor {
  display: flex;
  align-items: center;
  gap: 8px;
}

.f-label {
  width: 64px;
  font-size: 13px;
  color: #606266;
  flex-shrink: 0;
}

.match-actions {
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px dashed #ebeef5;
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>
