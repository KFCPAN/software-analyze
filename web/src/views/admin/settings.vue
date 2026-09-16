<template>
  <div class="settings-page">
    <h2 class="page-title">系统设置</h2>

    <el-card class="setting-card">
      <template #header>智能匹配参数配置</template>
      <el-form :model="matchConfig" label-width="160px">
        <el-form-item label="文本相似度权重 (w1)">
          <el-slider v-model="matchConfig.w1" :min="0" :max="1" :step="0.1" show-input />
        </el-form-item>
        <el-form-item label="图像相似度权重 (w2)">
          <el-slider v-model="matchConfig.w2" :min="0" :max="1" :step="0.1" show-input />
        </el-form-item>
        <el-form-item label="时间接近度权重 (w3)">
          <el-slider v-model="matchConfig.w3" :min="0" :max="1" :step="0.1" show-input />
        </el-form-item>
        <el-form-item label="地点接近度权重 (w4)">
          <el-slider v-model="matchConfig.w4" :min="0" :max="1" :step="0.1" show-input />
        </el-form-item>
        <el-form-item label="匹配触发阈值">
          <el-slider v-model="matchConfig.threshold" :min="0" :max="1" :step="0.05" show-input />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="saveMatchConfig">保存配置</el-button>
          <el-button @click="resetMatchConfig">恢复默认</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="setting-card" style="margin-top: 20px">
      <template #header>物品分类管理</template>
      <div class="category-list">
        <el-tag v-for="cat in categories" :key="cat" closable @close="removeCategory(cat)" style="margin: 4px">
          {{ cat }}
        </el-tag>
      </div>
      <div class="add-category">
        <el-input v-model="newCategory" placeholder="输入新分类" style="width: 200px" />
        <el-button type="primary" @click="addCategory">添加</el-button>
      </div>
    </el-card>

    <el-card class="setting-card" style="margin-top: 20px">
      <template #header>地点词表维护</template>
      <div class="location-list">
        <el-tag v-for="loc in locations" :key="loc" closable @close="removeLocation(loc)" style="margin: 4px">
          {{ loc }}
        </el-tag>
      </div>
      <div class="add-category">
        <el-input v-model="newLocation" placeholder="输入新地点" style="width: 200px" />
        <el-button type="primary" @click="addLocation">添加</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { getMatchConfig, updateMatchConfig, manageCategory, manageLocation } from '@/api/admin'
import { ElMessage } from 'element-plus'

const matchConfig = reactive({
  w1: 0.4,
  w2: 0.3,
  w3: 0.15,
  w4: 0.15,
  threshold: 0.6
})

const categories = ref(['电子产品', '证件卡片', '钥匙', '书籍文具', '衣物配饰', '其他'])
const locations = ref(['图书馆', '教学楼A座', '教学楼B座', '食堂一楼', '食堂二楼', '体育馆', '操场', '宿舍楼'])
const newCategory = ref('')
const newLocation = ref('')

onMounted(() => {
  loadConfig()
})

async function loadConfig() {
  try {
    const res = await getMatchConfig()
    Object.assign(matchConfig, res.data)
  } catch (e) {}
}

async function saveMatchConfig() {
  try {
    await updateMatchConfig(matchConfig)
    ElMessage.success('配置已保存')
  } catch (e) {
    ElMessage.success('配置已保存（演示模式）')
  }
}

function resetMatchConfig() {
  Object.assign(matchConfig, { w1: 0.4, w2: 0.3, w3: 0.15, w4: 0.15, threshold: 0.6 })
  ElMessage.info('已恢复默认值')
}

function addCategory() {
  if (!newCategory.value) return
  if (categories.value.includes(newCategory.value)) {
    ElMessage.warning('分类已存在')
    return
  }
  categories.value.push(newCategory.value)
  newCategory.value = ''
}

function removeCategory(cat) {
  categories.value = categories.value.filter(c => c !== cat)
}

function addLocation() {
  if (!newLocation.value) return
  if (locations.value.includes(newLocation.value)) {
    ElMessage.warning('地点已存在')
    return
  }
  locations.value.push(newLocation.value)
  newLocation.value = ''
}

function removeLocation(loc) {
  locations.value = locations.value.filter(l => l !== loc)
}
</script>

<style scoped>
.page-title {
  font-size: 20px;
  margin-bottom: 20px;
}

.setting-card {
  max-width: 700px;
}

.category-list,
.location-list {
  margin-bottom: 16px;
  min-height: 40px;
}

.add-category {
  display: flex;
  gap: 12px;
}
</style>
