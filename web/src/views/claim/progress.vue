<template>
  <div class="progress-page">
    <NavBar />
    <div class="progress-container">
      <h2 class="page-title">认领中心</h2>

      <el-tabs v-model="activeTab">
        <!-- 我的认领申请 -->
        <el-tab-pane label="我的认领" name="mine">
          <div v-loading="loadingMine" class="list">
            <el-empty v-if="mineList.length === 0 && !loadingMine" description="暂无认领记录" />
            <el-card v-for="item in mineList" :key="item.id" class="list-item" shadow="never">
              <div class="item-header">
                <span class="item-title">{{ item.itemTitle }}</span>
                <el-tag :type="statusType[item.status]" size="small">{{ statusText[item.status] }}</el-tag>
              </div>
              <el-steps :active="stepMap[item.status]" finish-status="success" align-center class="steps">
                <el-step title="提交申请" />
                <el-step title="拾获者核验" />
                <el-step title="线下核销" />
                <el-step title="完成" />
              </el-steps>
              <p v-if="item.rejectReason" class="reject-reason">驳回原因：{{ item.rejectReason }}</p>
              <div class="item-actions">
                <template v-if="item.status === 'APPROVED'">
                  <el-button type="primary" size="small" @click="showQr(item)">查看核销码</el-button>
                </template>
                <el-button text size="small" @click="$router.push(`/detail/${item.foundItemId}`)">查看物品</el-button>
              </div>
            </el-card>
          </div>
        </el-tab-pane>

        <!-- 待我核验（拾获者/审核员视角） -->
        <el-tab-pane label="待我核验" name="verify">
          <div v-loading="loadingVerify" class="list">
            <el-empty v-if="verifyList.length === 0 && !loadingVerify" description="没有待核验的申请" />
            <el-card v-for="item in verifyList" :key="item.id" class="list-item" shadow="never">
              <div class="item-header">
                <span class="item-title">{{ item.itemTitle }}</span>
                <el-tag type="warning" size="small">{{ statusText[item.status] || '待核验' }}</el-tag>
              </div>
              <div class="verify-detail">
                <p><strong>申请人：</strong>{{ item.claimant?.nickname || '未知' }}（信用分 {{ item.claimant?.creditScore ?? '-' }}）</p>
                <p v-for="(f, i) in item.featureAnswers || []" :key="i">
                  <strong>{{ f.featureKey }}：</strong>{{ f.answer }}
                  <el-tag v-if="f.matched === true" type="success" size="small">匹配</el-tag>
                  <el-tag v-else-if="f.matched === false" type="danger" size="small">不匹配</el-tag>
                </p>
                <p v-if="!(item.featureAnswers && item.featureAnswers.length)">（无特征回答）</p>
              </div>
              <div class="item-actions">
                <template v-if="item.status === 'PENDING'">
                  <el-button type="success" size="small" @click="verifyClaim(item, true)">通过</el-button>
                  <el-button type="danger" size="small" @click="verifyClaim(item, false)">拒绝</el-button>
                </template>
              </div>
            </el-card>
          </div>
        </el-tab-pane>
      </el-tabs>

      <!-- 核销码弹窗（qrcode.vue 生成二维码） -->
      <el-dialog v-model="qrVisible" title="线下交接核销码" width="380px">
        <div class="qr-box" v-if="currentItem">
          <QrcodeVue :value="qrValue" :size="220" level="M" />
          <p class="qr-code">核销码：{{ verifyCode }}</p>
          <p class="qr-tip">请向拾获者出示此二维码/核销码，对方扫码核销后完成交接（{{ expiresAt ? '有效期至 ' + formatTime(expiresAt) : '一次性有效' }}）</p>
        </div>
      </el-dialog>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import NavBar from '@/components/NavBar.vue'
import QrcodeVue from 'qrcode.vue'
import { getClaimProgress, verifyClaim as apiVerify, getClaimCode } from '@/api/claim'
import { ElMessage } from 'element-plus'

const router = useRouter()
const activeTab = ref('mine')

const loadingMine = ref(false)
const loadingVerify = ref(false)
const mineList = ref([])
const verifyList = ref([])

const qrVisible = ref(false)
const currentItem = ref(null)
const verifyCode = ref('')
const expiresAt = ref('')

const statusText = {
  PENDING: '待核验',
  APPROVED: '核验通过',
  COMPLETED: '已交接',
  REJECTED: '已拒绝',
  DISPUTED: '争议中',
  EXPIRED: '已超时'
}
const statusType = {
  PENDING: 'warning',
  APPROVED: 'primary',
  COMPLETED: 'success',
  REJECTED: 'danger',
  DISPUTED: 'danger',
  EXPIRED: 'info'
}
const stepMap = { PENDING: 1, APPROVED: 2, COMPLETED: 3, REJECTED: 1, DISPUTED: 2, EXPIRED: 1 }

const qrValue = computed(() => verifyCode.value || `claim://confirm/${currentItem.value?.id || ''}`)

onMounted(() => {
  loadMine()
  loadVerify()
})

async function loadMine() {
  loadingMine.value = true
  try {
    const res = await getClaimProgress({ scope: 'mine' })
    mineList.value = res.data || []
  } catch (e) {
    mineList.value = []
  } finally {
    loadingMine.value = false
  }
}

async function loadVerify() {
  loadingVerify.value = true
  try {
    const res = await getClaimProgress({ scope: 'verify' })
    verifyList.value = res.data || []
  } catch (e) {
    verifyList.value = []
  } finally {
    loadingVerify.value = false
  }
}

async function showQr(item) {
  currentItem.value = item
  qrVisible.value = true
  try {
    const res = await getClaimCode(item.id)
    verifyCode.value = res.data?.verifyCode || ''
    expiresAt.value = res.data?.expiresAt || ''
  } catch (e) {
    verifyCode.value = `C${String(item.id).padStart(6, '0')}`
    expiresAt.value = ''
  }
}

async function verifyClaim(item, pass) {
  try {
    await apiVerify(item.id, { pass })
    ElMessage.success(pass ? '已通过，认领人可查看核销码' : '已拒绝该申请')
    verifyList.value = verifyList.value.filter(i => i.id !== item.id)
    loadMine()
  } catch (e) {
    // 失败由拦截器提示
  }
}

function formatTime(t) {
  if (!t) return ''
  return new Date(t).toLocaleString('zh-CN')
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

.list-item {
  margin-bottom: 16px;
}

.item-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.item-title {
  font-size: 16px;
  font-weight: 500;
}

.steps {
  margin-bottom: 16px;
}

.reject-reason {
  color: #f56c6c;
  font-size: 13px;
  margin-bottom: 8px;
}

.item-actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
  padding-top: 12px;
  border-top: 1px solid #f0f0f0;
}

.verify-detail {
  background: #fafafa;
  padding: 12px 16px;
  border-radius: 6px;
  margin-bottom: 12px;
  font-size: 14px;
  line-height: 1.9;
  color: #606266;
}

.qr-box {
  text-align: center;
  padding: 8px 0;
}

.qr-box canvas {
  margin: 0 auto;
}

.qr-code {
  margin-top: 12px;
  font-size: 18px;
  font-weight: bold;
  color: #409eff;
  letter-spacing: 2px;
}

.qr-tip {
  margin-top: 8px;
  font-size: 12px;
  color: #909399;
  line-height: 1.6;
}
</style>
