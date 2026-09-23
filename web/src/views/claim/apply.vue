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

        <!-- 动态渲染隐藏特征问答 -->
        <el-form-item
          v-for="(q, idx) in questions"
          :key="idx"
          :label="`验证问题 ${idx + 1}`"
          :prop="`answers.${idx}`"
          :rules="[{ required: true, message: '请回答验证问题', trigger: 'blur' }]"
        >
          <div class="question-box">
            <p>{{ q.question || q }}</p>
          </div>
          <el-input
            v-model="form.answers[idx]"
            type="textarea"
            :rows="2"
            :placeholder="q.hint || '请详细描述，只有真正的失主才知道'"
          />
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
const questions = ref([])

const form = reactive({
  answers: [],
  evidence: [],
  remark: ''
})

const rules = {}

onMounted(() => {
  loadPostInfo()
})

async function loadPostInfo() {
  try {
    const res = await getPostDetail(route.params.id)
    postTitle.value = res.data.title
    // 动态隐藏特征问答：优先取 detail.questions，否则给默认问题兜底
    questions.value = res.data.questions?.length
      ? res.data.questions
      : [{ question: '请描述该物品的隐藏特征（如内部标记、特殊挂饰、卡面信息等）', hint: '请详细描述，只有真正的失主才知道' }]
  } catch (e) {
    postTitle.value = '黑色钱包'
    questions.value = [
      { question: '请描述该物品的隐藏特征（如内部标记、特殊挂饰、卡面信息等）', hint: '请详细描述，只有真正的失主才知道' },
      { question: '钱包内有几张卡片？分别是什么类型？', hint: '例如：校园卡 + 身份证' }
    ]
  }
  form.answers = questions.value.map(() => '')
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
  width: 100%;
}

.tip {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}
</style>
