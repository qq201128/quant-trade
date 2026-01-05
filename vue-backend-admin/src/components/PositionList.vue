<template>
  <el-card class="position-card" shadow="hover">
    <template #header>
      <div class="card-header">
        <span>
          <el-icon><Box /></el-icon>
          持仓信息
        </span>
        <el-button type="text" @click="$emit('refresh')">
          <el-icon><Refresh /></el-icon>
        </el-button>
      </div>
    </template>
    
    <el-table 
      :data="positions" 
      stripe
      style="width: 100%"
      :default-sort="{ prop: 'unrealizedPnl', order: 'descending' }"
      v-loading="loading"
    >
      <el-table-column prop="symbol" label="交易对" width="150" />
      <el-table-column prop="side" label="方向" width="100">
        <template #default="{ row }">
          <el-tag :type="row.side === 'LONG' ? 'success' : 'danger'">
            {{ row.side === 'LONG' ? '做多' : '做空' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="quantity" label="持仓数量" width="150">
        <template #default="{ row }">
          {{ formatNumber(row.quantity) }}
        </template>
      </el-table-column>
      <el-table-column prop="available" label="可用数量" width="150">
        <template #default="{ row }">
          {{ formatNumber(row.available) }}
        </template>
      </el-table-column>
      <el-table-column prop="avgPrice" label="开仓均价" width="150">
        <template #default="{ row }">
          {{ formatNumber(row.avgPrice) }}
        </template>
      </el-table-column>
      <el-table-column prop="currentPrice" label="当前价格" width="150">
        <template #default="{ row }">
          {{ formatNumber(row.currentPrice) }}
        </template>
      </el-table-column>
      <el-table-column prop="leverage" label="杠杆" width="100" />
      <el-table-column prop="margin" label="保证金 (USDT)" width="150">
        <template #default="{ row }">
          {{ formatNumber(row.margin) }}
        </template>
      </el-table-column>
      <el-table-column prop="unrealizedPnl" label="未实现盈亏 (USDT)" width="180" sortable>
        <template #default="{ row }">
          <span :class="getPnlClass(row.unrealizedPnl)">
            {{ formatNumber(row.unrealizedPnl) }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="pnlPercentage" label="盈亏比例" width="120">
        <template #default="{ row }">
          <span :class="getPnlClass(row.unrealizedPnl)">
            {{ formatPercentage(row.pnlPercentage) }}
          </span>
        </template>
      </el-table-column>
    </el-table>
    
    <div v-if="positions && positions.length > 0" class="statistics-summary">
      <el-divider>持仓统计</el-divider>
      <el-row :gutter="20">
        <el-col :span="6">
          <div class="stat-item">
            <div class="stat-label">持仓数量</div>
            <div class="stat-value">{{ positions.length }}</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-item">
            <div class="stat-label">总未实现盈亏</div>
            <div class="stat-value" :class="getTotalPnlClass()">
              {{ formatNumber(getTotalPnl()) }} USDT
            </div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-item">
            <div class="stat-label">做多持仓</div>
            <div class="stat-value">{{ getLongCount() }}</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-item">
            <div class="stat-label">做空持仓</div>
            <div class="stat-value">{{ getShortCount() }}</div>
          </div>
        </el-col>
      </el-row>
    </div>
    
    <el-empty v-if="!positions || positions.length === 0" description="暂无持仓信息" />
  </el-card>
</template>

<script>
import { ref } from 'vue'
import { Box, Refresh } from '@element-plus/icons-vue'

export default {
  name: 'PositionList',
  components: {
    Box,
    Refresh
  },
  props: {
    positions: {
      type: Array,
      default: () => []
    },
    userId: {
      type: String,
      required: true
    }
  },
  emits: ['refresh'],
  setup(props) {
    const loading = ref(false)

    const formatNumber = (value) => {
      if (value === null || value === undefined) return '0.00'
      return Number(value).toLocaleString('zh-CN', {
        minimumFractionDigits: 2,
        maximumFractionDigits: 8
      })
    }

    const formatPercentage = (value) => {
      if (value === null || value === undefined) return '0.00%'
      return Number(value).toFixed(2) + '%'
    }

    const getPnlClass = (pnl) => {
      if (!pnl) return ''
      const value = Number(pnl)
      if (value > 0) return 'profit'
      if (value < 0) return 'loss'
      return ''
    }

    const getTotalPnl = () => {
      if (!props.positions || props.positions.length === 0) return 0
      return props.positions.reduce((sum, position) => {
        const pnl = Number(position.unrealizedPnl) || 0
        return sum + pnl
      }, 0)
    }

    const getTotalPnlClass = () => {
      const total = getTotalPnl()
      if (total > 0) return 'profit'
      if (total < 0) return 'loss'
      return ''
    }

    const getLongCount = () => {
      if (!props.positions) return 0
      return props.positions.filter(p => p.side === 'LONG').length
    }

    const getShortCount = () => {
      if (!props.positions) return 0
      return props.positions.filter(p => p.side === 'SHORT').length
    }

    return {
      loading,
      formatNumber,
      formatPercentage,
      getPnlClass,
      getTotalPnl,
      getTotalPnlClass,
      getLongCount,
      getShortCount
    }
  }
}
</script>

<style scoped>
.position-card {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.profit {
  color: #67C23A;
  font-weight: bold;
}

.loss {
  color: #F56C6C;
  font-weight: bold;
}

.statistics-summary {
  margin-top: 20px;
  padding-top: 20px;
}

.stat-item {
  background: #f8f9fa;
  padding: 15px;
  border-radius: 8px;
  text-align: center;
}

.stat-label {
  font-size: 14px;
  color: #666;
  margin-bottom: 8px;
}

.stat-value {
  font-size: 20px;
  font-weight: bold;
  color: #333;
}
</style>

