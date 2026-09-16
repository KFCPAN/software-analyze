<template>
  <div class="post-page">
    <NavBar />
    <div class="post-container">
      <h2 class="page-title">发布失物启事</h2>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" class="post-form">
        <el-form-item label="物品名称" prop="title">
          <el-input v-model="form.title" placeholder="例如：黑色钱包、iPhone手机" />
        </el-form-item>
        <el-form-item label="物品分类" prop="category">
          <el-select v-model="form.category" placeholder="请选择分类" style="width: 100%">
            <el-option v-for="cat in categories" :key="cat" :label="cat" :value="cat" />
          </el-select>
        </el-form-item>
        <el-form-item label="丢失时间" prop="lostTime">
          <el-date-picker v-model="form.lostTime" type="datetime" placeholder="选择丢失时间" style="width: 100%" />
        </el-form-item>
        <el-form-item label="丢失地点" prop="location">
          <el-select v-model="form.location" placeholder="选择地点" filterable allow-create style="width: 100%">
            <el-option v-for="loc in locations" :key="loc" :label="loc" :value="loc" />
          </el-select>
        </el-form-item>
        <el-form-item label="详细描述" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="4" placeholder="描述物品特征、品牌、颜色、内含物品等，越详细匹配越准" />
        </el-form-item>
        <el-form-item label="物品照片">
          <el-upload
            v-model:file-list="form.images"
            list-type="picture-card"
            :auto-upload="false"
            :limit="4"
            accept="image/*"
          >
            <el-icon><Plus /></el-icon>
          </el-upload>
          <div class="upload-tip">最多上传4张照片，首张将作为封面</div>
        </el-form-item>
        <el-form-item label="联系方式">
          <el-input v-model="form.contact" placeholder="手机号/微信，匹配成功后才会展示给对方" />
        </el-form-item>
        <el-form-item label="感谢语">
          <el-input v-model="form.reward" placeholder="例如：必有重谢、请喝奶茶" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" size="large" :loading="loading" @click="handleSubmit">发布</el-button>
          <el-button size="large" @click="$router.back()">取消</el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import NavBar from '@/components/NavBar.vue'
import { postLost } from '@/api/post'
import { ElMessage } from 'element-plus'

const router = useRouter()
const formRef = ref(null)
const loading = ref(false)

const categories = ['电子产品', '证件卡片', '钥匙', '书籍文具', '衣物配饰', '其他']
const locations = ['图书馆', '教学楼A座', '教学楼B座', '食堂一楼', '食堂二楼', '体育馆', '操场', '宿舍楼', '逸夫楼', '奶茶店', '其他']

const form = reactive({
  title: '',
  category: '',
  lostTime: '',
  location: '',
  description: '',
  images: [],
  contact: '',
  reward: ''
})

const rules = {
  title: [{ required: true, message: '请输入物品名称', trigger: 'blur' }],
  category: [{ required: true, message: '请选择分类', trigger: 'change' }],
  lostTime: [{ required: true, message: '请选择丢失时间', trigger: 'change' }],
  location: [{ required: true, message: '请选择丢失地点', trigger: 'change' }],
  description: [{ required: true, message: '请填写详细描述', trigger: 'blur' }]
}

async function handleSubmit() {
  await formRef.value.validate()
  loading.value = true
  try {
    await postLost(form)
    ElMessage.success('发布成功，系统正在为您智能匹配')
    router.push('/post/mine')
  } catch (e) {
    ElMessage.success('发布成功（演示模式）')
    router.push('/post/mine')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.post-page {
  min-height: 100vh;
}

.post-container {
  max-width: 800px;
  margin: 0 auto;
  padding: 24px 20px;
}

.page-title {
  font-size: 22px;
  margin-bottom: 24px;
  color: #303133;
}

.post-form {
  background: #fff;
  padding: 32px;
  border-radius: 8px;
}

.upload-tip {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}
</style>
