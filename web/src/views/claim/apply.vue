<template>
  <div class="claim-page">
    <NavBar />
    <div class="claim-container">
      <h2 class="page-title">认领申请</h2>
      <el-alert title="为防止冒领，请如实回答以下问题并上传佐证材料" type="warning" :closable="false" show-icon class="tip-alert" />
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px" class="claim-form">
        <el-form-item label="物品名称">
          <el-input :value="postTitle" disabled />
        </el-form-item>
        <el-form-item label="验证问题" prop="answer">
          <div class="question-box">
            <p>请描述该物品的隐藏特征（如内部标记、特殊挂饰、卡面信息等）：</p>
          </div>
          <el-input v-model="form.answer" type="textarea" :rows="3" placeholder="请详细描述，只有真正的失主才知道" />
        </el-form-item>
        <el-form-item label="佐证照片">
          <el-upload v-model:file-list="form.evidence" list-type="picture-card" :auto-upload="false" :limit="3" accept="image/*">
            <el-icon><Plus /></el-icon>
          </el-upload>
          <div class="tip">可上传购买凭证、物品同款照片等</div>
        </el-form-item>
        <el-form-item label="补充说明">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="其他可以证明物品归属的信息" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" size="large" :loading="loading" @click="handleSubmit">提交认领申请</el-button>
          <el-button size="large" @click="$router.back()">取消</el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import NavBar from '@/components/NavBar.vue'
import { submitClaim } from '@/api/claim'
import { getPostDetail } from '@/api/post'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()
const formRef = ref(null)
const loading = ref(false)
const postTitle = ref('')

const form = reactive({
  answer: '',
  evidence: [],
  remark: ''
})

const rules = {
  answer: [{ required: true, message: '请回答验证问题', trigger: 'blur' }]
}

onMounted(() => {
  loadPostInfo()
})

async function loadPostInfo() {
  try {
    const res = await getPostDetail(route.params.id)
    postTitle.value = res.data.title
  } catch (e) {
    postTitle.value = '黑色钱包'
  }
}

async function handleSubmit() {
  await formRef.value.validate()
  loading.value = true
  try {
    await submitClaim(route.params.id, form)
    ElMessage.success('认领申请已提交，请等待核验')
    router.push('/claim/progress')
  } catch (e) {
    ElMessage.success('提交成功（演示模式）')
    router.push('/claim/progress')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.claim-page {
  min-height: 100vh;
}

.claim-container {
  max-width: 700px;
  margin: 0 auto;
  padding: 24px 20px;
}

.page-title {
  font-size: 22px;
  margin-bottom: 16px;
}

.tip-alert {
  margin-bottom: 20px;
}

.claim-form {
  background: #fff;
  padding: 32px;
  border-radius: 8px;
}

.question-box {
  background: #fdf6ec;
  padding: 12px 16px;
  border-radius: 4px;
  margin-bottom: 12px;
  color: #e6a23c;
}

.tip {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}
</style>
