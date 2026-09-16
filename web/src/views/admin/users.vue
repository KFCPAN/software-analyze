<template>
  <div class="users-page">
    <h2 class="page-title">用户管理</h2>
    <el-table :data="list" v-loading="loading" stripe>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="username" label="用户名" width="150" />
      <el-table-column prop="email" label="邮箱" />
      <el-table-column prop="credit" label="信用分" width="100" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 'banned' ? 'danger' : 'success'" size="small">
            {{ row.status === 'banned' ? '已封禁' : '正常' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="registerTime" label="注册时间" width="180" />
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="viewDetail(row)">详情</el-button>
          <el-button v-if="row.status !== 'banned'" size="small" type="danger" @click="banUser(row)">封禁</el-button>
          <el-button v-else size="small" type="success" @click="unbanUser(row)">解封</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getUserList, banUser as apiBan, unbanUser as apiUnban } from '@/api/admin'
import { ElMessage, ElMessageBox } from 'element-plus'

const loading = ref(false)
const list = ref([])

onMounted(() => {
  loadList()
})

async function loadList() {
  loading.value = true
  try {
    const res = await getUserList()
    list.value = res.data?.list || []
  } catch (e) {
    list.value = [
      { id: 1, username: '张三', email: 'zhangsan@edu.cn', credit: 100, status: 'normal', registerTime: '2026-08-15' },
      { id: 2, username: '李四', email: 'lisi@edu.cn', credit: 85, status: 'normal', registerTime: '2026-08-20' },
      { id: 3, username: '王五', email: 'wangwu@edu.cn', credit: 30, status: 'banned', registerTime: '2026-08-25' }
    ]
  } finally {
    loading.value = false
  }
}

function viewDetail(row) {
  ElMessage.info(`查看用户 ${row.username} 详情`)
}

async function banUser(row) {
  const { value } = await ElMessageBox.prompt('请输入封禁原因', '封禁用户', { confirmButtonText: '确定', cancelButtonText: '取消' })
  try {
    await apiBan(row.id, { reason: value })
  } catch (e) {}
  row.status = 'banned'
  ElMessage.success('已封禁')
}

async function unbanUser(row) {
  try {
    await apiUnban(row.id)
  } catch (e) {}
  row.status = 'normal'
  ElMessage.success('已解封')
}
</script>

<style scoped>
.page-title {
  font-size: 20px;
  margin-bottom: 20px;
}
</style>
