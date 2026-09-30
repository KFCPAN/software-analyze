<template>
  <el-card class="item-card" shadow="hover" @click="handleClick">
    <div class="card-image">
      <img v-if="coverUrl" :src="coverUrl" :alt="item.title" />
      <div v-else class="image-placeholder">
        <el-icon size="48"><Picture /></el-icon>
      </div>
      <el-tag :type="item.type === 'LOST' ? 'danger' : 'success'" size="small" class="type-tag">
        {{ item.type === 'LOST' ? '寻物' : '招领' }}
      </el-tag>
      <el-tag v-if="item.status === 'CLOSED' || item.status === 'ARCHIVED'" type="info" size="small" class="status-tag">
        已完结
      </el-tag>
    </div>
    <div class="card-content">
      <h3 class="card-title">{{ item.title }}</h3>
      <p class="card-desc">{{ statusText }}</p>
      <div class="card-meta">
        <span><el-icon><Location /></el-icon> {{ item.locationName || '未知地点' }}</span>
        <span><el-icon><Clock /></el-icon> {{ formatTime(item.eventTime) }}</span>
      </div>
      <div class="card-footer">
        <el-tag size="small" type="info">{{ item.categoryName || '未分类' }}</el-tag>
        <span class="publisher">{{ item.createdAt ? formatDay(item.createdAt) : '' }}</span>
      </div>
    </div>
  </el-card>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'

const props = defineProps({
  item: {
    type: Object,
    required: true
  }
})

const router = useRouter()

const API_BASE = import.meta.env.VITE_API_BASE_URL || ''
const coverUrl = computed(() => {
  const p = props.item.coverImage
  if (!p) return ''
  return p.startsWith('http') ? p : `${API_BASE}${p}`
})

const statusText = computed(() => {
  const s = props.item.status
  const map = { OPEN: '进行中', MATCHED: '已匹配', CLAIMING: '认领中', CLOSED: '已完结', ARCHIVED: '已归档' }
  return (props.item.type === 'LOST' ? '寻找中' : '等待认领') + ' · ' + (map[s] || '')
})

function handleClick() {
  router.push(`/detail/${props.item.id}`)
}

function formatTime(time) {
  if (!time) return ''
  const date = new Date(time)
  return `${date.getMonth() + 1}/${date.getDate()}`
}

function formatDay(time) {
  if (!time) return ''
  return new Date(time).toLocaleDateString('zh-CN')
}
</script>

<style scoped>
.item-card {
  cursor: pointer;
  transition: transform 0.2s;
}

.item-card:hover {
  transform: translateY(-4px);
}

.card-image {
  position: relative;
  width: 100%;
  height: 180px;
  overflow: hidden;
  border-radius: 4px;
  margin-bottom: 12px;
}

.card-image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.image-placeholder {
  width: 100%;
  height: 100%;
  background: #f5f7fa;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #c0c4cc;
}

.type-tag {
  position: absolute;
  top: 8px;
  left: 8px;
}

.status-tag {
  position: absolute;
  top: 8px;
  right: 8px;
}

.card-title {
  font-size: 16px;
  margin-bottom: 8px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-desc {
  font-size: 13px;
  color: #909399;
  margin-bottom: 12px;
  min-height: 20px;
}

.card-meta {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #909399;
  margin-bottom: 10px;
}

.card-meta span {
  display: flex;
  align-items: center;
  gap: 4px;
}

.card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 10px;
  border-top: 1px solid #f0f0f0;
}

.publisher {
  font-size: 12px;
  color: #c0c4cc;
}
</style>
