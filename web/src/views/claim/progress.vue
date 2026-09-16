<template>
  <div class="progress-page">
    <NavBar />
    <div class="progress-container">
      <h2 class="page-title">认领进度</h2>
      <div v-loading="loading" class="progress-list">
        <el-empty v-if="list.length === 0 && !loading" description="暂无认领记录" />
        <el-card v-for="item in list" :key="item.id" class="progress-item" shadow="never">
          <div class="progress-header">
            <span class="item-title">{{ item.postTitle }}</span>
            <el-tag :type="statusType[item.status]" size="small">{{ statusText[item.status] }}</el-tag>
          </div>
          <el-steps :active="stepMap[item.status]" finish-status="success" align-center class="steps">
            <el-step title="提交申请" />
            <el-step title="拾获者核验" />
            <el-step title="线下交接" />
            <el-step title="完成评价" />
          </el-steps>
          <div class="progress-actions">
            <template v-if="item.status === 'verified'">
              <el-button type="primary" @click="showQrCode(item)">查看交接二维码</el-button>
            </template>
            <template v-if="item.status === 'confirmed' && !item.reviewed">
              <el-button type="success" @click="goReview(item)">去评价</el-button>
            </template>
            <template v-if="item.status === 'rejected'">
              <el-button type="danger" @click="goAppeal(item)">申诉</el-button>
            </template>
            <el-button text @click="$router.push(`/detail/${item.postId}`)">查看详情</el-button>
          </div>
        </el-card>
      </div>

      <!-- 二维码弹窗 -->
      <el-dialog v-model="qrVisible" title="交接二维码" width="360px">
        <div class="qr-box">
          <div class="qr-placeholder">
            <el-icon size="80"><FullScreen /></el-icon>
            <p>交接二维码</p>
            <p class="qr-code">编号：{{ currentClaim?.qrCode || 'XXXXXX' }}</p>
          </div>
          <p class="qr-tip">请在交接时向拾获者出示此二维码，对方扫码后完成核销</p>
        </div>
      </el-dialog>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import NavBar from '@/components/NavBar.vue'
import { getClaimProgress } from '@/api/claim'
import { ElMessage } from 'element-plus'

const router = useRouter()
const loading = ref(false)
const list = ref([])
const qrVisible = ref(false)
const currentClaim = ref(null)

const statusText = {
  pending: '待核验',
  verified: '核验通过',
  confirmed: '交接完成',
  reviewed: '已评价',
  rejected: '已拒绝'
}

const statusType = {
  pending: 'warning',
  verified: 'primary',
  confirmed: 'success',
  reviewed: 'info',
  rejected: 'danger'
}

const stepMap = {
  pending: 1,
  verified: 2,
  confirmed: 3,
  reviewed: 4,
  rejected: 1
}

onMounted(() => {
  loadList()
})

async function loadList() {
  loading.value = true
  try {
    const res = await getClaimProgress()
    list.value = res.data?.list || []
  } catch (e) {
    list.value = [
      { id: 1, postId: 1, postTitle: '黑色钱包', status: 'pending', reviewed: false },
      { id: 2, postId: 2, postTitle: 'iPhone 14', status: 'verified', qrCode: 'A8K2M9', reviewed: false }
    ]
  } finally {
    loading.value = false
  }
}

function showQrCode(item) {
  currentClaim.value = item
  qrVisible.value = true
}

function goReview(item) {
  ElMessage.success('跳转到评价页（演示）')
}

function goAppeal(item) {
  ElMessage.info('跳转到申诉页（演示）')
}
</script>

<style scoped>
.progress-page {
  min-height: 100vh;
}

.progress-container {
  max-width: 800px;
  margin: 0 auto;
  padding: 24px 20px;
}

.page-title {
  font-size: 22px;
  margin-bottom: 20px;
}

.progress-item {
  margin-bottom: 16px;
}

.progress-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.item-title {
  font-size: 16px;
  font-weight: 500;
}

.steps {
  margin-bottom: 20px;
}

.progress-actions {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
}

.qr-box {
  text-align: center;
}

.qr-placeholder {
  width: 200px;
  height: 200px;
  margin: 0 auto 16px;
  border: 2px dashed #dcdfe6;
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #c0c4cc;
  gap: 8px;
}

.qr-code {
  font-size: 18px;
  font-weight: bold;
  color: #409eff;
  letter-spacing: 2px;
}

.qr-tip {
  font-size: 12px;
  color: #909399;
  line-height: 1.6;
}
</style>
