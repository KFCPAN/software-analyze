<template>
  <div class="detail-page">
    <NavBar />
    <div class="detail-container">
      <div v-loading="loading" class="detail-content">
        <div class="detail-header">
          <el-tag :type="detail.type === 'LOST' ? 'danger' : 'success'" size="large">
            {{ detail.type === 'LOST' ? '寻物启事' : '招领信息' }}
          </el-tag>
          <h1 class="detail-title">{{ detail.title }}</h1>
          <div class="detail-meta">
            <span><el-icon><Location /></el-icon> {{ detail.locationName || '未知地点' }}</span>
            <span><el-icon><Clock /></el-icon> {{ formatTime(detail.eventTime) }}</span>
            <span><el-icon><User /></el-icon> {{ publisherName }}</span>
            <el-tag size="small" type="info">{{ detail.categoryName || '未分类' }}</el-tag>
          </div>
        </div>

        <div class="detail-body">
          <div class="image-section" v-if="detailImages.length">
            <el-image
              v-for="(img, idx) in detailImages"
              :key="idx"
              :src="img"
              :preview-src-list="detailImages"
              fit="cover"
              class="detail-image"
            />
          </div>

          <div class="info-section">
            <h3>物品描述</h3>
            <p class="description">{{ detail.description }}</p>

            <div class="info-grid" v-if="detail.type === 'FOUND'">
              <el-alert title="关键特征已隐藏，认领时需回答验证问题" type="warning" :closable="false" show-icon />
            </div>

            <!-- 发布者信息卡片 -->
            <div class="publisher-card">
              <el-avatar :size="44">{{ publisherName.charAt(0) }}</el-avatar>
              <div class="publisher-info">
                <div class="publisher-name">{{ publisherName }}</div>
                <div class="publisher-meta">
                  <el-tag v-if="publisherCredit !== null" size="small" type="warning" effect="plain">
                    信用分 {{ publisherCredit }}
                  </el-tag>
                  <span v-if="publisherVerified" class="verified-tag">
                    <el-icon><CircleCheck /></el-icon> 已认证
                  </span>
                </div>
              </div>
              <div class="publisher-contact">
                <template v-if="contactVisible && contactText">
                  <el-icon><Phone /></el-icon> {{ contactText }}
                </template>
                <template v-else>
                  <el-tag type="info" effect="plain" size="small">联系方式已隐藏，匹配/认领成功后展示</el-tag>
                </template>
              </div>
            </div>

            <div class="action-buttons">
              <el-button v-if="detail.type === 'FOUND' && isOpen" type="primary" size="large" @click="goClaim">
                <el-icon><Pointer /></el-icon> 我要认领
              </el-button>
              <el-button v-if="detail.type === 'LOST' && isOpen" type="success" size="large" @click="handleFound">
                我找到了，发布招领
              </el-button>
              <el-button size="large" @click="$router.back()">返回</el-button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import NavBar from '@/components/NavBar.vue'
import { getPostDetail } from '@/api/post'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const detail = ref({})

const API_BASE = import.meta.env.VITE_API_BASE_URL || ''

const detailImages = computed(() => {
  const imgs = detail.value.images || []
  return imgs.map(p => (p.startsWith('http') ? p : `${API_BASE}${p}`))
})

const isOpen = computed(() => ['OPEN', 'MATCHED', 'CLAIMING'].includes(detail.value.status))

// 发布者信息（容错：publisher 可能是字符串或对象）
const publisherName = computed(() => {
  const p = detail.value.publisher
  if (!p) return '匿名用户'
  if (typeof p === 'string') return p
  return p.nickname || p.username || '匿名用户'
})
const publisherCredit = computed(() => {
  const p = detail.value.publisher
  if (!p || typeof p === 'string') return null
  return p.creditScore ?? p.credit ?? null
})
const publisherVerified = computed(() => {
  const p = detail.value.publisher
  return !!(p && typeof p === 'object' && p.verified)
})
// contactVisible 容错：字段缺失/为 false 时都不展示联系方式
const contactVisible = computed(() => {
  if (typeof detail.value.contactVisible === 'boolean') return detail.value.contactVisible
  return false
})
const contactText = computed(() => {
  const p = detail.value.publisher
  if (p && typeof p === 'object') return p.phone || p.email || ''
  return detail.value.contact || ''
})

onMounted(() => {
  loadDetail()
})

async function loadDetail() {
  loading.value = true
  try {
    const res = await getPostDetail(route.params.id)
    detail.value = res.data
  } catch (e) {
    detail.value = {
      id: route.params.id,
      title: '黑色钱包',
      type: 'LOST',
      status: 'OPEN',
      categoryName: '证件卡片',
      locationName: '图书馆三楼',
      eventTime: new Date().toISOString(),
      publisher: { id: 1, nickname: '张三', creditScore: 96, verified: true, phone: '138****1234' },
      description: '在图书馆三楼自习室丢失，黑色皮质钱包，内有身份证、校园卡和少量现金。校园卡上有姓名，有看到的同学请联系我，必有重谢！',
      images: [],
      contactVisible: false
    }
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

.publisher-card {
  margin: 20px 0;
  padding: 16px;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  display: flex;
  align-items: center;
  gap: 12px;
  background: #fafafa;
}

.publisher-info {
  flex: 1;
}

.publisher-name {
  font-weight: 600;
  font-size: 15px;
  margin-bottom: 4px;
}

.publisher-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: #909399;
}

.verified-tag {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  color: #67c23a;
}

.publisher-contact {
  font-size: 13px;
  color: #606266;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.action-buttons {
  margin-top: 24px;
  display: flex;
  gap: 12px;
}
</style>
