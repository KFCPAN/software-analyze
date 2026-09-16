<template>
  <div class="dashboard-page">
    <h2 class="page-title">运营数据看板</h2>
    <el-row :gutter="20" class="stat-cards">
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-item">
            <div class="stat-icon blue"><el-icon size="28"><Document /></el-icon></div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.totalPosts }}</div>
              <div class="stat-label">总发布量</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-item">
            <div class="stat-icon green"><el-icon size="28"><CircleCheck /></el-icon></div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.resolvedCount }}</div>
              <div class="stat-label">已找回/认领</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-item">
            <div class="stat-icon orange"><el-icon size="28"><MagicStick /></el-icon></div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.matchRate }}%</div>
              <div class="stat-label">匹配命中率</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-item">
            <div class="stat-icon purple"><el-icon size="28"><Clock /></el-icon></div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.avgTime }}h</div>
              <div class="stat-label">平均找回时长</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top: 20px">
      <el-col :span="14">
        <el-card>
          <template #header>发布趋势（近7天）</template>
          <div ref="chartRef" style="height: 300px"></div>
        </el-card>
      </el-col>
      <el-col :span="10">
        <el-card>
          <template #header>物品分类分布</template>
          <div ref="pieRef" style="height: 300px"></div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top: 20px">
      <el-col :span="24">
        <el-card>
          <template #header>地点热力分布</template>
          <div class="location-heatmap">
            <div v-for="loc in locationStats" :key="loc.name" class="location-bar">
              <span class="loc-name">{{ loc.name }}</span>
              <div class="bar-container">
                <div class="bar-fill" :style="{ width: (loc.count / maxLocation * 100) + '%' }"></div>
              </div>
              <span class="loc-count">{{ loc.count }}条</span>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import { getDashboardStats } from '@/api/admin'

const chartRef = ref(null)
const pieRef = ref(null)

const stats = ref({
  totalPosts: 0,
  resolvedCount: 0,
  matchRate: 0,
  avgTime: 0
})

const locationStats = ref([])
const maxLocation = ref(1)

onMounted(async () => {
  try {
    const res = await getDashboardStats()
    stats.value = res.data.stats
    locationStats.value = res.data.locations
  } catch (e) {
    stats.value = { totalPosts: 156, resolvedCount: 89, matchRate: 57, avgTime: 12 }
    locationStats.value = [
      { name: '图书馆', count: 45 },
      { name: '食堂', count: 38 },
      { name: '教学楼', count: 32 },
      { name: '体育馆', count: 18 },
      { name: '操场', count: 15 },
      { name: '宿舍楼', count: 8 }
    ]
  }
  maxLocation.value = Math.max(...locationStats.value.map(l => l.count))
  await nextTick()
  initCharts()
})

function initCharts() {
  const chart = echarts.init(chartRef.value)
  chart.setOption({
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: ['周一', '周二', '周三', '周四', '周五', '周六', '周日'] },
    yAxis: { type: 'value' },
    series: [
      { name: '寻物', type: 'line', smooth: true, data: [12, 15, 18, 14, 20, 8, 6], itemStyle: { color: '#f56c6c' } },
      { name: '招领', type: 'line', smooth: true, data: [10, 13, 16, 12, 18, 7, 5], itemStyle: { color: '#67c23a' } }
    ]
  })

  const pie = echarts.init(pieRef.value)
  pie.setOption({
    tooltip: { trigger: 'item' },
    series: [{
      type: 'pie',
      radius: ['40%', '70%'],
      data: [
        { value: 45, name: '电子产品' },
        { value: 38, name: '证件卡片' },
        { value: 25, name: '钥匙' },
        { value: 20, name: '书籍文具' },
        { value: 18, name: '衣物配饰' },
        { value: 10, name: '其他' }
      ]
    }]
  })
}
</script>

<style scoped>
.page-title {
  font-size: 20px;
  margin-bottom: 20px;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 16px;
}

.stat-icon {
  width: 56px;
  height: 56px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
}

.stat-icon.blue { background: #409eff; }
.stat-icon.green { background: #67c23a; }
.stat-icon.orange { background: #e6a23c; }
.stat-icon.purple { background: #909399; }

.stat-value {
  font-size: 28px;
  font-weight: bold;
}

.stat-label {
  font-size: 13px;
  color: #909399;
}

.location-heatmap {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.location-bar {
  display: flex;
  align-items: center;
  gap: 12px;
}

.loc-name {
  width: 80px;
  text-align: right;
  font-size: 13px;
}

.bar-container {
  flex: 1;
  height: 24px;
  background: #f0f0f0;
  border-radius: 4px;
  overflow: hidden;
}

.bar-fill {
  height: 100%;
  background: linear-gradient(90deg, #409eff, #66b1ff);
  border-radius: 4px;
  transition: width 0.5s;
}

.loc-count {
  width: 60px;
  font-size: 13px;
  color: #606266;
}
</style>
