<template>
  <div class="home-page">
    <NavBar />
    <div class="home-container">
      <!-- 搜索栏 -->
      <div class="search-section">
        <el-input v-model="searchKeyword" placeholder="搜索物品名称、描述关键词..." size="large" clearable @keyup.enter="handleSearch">
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-button type="primary" size="large" @click="handleSearch">搜索</el-button>
      </div>

      <!-- 招领/遗失 双tab -->
      <div class="filter-section">
        <el-radio-group v-model="activeTab" @change="loadList">
          <el-radio-button label="found">招领</el-radio-button>
          <el-radio-button label="lost">遗失</el-radio-button>
        </el-radio-group>
        <div class="filter-right">
          <el-select v-model="filterCategory" placeholder="分类" clearable style="width: 120px" @change="loadList">
            <el-option v-for="cat in categories" :key="cat" :label="cat" :value="cat" />
          </el-select>
        </div>
      </div>

      <!-- 物品列表 -->
      <div v-loading="loading" class="item-list">
        <div v-if="list.length === 0 && !loading" class="empty-state">
          <el-empty description="暂无相关信息" />
        </div>
        <div v-else class="item-grid">
          <ItemCard v-for="item in list" :key="item.id" :item="item" />
        </div>
      </div>

      <!-- 分页 -->
      <div class="pagination">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :total="total"
          layout="prev, pager, next"
          @current-change="loadList"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import NavBar from '@/components/NavBar.vue'
import ItemCard from '@/components/ItemCard.vue'
import { getPostList, getCategories } from '@/api/post'
import { ElMessage } from 'element-plus'

const router = useRouter()

const searchKeyword = ref('')
const activeTab = ref('found')
const filterCategory = ref('')
const categories = ref(['电子产品', '证件卡片', '钥匙', '书籍文具', '衣物配饰', '其他'])
const list = ref([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(12)
const loading = ref(false)

onMounted(() => {
  loadList()
})

async function loadList() {
  loading.value = true
  try {
    const res = await getPostList({
      page: currentPage.value,
      pageSize: pageSize.value,
      type: activeTab.value,
      category: filterCategory.value,
      keyword: searchKeyword.value
    })
    list.value = res.data?.list || []
    total.value = res.data?.total || 0
  } catch (e) {
    // API未就绪时使用模拟数据
    list.value = getMockData()
    total.value = 12
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  currentPage.value = 1
  loadList()
}

// 模拟数据（后端/Mock接口就绪后删除）
function getMockData() {
  const tab = activeTab.value
  const lostData = ['黑色钱包', 'iPhone 14手机', '校园卡', '蓝色雨伞', 'AirPods耳机', '高数教材', '钥匙串', '水杯']
  const foundData = ['捡到钱包', '捡到手机', '捡到校园卡', '捡到雨伞', '捡到耳机', '捡到教材', '捡到钥匙', '捡到水杯']
  return Array.from({ length: 8 }, (_, i) => ({
    id: i + 1,
    title: (tab === 'lost' ? lostData : foundData)[i],
    description: ['在图书馆三楼丢失，内有身份证和校园卡', '食堂二楼捡到，屏幕有裂痕', '教学楼A座捡到，姓名张三', '体育馆门口捡到', '操场附近丢失，白色充电盒', '逸夫楼捡到，内有笔记', '宿舍楼下捡到，有小熊挂件', '奶茶店捡到，粉色保温杯'][i],
    location: ['图书馆', '食堂', '教学楼', '体育馆', '操场', '逸夫楼', '宿舍楼', '奶茶店'][i],
    time: new Date(Date.now() - i * 3600000).toISOString(),
    category: ['证件卡片', '电子产品', '证件卡片', '衣物配饰', '电子产品', '书籍文具', '钥匙', '其他'][i],
    type: tab,
    status: 'active',
    publisher: '用户' + (i + 1),
    images: []
  }))
}
</script>

<style scoped>
.home-page {
  min-height: 100vh;
}

.home-container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 24px 20px;
}

.search-section {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
}

.search-section .el-input {
  flex: 1;
}

.filter-section {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding: 16px;
  background: #fff;
  border-radius: 8px;
}

.filter-right {
  display: flex;
  gap: 12px;
}

.item-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 20px;
}

.empty-state {
  padding: 60px 0;
}

.pagination {
  display: flex;
  justify-content: center;
  margin-top: 32px;
}
</style>
