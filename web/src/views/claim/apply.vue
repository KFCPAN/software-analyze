<template>
  <div class="claim-page">
    <NavBar />
    <div class="claim-container">
      <h2 class="page-title">认领申请</h2>
      <el-alert title="为防止冒领，请如实回答以下问题" type="warning" :closable="false" show-icon class="tip-alert" />
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
  answers: []
})

const rules = {}

onMounted(() => {
  loadPostInfo()
})

async function loadPostInfo() {
  try {
    const res = await getPostDetail(route.params.id)
    postTitle.value = res.data.title
  } catch (e) {
    postTitle.value = ''
  }
  // 后端详情不返回隐藏特征问题（防泄露），用通用问题兜底
  questions.value = [
    { featureKey: 'hidden_feature_1', question: '请描述该物品的隐藏特征（如内部标记、特殊挂饰、卡面信息等）', hint: '请详细描述，只有真正的失主才知道' },
    { featureKey: 'hidden_feature_2', question: '请补充一个只有失主知道的细节', hint: '例如：卡内姓名、挂件样式等' }
  ]
  form.answers = questions.value.map(() => '')
}

async function handleSubmit() {
  await formRef.value.validate()
  loading.value = true
  try {
    // 后端要求 answers: [{ featureKey, answer }]
    const answers = questions.value.map((q, i) => ({
      featureKey: q.featureKey || String(q.question || q),
      answer: form.answers[i]
    }))
    await submitClaim(route.params.id, { answers })
    ElMessage.success('认领申请已提交，请等待核验')
    router.push('/claim/progress')
  } catch (e) {
    // 失败由拦截器提示
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
</style>
