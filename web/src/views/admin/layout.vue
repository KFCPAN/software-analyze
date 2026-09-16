<template>
  <el-container class="admin-layout">
    <el-aside width="220px" class="admin-aside">
      <div class="admin-logo">
        <el-icon size="24"><Setting /></el-icon>
        <span>管理后台</span>
      </div>
      <el-menu :default-active="activeMenu" router class="admin-menu">
        <el-menu-item index="/admin/dashboard">
          <el-icon><DataAnalysis /></el-icon>
          <span>数据看板</span>
        </el-menu-item>
        <el-menu-item index="/admin/audit">
          <el-icon><DocumentChecked /></el-icon>
          <span>内容审核</span>
        </el-menu-item>
        <el-menu-item index="/admin/users">
          <el-icon><User /></el-icon>
          <span>用户管理</span>
        </el-menu-item>
        <el-menu-item index="/admin/settings">
          <el-icon><Tools /></el-icon>
          <span>系统设置</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="admin-header">
        <div class="header-left">
          <el-button text @click="$router.push('/home')">
            <el-icon><Back /></el-icon> 返回前台
          </el-button>
        </div>
        <div class="header-right">
          <span>{{ userStore.userInfo?.username || '管理员' }}</span>
        </div>
      </el-header>
      <el-main class="admin-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const userStore = useUserStore()

const activeMenu = computed(() => route.path)
</script>

<style scoped>
.admin-layout {
  height: 100vh;
}

.admin-aside {
  background: #001529;
  color: #fff;
}

.admin-logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-size: 18px;
  font-weight: bold;
  border-bottom: 1px solid #1f3a5c;
}

.admin-menu {
  border-right: none;
  background: transparent;
}

.admin-menu :deep(.el-menu-item) {
  color: rgba(255, 255, 255, 0.7);
}

.admin-menu :deep(.el-menu-item:hover),
.admin-menu :deep(.el-menu-item.is-active) {
  background: #1890ff;
  color: #fff;
}

.admin-header {
  background: #fff;
  border-bottom: 1px solid #e8e8e8;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.admin-main {
  background: #f0f2f5;
  padding: 20px;
}
</style>
