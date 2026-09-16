<template>
  <div class="detail-page">
    <NavBar />
    <div class="detail-container">
      <div v-loading="loading" class="detail-content">
        <div class="detail-header">
          <el-tag :type="detail.type === 'lost' ? 'danger' : 'success'" size="large">
            {{ detail.type === 'lost' ? '寻物启事' : '招领信息' }}
          </el-tag>
          <h1 class="detail-title">{{ detail.title }}</h1>
          <div class="detail-meta">
            <span><el-icon><Location /></el-icon> {{ detail.location }}</span>
            <span><el-icon><Clock /></el-icon> {{ formatTime(detail.time) }}</span>
            <span><el-icon><User /></el-icon> {{ detail.publisher }}</span>
            <el-tag size="small" type="info">{{ detail.category }}</el-tag>
          </div>
        </div>

        <div class="detail-body">
          <div class="image-section" v-if="detail.images && detail.images.length">
            <el-image
              v-for="(img, idx) in detail.images"
              :key="idx"
              :src="img"
              :preview-src-list="detail.images"
              fit="cover"
              class="detail-image"
            />
          </div>

          <div class="info-section">
            <h3>物品描述</h3>
            <p class="description">{{ detail.description }}</p>

            <div class="info-grid" v-if="detail.type === 'found'">
              <el-alert title="关键特征已隐藏，认领时需回答验证问题" type="warning" :closable="false" show-icon />
            </div>

            <div class="contact-section" v-if="detail.contactVisible">
              <h3>联系方式</h3>
              <p>{{ detail.contact }}</p>
            </div>

            <div class="action-buttons">
              <el-button v-if="detail.type === 'found' && detail.status === 'active'" type="primary" size="large" @click="goClaim">
                <el-icon><Pointer /></el-icon> 我要认领
              </el-button>
              <el-button v-if="detail.type === 'lost' && detail.status === 'active'" type="success" size="large" @click="handleFound">
                我找到了，发布招领
              </el-button>
              <el-button size="large" @click="$router.back()">返回</el-button>
            </div>
          </div>
        </div>

        <!-- 智能匹配候选 -->
        <div class="match-section" v-if="matches.length > 0">
          <h3><el-icon><MagicStick /></el-icon> 智能匹配候选</h3>
          <div class="match-list">
            <div v-for="m in matches" :key="m.id" class="match-item" @click="$router.push(`/detail/${m.id}`)">
              <div class="match-thumb">
                <img v-if="m.images && m.images.length" :src="m.images[0]" />
                <el-icon v-else><Picture /></el-icon>
              </div>
              <div class="match-info">
                <div class="match-title">{{ m.title }}</div>
                <div class="match-score">
                  匹配度 {{ (m.score * 100).toFixed(0) }}%
                  <el-progress :percentage="m.score * 100" :show-text="false" :stroke-width="6" />
                </div>
              </div>
              <div class="match-actions" @click.stop>
                <el-button size="small" type="success" @click="confirmMatch(m)">是它</el-button>
                <el-button size="small" @click="denyMatch(m)">不是</el-button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import NavBar from '@/components/NavBar.vue'
import { getPostDetail } from '@/api/post'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const detail = ref({})
const matches = ref([])

onMounted(() => {
  loadDetail()
})

async function loadDetail() {
  loading.value = true
  try {
    const res = await getPostDetail(route.params.id)
    detail.value = res.data
    matches.value = res.data.matches || []
  } catch (e) {
    detail.value = {
      id: route.params.id,
      title: '黑色钱包',
      type: 'lost',
      status: 'active',
      category: '证件卡片',
      location: '图书馆三楼',
      time: new Date().toISOString(),
      publisher: '张三',
      description: '在图书馆三楼自习室丢失，黑色皮质钱包，内有身份证、校园卡和少量现金。校园卡上有姓名，有看到的同学请联系我，必有重谢！',
      images: [],
      contactVisible: false
    }
    matches.value = [
      { id: 101, title: '捡到黑色钱包', score: 0.92, images: [] },
      { id: 102, title: '食堂捡到钱包一个', score: 0.75, images: [] }
    ]
  } finally {
    loading.value = false
  }
}

function goClaim() {
  router.push(`/claim/${route.params.id}`)
}

function handleFound() {
  router.push('/post/found')
}

function confirmMatch(m) {
  ElMessage.success('已确认，正在为您跳转到认领流程')
  router.push(`/claim/${m.id}`)
}

function denyMatch(m) {
  matches.value = matches.value.filter(item => item.id !== m.id)
  ElMessage.info('已记录，后续会减少类似推荐')
}

function formatTime(time) {
  if (!time) return ''
  return new Date(time).toLocaleString('zh-CN')
}
</script>

<style scoped>
.detail-page {
  min-height: 100vh;
}

.detail-container {
  max-width: 900px;
  margin: 0 auto;
  padding: 24px 20px;
}

.detail-content {
  background: #fff;
  border-radius: 8px;
  padding: 32px;
}

.detail-header {
  margin-bottom: 24px;
  padding-bottom: 20px;
  border-bottom: 1px solid #f0f0f0;
}

.detail-title {
  font-size: 24px;
  margin: 12px 0;
}

.detail-meta {
  display: flex;
  gap: 20px;
  color: #909399;
  font-size: 14px;
  align-items: center;
}

.detail-meta span {
  display: flex;
  align-items: center;
  gap: 4px;
}

.image-section {
  display: flex;
  gap: 12px;
  margin-bottom: 24px;
  flex-wrap: wrap;
}

.detail-image {
  width: 200px;
  height: 200px;
  border-radius: 6px;
}

.info-section h3 {
  font-size: 16px;
  margin-bottom: 12px;
  color: #303133;
}

.description {
  line-height: 1.8;
  color: #606266;
  margin-bottom: 20px;
}

.info-grid {
  margin-bottom: 20px;
}

.contact-section {
  margin: 20px 0;
  padding: 16px;
  background: #f0f9eb;
  border-radius: 6px;
}

.action-buttons {
  margin-top: 24px;
  display: flex;
  gap: 12px;
}

.match-section {
  margin-top: 32px;
  padding-top: 24px;
  border-top: 1px solid #f0f0f0;
}

.match-section h3 {
  margin-bottom: 16px;
  display: flex;
  align-items: center;
  gap: 8px;
  color: #409eff;
}

.match-item {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  margin-bottom: 12px;
  cursor: pointer;
  transition: all 0.2s;
}

.match-item:hover {
  border-color: #409eff;
  box-shadow: 0 2px 12px rgba(64, 158, 255, 0.1);
}

.match-thumb {
  width: 60px;
  height: 60px;
  border-radius: 6px;
  overflow: hidden;
  background: #f5f7fa;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #c0c4cc;
  flex-shrink: 0;
}

.match-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.match-info {
  flex: 1;
}

.match-title {
  font-weight: 500;
  margin-bottom: 6px;
}

.match-score {
  font-size: 12px;
  color: #909399;
  display: flex;
  align-items: center;
  gap: 8px;
}

.match-score .el-progress {
  width: 120px;
}
</style>
