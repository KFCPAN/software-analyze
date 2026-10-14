<template>
  <div class="post-page">
    <NavBar />
    <div class="post-container">
      <h2 class="page-title">发布招领信息</h2>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" class="post-form">
        <el-form-item label="物品名称" prop="title">
          <el-input v-model="form.title" placeholder="例如：黑色钱包、iPhone手机" />
        </el-form-item>
        <el-form-item label="物品分类" prop="categoryId">
          <el-select v-model="form.categoryId" placeholder="请选择分类" style="width: 100%">
            <el-option v-for="cat in categories" :key="cat.id" :label="cat.name" :value="cat.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="拾获时间" prop="eventTime">
          <el-date-picker v-model="form.eventTime" type="datetime" placeholder="选择拾获时间" style="width: 100%" />
        </el-form-item>
        <el-form-item label="拾获地点" prop="location">
          <el-cascader
            v-model="form.locationPath"
            :options="locationOptions"
            placeholder="选择校区和具体地点"
            style="width: 100%"
            clearable
          />
          <el-input
            v-model="form.customLocation"
            class="custom-loc-input"
            placeholder="不在列表中？直接填写，如：文华楼东区12层自习区"
            maxlength="100"
          />
        </el-form-item>
        <el-form-item label="公开描述" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="物品的公开特征，失主可看到" />
        </el-form-item>

        <!-- 隐藏特征（防冒领） -->
        <el-form-item label="隐藏特征" required>
          <div class="feature-list">
            <div v-for="(f, i) in form.hiddenFeatures" :key="i" class="feature-row">
              <el-input v-model="f.featureKey" placeholder="问题，如：卡内姓名" style="flex: 1" />
              <el-input v-model="f.answer" placeholder="答案，如：张三（加密存储）" style="flex: 1" />
              <el-button type="danger" text @click="removeFeature(i)">删除</el-button>
            </div>
            <el-button type="primary" plain size="small" @click="addFeature">+ 添加隐藏特征</el-button>
          </div>
          <div class="tip">隐藏特征不会公开显示，认领时系统会要求失主回答；建议至少 1 条</div>
        </el-form-item>

        <el-form-item label="物品照片">
          <el-upload
            v-model:file-list="fileList"
            list-type="picture-card"
            :auto-upload="false"
            :limit="4"
            accept="image/*"
            :on-change="onFileChange"
          >
            <el-icon><Plus /></el-icon>
          </el-upload>
          <div class="upload-tip">最多上传4张照片，发布时自动上传，首张将作为封面</div>
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
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import NavBar from '@/components/NavBar.vue'
import { postFound, getCategories, getLocations } from '@/api/post'
import { uploadFile } from '@/api/file'
import { ElMessage } from 'element-plus'

const router = useRouter()
const formRef = ref(null)
const loading = ref(false)

const categories = ref([])
const locations = ref([])
const locationOptions = ref([])
const fileList = ref([])

const form = reactive({
  title: '',
  categoryId: null,
  eventTime: '',
  locationPath: [],
  customLocation: '',
  description: '',
  hiddenFeatures: [{ featureKey: '', answer: '' }],
  images: []
})

const rules = {
  title: [{ required: true, message: '请输入物品名称', trigger: 'blur' }],
  categoryId: [{ required: true, message: '请选择分类', trigger: 'change' }],
  eventTime: [{ required: true, message: '请选择拾获时间', trigger: 'change' }],
  location: [{
    validator: (rule, value, callback) => {
      const hasCascader = form.locationPath && form.locationPath.length > 0
      if (!hasCascader && !form.customLocation.trim()) {
        callback(new Error('请选择或填写拾获地点'))
      } else {
        callback()
      }
    },
    trigger: 'change'
  }],
  description: [{ required: true, message: '请填写公开描述', trigger: 'blur' }]
}

onMounted(async () => {
  try {
    const [catRes, locRes] = await Promise.all([getCategories(), getLocations()])
    categories.value = catRes.data || []
    locations.value = locRes.data || []
    locationOptions.value = locations.value.map(loc => ({
      value: loc.id,
      label: loc.name,
      children: (loc.children || []).map(child => ({ value: child.id, label: child.name }))
    }))
  } catch (e) {
    categories.value = [
      { id: 1, name: '电子产品' }, { id: 2, name: '证件卡片' }, { id: 3, name: '钥匙' },
      { id: 4, name: '书籍文具' }, { id: 5, name: '衣物配饰' }, { id: 6, name: '其他' }
    ]
    locationOptions.value = [
      {
        value: 1, label: '海淀校区',
        children: [
          { value: 11, label: '图书馆' }, { value: 12, label: '教学楼' },
          { value: 13, label: '食堂' }, { value: 14, label: '体育馆' },
          { value: 15, label: '操场' }, { value: 16, label: '宿舍楼' }
        ]
      }
    ]
  }
})

function addFeature() {
  form.hiddenFeatures.push({ featureKey: '', answer: '' })
}

function removeFeature(i) {
  form.hiddenFeatures.splice(i, 1)
}

function onFileChange(file, fileList) {
  form.images = fileList.map(f => f.raw).filter(Boolean)
}

async function handleSubmit() {
  await formRef.value.validate()
  const features = form.hiddenFeatures.filter(f => f.featureKey && f.answer)
  if (features.length === 0) {
    ElMessage.warning('请至少填写一条隐藏特征（防冒领）')
    return
  }
  loading.value = true
  try {
    // 1. 先上传图片
    const paths = []
    for (const raw of form.images) {
      const res = await uploadFile(raw)
      if (res.data?.path) paths.push(res.data.path)
    }
    // 2. 发布信息
    const locPath = form.locationPath || []
    await postFound({
      title: form.title,
      categoryId: form.categoryId,
      locationId: locPath.length ? locPath[locPath.length - 1] : null,
      customLocation: form.customLocation.trim() || undefined,
      eventTime: form.eventTime ? new Date(form.eventTime).toISOString() : '',
      description: form.description,
      images: paths,
      hiddenFeatures: features
    })
    ElMessage.success('发布成功，系统正在为您智能匹配失主')
    router.push('/post/mine')
  } catch (e) {
    // 失败由拦截器提示
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

.feature-list {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.feature-row {
  display: flex;
  gap: 8px;
  align-items: center;
}

.tip {
  font-size: 12px;
  color: #e6a23c;
  margin-top: 4px;
  width: 100%;
}

.custom-loc-input {
  margin-top: 8px;
}

.upload-tip {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}
</style>
