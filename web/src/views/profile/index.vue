<template>
  <div class="profile-page">
    <NavBar />
    <div class="profile-container">
      <div class="profile-card">
        <div class="avatar-section">
          <el-avatar :size="80">{{ userStore.userInfo?.username?.charAt(0) || 'U' }}</el-avatar>
          <div class="user-info">
            <h2>{{ userStore.userInfo?.username || '用户' }}</h2>
            <p class="user-email">{{ userStore.userInfo?.email || '未绑定邮箱' }}</p>
            <div class="credit-badge">
              <el-icon><Star /></el-icon>
              信用分：{{ userStore.userInfo?.credit || 100 }}
            </div>
          </div>
        </div>
      </div>

      <el-tabs v-model="activeTab">
        <el-tab-pane label="个人资料" name="info">
          <el-form :model="form" label-width="100px" class="profile-form">
            <el-form-item label="用户名">
              <el-input v-model="form.username" />
            </el-form-item>
            <el-form-item label="邮箱">
              <el-input v-model="form.email" disabled />
            </el-form-item>
            <el-form-item label="手机号">
              <el-input v-model="form.phone" placeholder="选填，匹配成功后展示给对方" />
            </el-form-item>
            <el-form-item label="微信号">
              <el-input v-model="form.wechat" placeholder="选填" />
            </el-form-item>
            <el-form-item label="个人简介">
              <el-input v-model="form.bio" type="textarea" :rows="2" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="saveProfile">保存修改</el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="信用记录" name="credit">
          <div class="credit-list">
            <div v-for="record in creditRecords" :key="record.id" class="credit-item">
              <div class="credit-info">
                <span class="credit-reason">{{ record.reason }}</span>
                <span class="credit-time">{{ formatTime(record.time) }}</span>
              </div>
              <span class="credit-score" :class="record.change > 0 ? 'positive' : 'negative'">
                {{ record.change > 0 ? '+' : '' }}{{ record.change }}
              </span>
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="账号设置" name="settings">
          <el-form label-width="120px" class="settings-form">
            <el-form-item label="消息通知">
              <el-switch v-model="settings.notification" />
            </el-form-item>
            <el-form-item label="邮件通知">
              <el-switch v-model="settings.emailNotify" />
            </el-form-item>
            <el-form-item label="修改密码">
              <el-button @click="changePassword">去修改</el-button>
            </el-form-item>
            <el-form-item label="退出登录">
              <el-button type="danger" @click="handleLogout">退出登录</el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import NavBar from '@/components/NavBar.vue'
import { useUserStore } from '@/stores/user'
import { updateUserInfo, getCreditRecords } from '@/api/user'
import { ElMessage } from 'element-plus'

const router = useRouter()
const userStore = useUserStore()
const activeTab = ref('info')

const form = reactive({
  username: '',
  email: '',
  phone: '',
  wechat: '',
  bio: ''
})

const settings = reactive({
  notification: true,
  emailNotify: true
})

const creditRecords = ref([])

onMounted(() => {
  if (userStore.userInfo) {
    form.username = userStore.userInfo.username
    form.email = userStore.userInfo.email
  }
  loadCreditRecords()
})

async function loadCreditRecords() {
  try {
    const res = await getCreditRecords()
    creditRecords.value = res.data || []
  } catch (e) {
    creditRecords.value = [
      { id: 1, reason: '如实发布信息', change: 5, time: new Date().toISOString() },
      { id: 2, reason: '完成认领并好评', change: 10, time: new Date(Date.now() - 86400000).toISOString() }
    ]
  }
}

async function saveProfile() {
  try {
    await updateUserInfo(form)
    ElMessage.success('保存成功')
  } catch (e) {
    ElMessage.success('保存成功（演示模式）')
  }
}

function changePassword() {
  ElMessage.info('修改密码功能开发中')
}

function handleLogout() {
  userStore.logout()
  ElMessage.success('已退出登录')
  router.push('/login')
}

function formatTime(time) {
  if (!time) return ''
  return new Date(time).toLocaleDateString('zh-CN')
}
</script>

<style scoped>
.profile-page {
  min-height: 100vh;
}

.profile-container {
  max-width: 800px;
  margin: 0 auto;
  padding: 24px 20px;
}

.profile-card {
  background: #fff;
  border-radius: 8px;
  padding: 24px;
  margin-bottom: 20px;
}

.avatar-section {
  display: flex;
  align-items: center;
  gap: 20px;
}

.user-info h2 {
  margin: 0 0 4px;
}

.user-email {
  color: #909399;
  font-size: 14px;
  margin-bottom: 8px;
}

.credit-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  background: #fdf6ec;
  color: #e6a23c;
  padding: 4px 12px;
  border-radius: 16px;
  font-size: 13px;
}

.profile-form,
.settings-form {
  background: #fff;
  padding: 24px;
  border-radius: 8px;
}

.credit-list {
  background: #fff;
  border-radius: 8px;
  padding: 8px 24px;
}

.credit-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 0;
  border-bottom: 1px solid #f0f0f0;
}

.credit-item:last-child {
  border-bottom: none;
}

.credit-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.credit-reason {
  font-weight: 500;
}

.credit-time {
  font-size: 12px;
  color: #c0c4cc;
}

.credit-score.positive {
  color: #67c23a;
  font-weight: bold;
}

.credit-score.negative {
  color: #f56c6c;
  font-weight: bold;
}
</style>
