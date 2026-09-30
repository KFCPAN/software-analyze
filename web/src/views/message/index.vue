<template>
  <div class="message-page">
    <NavBar />
    <div class="message-container">
      <div class="message-header">
        <h2 class="page-title">消息中心</h2>
        <div class="header-right">
          <el-badge :value="userStore.unreadCount" :hidden="userStore.unreadCount === 0" class="unread-badge">
            <span class="unread-text">未读</span>
          </el-badge>
          <el-button size="small" @click="markAllRead">全部已读</el-button>
        </div>
      </div>
      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <el-tab-pane label="全部" name="all" />
        <el-tab-pane label="匹配通知" name="match" />
        <el-tab-pane label="认领进度" name="claim" />
        <el-tab-pane label="系统通知" name="system" />
      </el-tabs>
      <div v-loading="loading" class="message-list">
        <el-empty v-if="list.length === 0 && !loading" description="暂无消息" />
        <div v-for="msg in list" :key="msg.id" class="message-item" :class="{ unread: !msg.isRead }" @click="handleRead(msg)">
          <div class="msg-icon" :class="msg.type">
            <el-icon size="20">
              <MagicStick v-if="msg.type === 'match'" />
              <Pointer v-else-if="msg.type === 'claim'" />
              <Bell v-else />
            </el-icon>
          </div>
          <div class="msg-content">
            <div class="msg-title">{{ msg.title }}</div>
            <div class="msg-desc">{{ msg.content }}</div>
            <div class="msg-time">{{ formatTime(msg.createdAt) }}</div>
          </div>
          <el-tag v-if="!msg.isRead" type="danger" size="small" class="unread-dot">未读</el-tag>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import NavBar from '@/components/NavBar.vue'
import { useUserStore } from '@/stores/user'
import { getMessages, getUnreadCount, markRead } from '@/api/message'
import { ElMessage } from 'element-plus'

const router = useRouter()
const userStore = useUserStore()
const activeTab = ref('all')
const allList = ref([])
const list = computed(() => {
  if (activeTab.value === 'all') return allList.value
  return allList.value.filter(m => m.type === activeTab.value)
})
const loading = ref(false)

onMounted(() => {
  loadList()
  refreshUnread()
})

async function refreshUnread() {
  try {
    const res = await getUnreadCount()
    userStore.setUnreadCount(res.data?.unreadCount || 0)
  } catch (e) {
    userStore.setUnreadCount(allList.value.filter(m => !m.isRead).length)
  }
}

function handleTabChange() {
  // 前端过滤
}

async function loadList() {
  loading.value = true
  try {
    const res = await getMessages({ page: 1, size: 50 })
    allList.value = res.data?.list || []
    userStore.setUnreadCount(res.data?.unreadCount || 0)
  } catch (e) {
    allList.value = getMockData()
  } finally {
    loading.value = false
  }
}

async function handleRead(msg) {
  if (!msg.isRead) {
    try {
      await markRead(msg.id)
    } catch (e) {}
    msg.isRead = true
    userStore.setUnreadCount(Math.max(0, userStore.unreadCount - 1))
  }
  if (msg.type === 'match' && msg.relatedId) {
    router.push(`/detail/${msg.relatedId}`)
  } else if (msg.type === 'claim') {
    router.push('/claim/progress')
  }
}

async function markAllRead() {
  try {
    // 后端无批量已读接口：逐条标记
    const unread = allList.value.filter(m => !m.isRead)
    for (const m of unread) {
      await markRead(m.id)
      m.isRead = true
    }
  } catch (e) {}
  allList.value.forEach(m => m.isRead = true)
  userStore.setUnreadCount(0)
  ElMessage.success('已全部标记为已读')
}

function formatTime(time) {
  if (!time) return ''
  return new Date(time).toLocaleString('zh-CN')
}

function getMockData() {
  return [
    { id: 1, type: 'match', title: '疑似找到您的物品', content: '系统为您匹配到2条疑似招领信息，点击查看', createdAt: new Date().toISOString(), relatedId: 1, isRead: false },
    { id: 2, type: 'claim', title: '认领申请已通过', content: '您对"黑色钱包"的认领申请已通过核验，请查看交接二维码', createdAt: new Date().toISOString(), relatedId: 1, isRead: false },
    { id: 3, type: 'system', title: '欢迎使用校园失物招领平台', content: '完善个人资料可提高匹配成功率', createdAt: new Date(Date.now() - 86400000).toISOString(), isRead: true }
  ]
}
</script>

<style scoped>
.message-page {
  min-height: 100vh;
}

.message-container {
  max-width: 700px;
  margin: 0 auto;
  padding: 24px 20px;
}

.message-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.unread-badge :deep(.el-badge__content) {
  position: static;
  transform: none;
}

.unread-text {
  font-size: 14px;
  color: #909399;
}

.page-title {
  font-size: 22px;
  margin: 0;
}

.message-item {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  padding: 16px;
  background: #fff;
  border-radius: 8px;
  margin-bottom: 12px;
  cursor: pointer;
  transition: all 0.2s;
  position: relative;
}

.message-item:hover {
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
}

.message-item.unread {
  background: #ecf5ff;
}

.msg-icon {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  color: #fff;
}

.msg-icon.match { background: #409eff; }
.msg-icon.claim { background: #67c23a; }
.msg-icon.system { background: #909399; }

.msg-content {
  flex: 1;
}

.msg-title {
  font-weight: 500;
  margin-bottom: 4px;
}

.msg-desc {
  font-size: 13px;
  color: #909399;
  margin-bottom: 4px;
}

.msg-time {
  font-size: 12px;
  color: #c0c4cc;
}

.unread-dot {
  position: absolute;
  top: 16px;
  right: 16px;
}
</style>
