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
                <span class="item-title">{{ item.postTitle }}</span>
                <el-tag :type="statusType[item.status]" size="small">{{ statusText[item.status] }}</el-tag>
              </div>
              <el-steps :active="stepMap[item.status]" finish-status="success" align-center class="steps">
                <el-step title="提交申请" />
                <el-step title="拾获者核验" />
                <el-step title="线下核销" />
                <el-step title="完成评价" />
              </el-steps>
              <div class="item-actions">
                <template v-if="item.status === 'approved'">
                  <el-button type="primary" size="small" @click="showQr(item)">查看核销码</el-button>
                </template>
                <template v-if="item.status === 'completed' && !item.reviewed">
                  <el-button type="success" size="small" @click="goReview(item)">去评价</el-button>
                </template>
                <el-button text size="small" @click="$router.push(`/detail/${item.postId}`)">查看物品</el-button>
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
                <span class="item-title">{{ item.postTitle }}</span>
                <el-tag type="warning" size="small">待核验</el-tag>
              </div>
              <div class="verify-detail">
                <p><strong>申请人：</strong>{{ item.applicant }}</p>
                <p><strong>隐藏特征回答：</strong>{{ item.answers?.join(' / ') || '（无回答）' }}</p>
                <p v-if="item.evidence?.length"><strong>佐证材料：</strong>{{ item.evidence.length }} 张照片</p>
              </div>
              <div class="item-actions">
                <el-button type="success" size="small" @click="verifyClaim(item, true)">通过</el-button>
                <el-button type="danger" size="small" @click="verifyClaim(item, false)">拒绝</el-button>
              </div>
            </el-card>
          </div>
        </el-tab-pane>
      </el-tabs>

      <!-- 核销码弹窗（qrcode.vue 生成二维码） -->
      <el-dialog v-model="qrVisible" title="线下交接核销码" width="380px">
        <div class="qr-box" v-if="currentItem">
          <QrcodeVue :value="qrValue" :size="220" level="M" />
          <p class="qr-code">编号：{{ currentItem.qrCode }}</p>
          <p class="qr-tip">请向拾获者出示此二维码，对方扫码核销后完成交接（一次性有效）</p>
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
import { getClaimProgress, verifyClaim as apiVerify } from '@/api/claim'
import { ElMessage } from 'element-plus'

const router = useRouter()
const activeTab = ref('mine')

const loadingMine = ref(false)
const loadingVerify = ref(false)
const mineList = ref([])
const verifyList = ref([])

const qrVisible = ref(false)
const currentItem = ref(null)

const statusText = {
  pending: '待核验',
  approved: '核验通过',
  completed: '已交接',
  rejected: '已拒绝'
}
const statusType = {
  pending: 'warning',
  approved: 'primary',
  completed: 'success',
  rejected: 'danger'
}
const stepMap = { pending: 1, approved: 2, completed: 3, rejected: 1 }

// 核销码内容：接口给的 qrUrl 或生成带 id 的 URL
const qrValue = computed(() => currentItem.value?.qrUrl || `claim://confirm/${currentItem.value?.id || ''}`)

onMounted(() => {
  loadMine()
  loadVerify()
})

async function loadMine() {
  loadingMine.value = true
  try {
    const res = await getClaimProgress({ scope: 'mine' })
    mineList.value = res.data?.list || []
  } catch (e) {
    mineList.value = [
      { id: 1, postId: 1, postTitle: '黑色钱包', status: 'pending', answers: [], reviewed: false },
      { id: 2, postId: 2, postTitle: 'iPhone 14', status: 'approved', qrCode: 'A8K2M9', qrUrl: 'claim://confirm/A8K2M9', reviewed: false },
      { id: 3, postId: 3, postTitle: '校园卡', status: 'completed', reviewed: false }
    ]
  } finally {
    loadingMine.value = false
  }
}

async function loadVerify() {
  loadingVerify.value = true
  try {
    const res = await getClaimProgress({ scope: 'verify' })
    verifyList.value = res.data?.list || []
  } catch (e) {
    verifyList.value = [
      { id: 101, postId: 1, postTitle: '黑色钱包', applicant: '李四', answers: ['钱包内有校园卡和身份证'], evidence: [{ url: '' }] }
    ]
  } finally {
    loadingVerify.value = false
  }
}

function showQr(item) {
  currentItem.value = item
  qrVisible.value = true
}

async function verifyClaim(item, pass) {
  try {
    await apiVerify(item.id, { pass })
  } catch (e) {}
  if (pass) {
    ElMessage.success('已通过，已生成核销码')
    verifyList.value = verifyList.value.filter(i => i.id !== item.id)
    loadMine()
  } else {
    ElMessage.warning('已拒绝该申请')
    verifyList.value = verifyList.value.filter(i => i.id !== item.id)
  }
}

function goReview(item) {
  ElMessage.success('跳转评价页（开发中）')
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
  line-height: 1.8;
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
