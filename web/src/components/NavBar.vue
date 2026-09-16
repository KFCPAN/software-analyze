<template>
  <el-header class="navbar">
    <div class="nav-container">
      <div class="logo" @click="$router.push('/home')">
        <el-icon size="24"><Search /></el-icon>
        <span>校园失物招领</span>
      </div>
      <div class="nav-menu">
        <el-menu mode="horizontal" :default-active="activeMenu" @select="handleSelect" router>
          <el-menu-item index="/home">首页</el-menu-item>
          <el-menu-item index="/post/lost">发布失物</el-menu-item>
          <el-menu-item index="/post/found">发布招领</el-menu-item>
          <el-menu-item index="/post/mine">我的发布</el-menu-item>
        </el-menu>
      </div>
      <div class="nav-user">
        <template v-if="userStore.token">
          <el-badge :value="unreadCount" :hidden="unreadCount === 0" class="message-badge">
            <el-button text @click="$router.push('/messages')">
              <el-icon size="20"><Bell /></el-icon>
            </el-button>
          </el-badge>
          <el-dropdown @command="handleCommand">
            <span class="user-name">
              {{ userStore.userInfo?.username || '用户' }}
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">个人中心</el-dropdown-item>
                <el-dropdown-item command="admin" v-if="userStore.isAdmin()">管理后台</el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
        <template v-else>
          <el-button type="primary" @click="$router.push('/login')">登录</el-button>
          <el-button @click="$router.push('/register')">注册</el-button>
        </template>
      </div>
    </div>
  </el-header>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { getUnreadCount } from '@/api/message'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const unreadCount = ref(0)

const activeMenu = computed(() => route.path)

onMounted(() => {
  if (userStore.token) {
    fetchUnreadCount()
  }
})

async function fetchUnreadCount() {
  try {
    const res = await getUnreadCount()
    unreadCount.value = res.data?.count || 0
  } catch (e) {
    // 静默失败
  }
}

function handleSelect(index) {
  router.push(index)
}

function handleCommand(command) {
  if (command === 'profile') {
    router.push('/profile')
  } else if (command === 'admin') {
    router.push('/admin')
  } else if (command === 'logout') {
    userStore.logout()
    ElMessage.success('已退出登录')
    router.push('/login')
  }
}
</script>

<style scoped>
.navbar {
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
  padding: 0;
  height: 60px;
}

.nav-container {
  max-width: 1200px;
  margin: 0 auto;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
}

.logo {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 18px;
  font-weight: bold;
  color: #409eff;
  cursor: pointer;
}

.nav-menu {
  flex: 1;
  margin-left: 40px;
}

.nav-menu :deep(.el-menu) {
  border-bottom: none;
}

.nav-user {
  display: flex;
  align-items: center;
  gap: 16px;
}

.user-name {
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 4px;
  color: #606266;
}

.message-badge {
  margin-right: 8px;
}
</style>
