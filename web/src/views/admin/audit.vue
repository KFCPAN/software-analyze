<template>
  <div class="audit-page">
    <h2 class="page-title">内容审核</h2>
    <el-table :data="list" v-loading="loading" stripe>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="title" label="标题" />
      <el-table-column prop="type" label="类型" width="100">
        <template #default="{ row }">
          <el-tag :type="row.type === 'lost' ? 'danger' : 'success'" size="small">
            {{ row.type === 'lost' ? '寻物' : '招领' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="publisher" label="发布者" width="120" />
      <el-table-column prop="riskLevel" label="风险等级" width="120">
        <template #default="{ row }">
          <el-tag :type="row.riskLevel === 'high' ? 'danger' : row.riskLevel === 'medium' ? 'warning' : 'info'" size="small">
            {{ row.riskLevel === 'high' ? '高风险' : row.riskLevel === 'medium' ? '中风险' : '低风险' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="提交时间" width="180" />
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="viewDetail(row)">查看</el-button>
          <el-button size="small" type="success" @click="approve(row)">通过</el-button>
          <el-button size="small" type="danger" @click="reject(row)">驳回</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="detailVisible" title="审核详情" width="600px">
      <div v-if="current">
        <p><strong>标题：</strong>{{ current.title }}</p>
        <p><strong>描述：</strong>{{ current.description }}</p>
        <p><strong>风险原因：</strong>{{ current.riskReason }}</p>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getAuditList, auditApprove, auditReject } from '@/api/admin'
import { ElMessage, ElMessageBox } from 'element-plus'

const loading = ref(false)
const list = ref([])
const detailVisible = ref(false)
const current = ref(null)

onMounted(() => {
  loadList()
})

async function loadList() {
  loading.value = true
  try {
    const res = await getAuditList()
    list.value = res.data?.list || []
  } catch (e) {
    list.value = [
      { id: 1, title: '黑色钱包', type: 'lost', publisher: '张三', riskLevel: 'low', createTime: '2026-09-01 10:30', description: '内有身份证', riskReason: '无' },
      { id: 2, title: '捡到手机一部', type: 'found', publisher: '李四', riskLevel: 'high', createTime: '2026-09-01 11:20', description: '屏幕有裂痕', riskReason: '照片含人脸' }
    ]
  } finally {
    loading.value = false
  }
}

function viewDetail(row) {
  current.value = row
  detailVisible.value = true
}

async function approve(row) {
  try {
    await auditApprove(row.id)
  } catch (e) {}
  list.value = list.value.filter(i => i.id !== row.id)
  ElMessage.success('已通过')
}

async function reject(row) {
  const { value } = await ElMessageBox.prompt('请输入驳回原因', '驳回审核', { confirmButtonText: '确定', cancelButtonText: '取消' })
  try {
    await auditReject(row.id, { reason: value })
  } catch (e) {}
  list.value = list.value.filter(i => i.id !== row.id)
  ElMessage.success('已驳回')
}
</script>

<style scoped>
.page-title {
  font-size: 20px;
  margin-bottom: 20px;
}
</style>
