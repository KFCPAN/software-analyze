<template>
  <div class="message-page">
    <NavBar />
    <div class="message-container">
      <div class="message-header">
        <h2 class="page-title">消息中心</h2>
        <el-button @click="markAllRead">全部已读</el-button>
      </div>
      <el-tabs v-model="activeTab" @tab-change="loadList">
        <el-tab-pane label="全部" name="all" />
        <el-tab-pane label="匹配通知" name="match" />
        <el-tab-pane label="认领进度" name="claim" />
        <el-tab-pane label="系统通知" name="system" />
      </el-tabs>
      <div v-loading="loading" class="message-list">
        <el-empty v-if="list.length === 0 && !loading" description="暂无消息" />
        <div v-for="msg in list" :key="msg.id" class="message-item" :class="{ unread: !msg.read }" @click="handleRead(msg)">
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
            <div class="msg-time">{{ formatTime(msg.time) }}</div>
          </div>
          <el-tag v-if="!msg.read" type="danger" size="small" class="unread-dot">未读</el-tag>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import NavBar from '@/components/NavBar.vue'
import { getMessages, markRead, markAllRead as apiMarkAllRead } from '@/api/message'
import { ElMessage } from 'element-plus'

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
    const res = await getMessages({ type: activeTab.value })
    list.value = res.data?.list || []
  } catch (e) {
    list.value = [
      { id: 1, type: 'match', title: '疑似找到您的物品', content: '系统为您匹配到2条疑似招领信息，点击查看', time: new Date().toISOString(), read: false },
      { id: 2, type: 'claim', title: '认领申请已通过', content: '您对"黑色钱包"的认领申请已通过核验，请查看交接二维码', time: new Date().toISOString(), read: false },
      { id: 3, type: 'system', title: '欢迎使用校园失物招领平台', content: '完善个人资料可提高匹配成功率', time: new Date(Date.now() - 86400000).toISOString(), read: true }
    ]
  } finally {
    loading.value = false
  }
}

async function handleRead(msg) {
  if (!msg.read) {
    try {
      await markRead(msg.id)
    } catch (e) {}
    msg.read = true
  }
  if (msg.type === 'match') {
    router.push('/home')
  } else if (msg.type === 'claim') {
    router.push('/claim/progress')
  }
}

async function markAllRead() {
  try {
    await apiMarkAllRead()
  } catch (e) {}
  list.value.forEach(m => m.read = true)
  ElMessage.success('已全部标记为已读')
}

function formatTime(time) {
  if (!time) return ''
  return new Date(time).toLocaleString('zh-CN')
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
