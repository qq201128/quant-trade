<template>
  <el-card class="history-card" shadow="hover">
    <template #header>
      <div class="card-header">
        <span>
          <el-icon><Document /></el-icon>
          平仓历史记录
        </span>
        <div class="header-actions">
          <el-date-picker
            v-model="dateRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            format="YYYY-MM-DD HH:mm:ss"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="margin-right: 10px; width: 400px;"
            @change="handleDateRangeChange"
          />
          <el-select
            v-model="selectedSymbol"
            placeholder="选择交易对"
            clearable
            style="width: 150px; margin-right: 10px;"
            @change="handleFilterChange"
          >
            <el-option
              v-for="symbol in symbolList"
              :key="symbol"
              :label="symbol"
              :value="symbol"
            />
          </el-select>
          <el-select
            v-model="selectedCloseType"
            placeholder="平仓类型"
            clearable
            style="width: 120px; margin-right: 10px;"
            @change="handleFilterChange"
          >
            <el-option label="手动平仓" value="MANUAL" />
            <el-option label="策略平仓" value="STRATEGY" />
          </el-select>
          <el-button type="text" @click="$emit('refresh')">
            <el-icon><Refresh /></el-icon>
          </el-button>
        </div>
      </div>
    </template>
    
    <el-table 
      :data="filteredRecords" 
      stripe
      style="width: 100%"
      :default-sort="{ prop: 'createdAt', order: 'descending' }"
      v-loading="loading"
      max-height="600"
    >
      <el-table-column prop="createdAt" label="平仓时间" width="180" sortable>
        <template #default="{ row }">
          {{ formatDateTime(row.createdAt) }}
        </template>
      </el-table-column>
      <el-table-column prop="symbol" label="交易对" width="120" />
      <el-table-column prop="side" label="方向" width="100">
        <template #default="{ row }">
          <el-tag :type="row.side === 'LONG' ? 'success' : 'danger'">
            {{ row.side === 'LONG' ? '做多' : '做空' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="closeQuantity" label="平仓数量" width="150">
        <template #default="{ row }">
          {{ formatNumber(row.closeQuantity) }}
        </template>
      </el-table-column>
      <el-table-column prop="avgPrice" label="开仓均价" width="150">
        <template #default="{ row }">
          {{ formatNumber(row.avgPrice) }}
        </template>
      </el-table-column>
      <el-table-column prop="closePrice" label="平仓价格" width="150">
        <template #default="{ row }">
          {{ formatNumber(row.closePrice) }}
        </template>
      </el-table-column>
      <el-table-column prop="margin" label="保证金 (USDT)" width="150">
        <template #default="{ row }">
          {{ formatNumber(row.margin) }}
        </template>
      </el-table-column>
      <el-table-column prop="leverage" label="杠杆" width="100" />
      <el-table-column prop="realizedPnl" label="已实现盈亏 (USDT)" width="150" sortable>
        <template #default="{ row }">
          <span :class="getPnlClass(row.realizedPnl)">
            {{ formatNumber(row.realizedPnl) }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="pnlPercentage" label="盈亏比例" width="120">
        <template #default="{ row }">
          <span :class="getPnlClass(row.realizedPnl)">
            {{ formatPercentage(row.pnlPercentage) }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="closeType" label="平仓类型" width="120">
        <template #default="{ row }">
          <el-tag :type="row.closeType === 'MANUAL' ? 'primary' : 'success'">
            {{ row.closeType === 'MANUAL' ? '手动平仓' : '策略平仓' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="strategyName" label="策略名称" width="150">
        <template #default="{ row }">
          {{ row.strategyName || '-' }}
        </template>
      </el-table-column>
      <el-table-column prop="orderId" label="订单ID" width="200" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.orderId || '-' }}
        </template>
      </el-table-column>
    </el-table>
    
    <div v-if="filteredRecords && filteredRecords.length > 0" class="statistics-summary">
      <el-divider>统计汇总</el-divider>
      <el-row :gutter="20">
        <el-col :span="6">
          <div class="stat-item">
            <div class="stat-label">总平仓次数</div>
            <div class="stat-value">{{ filteredRecords.length }}</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-item">
            <div class="stat-label">总已实现盈亏</div>
            <div class="stat-value" :class="getTotalPnlClass()">
              {{ formatNumber(getTotalPnl()) }} USDT
            </div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-item">
            <div class="stat-label">盈利次数</div>
            <div class="stat-value profit">{{ getProfitCount() }}</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-item">
            <div class="stat-label">亏损次数</div>
            <div class="stat-value loss">{{ getLossCount() }}</div>
          </div>
        </el-col>
      </el-row>
      <el-row :gutter="20" style="margin-top: 15px;">
        <el-col :span="6">
          <div class="stat-item">
            <div class="stat-label">胜率</div>
            <div class="stat-value">{{ getWinRate() }}%</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-item">
            <div class="stat-label">平均盈亏</div>
            <div class="stat-value" :class="getTotalPnlClass()">
              {{ formatNumber(getAveragePnl()) }} USDT
            </div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-item">
            <div class="stat-label">最大盈利</div>
            <div class="stat-value profit">{{ formatNumber(getMaxProfit()) }} USDT</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-item">
            <div class="stat-label">最大亏损</div>
            <div class="stat-value loss">{{ formatNumber(getMaxLoss()) }} USDT</div>
          </div>
        </el-col>
      </el-row>
    </div>
    
    <el-empty v-if="!filteredRecords || filteredRecords.length === 0" description="暂无平仓历史记录" />
  </el-card>
</template>

<script>
import { ref, computed } from 'vue'
import { Document, Refresh } from '@element-plus/icons-vue'

export default {
  name: 'CloseHistory',
  components: {
    Document,
    Refresh
  },
  props: {
    records: {
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
    const dateRange = ref(null)
    const selectedSymbol = ref('')
    const selectedCloseType = ref('')

    // 获取所有交易对列表
    const symbolList = computed(() => {
      if (!props.records) return []
      const symbols = new Set()
      props.records.forEach(record => {
        if (record.symbol) {
          symbols.add(record.symbol)
        }
      })
      return Array.from(symbols).sort()
    })

    // 过滤后的记录
    const filteredRecords = computed(() => {
      if (!props.records) return []
      
      let filtered = [...props.records]

      // 按交易对过滤
      if (selectedSymbol.value) {
        filtered = filtered.filter(r => r.symbol === selectedSymbol.value)
      }

      // 按平仓类型过滤
      if (selectedCloseType.value) {
        filtered = filtered.filter(r => r.closeType === selectedCloseType.value)
      }

      // 按时间范围过滤
      if (dateRange.value && dateRange.value.length === 2) {
        const [startTime, endTime] = dateRange.value
        filtered = filtered.filter(r => {
          const recordTime = new Date(r.createdAt).getTime()
          const start = new Date(startTime).getTime()
          const end = new Date(endTime).getTime()
          return recordTime >= start && recordTime <= end
        })
      }

      return filtered
    })

    const handleDateRangeChange = () => {
      // 日期范围改变时自动过滤
    }

    const handleFilterChange = () => {
      // 过滤条件改变时自动过滤
    }

    const formatDateTime = (dateTime) => {
      if (!dateTime) return '-'
      return new Date(dateTime).toLocaleString('zh-CN')
    }

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
      if (!filteredRecords.value || filteredRecords.value.length === 0) return 0
      return filteredRecords.value.reduce((sum, record) => {
        const pnl = Number(record.realizedPnl) || 0
        return sum + pnl
      }, 0)
    }

    const getTotalPnlClass = () => {
      const total = getTotalPnl()
      if (total > 0) return 'profit'
      if (total < 0) return 'loss'
      return ''
    }

    const getProfitCount = () => {
      if (!filteredRecords.value) return 0
      return filteredRecords.value.filter(r => Number(r.realizedPnl) > 0).length
    }

    const getLossCount = () => {
      if (!filteredRecords.value) return 0
      return filteredRecords.value.filter(r => Number(r.realizedPnl) < 0).length
    }

    const getWinRate = () => {
      const total = filteredRecords.value?.length || 0
      if (total === 0) return 0
      const profit = getProfitCount()
      return ((profit / total) * 100).toFixed(2)
    }

    const getAveragePnl = () => {
      const total = filteredRecords.value?.length || 0
      if (total === 0) return 0
      return getTotalPnl() / total
    }

    const getMaxProfit = () => {
      if (!filteredRecords.value || filteredRecords.value.length === 0) return 0
      return Math.max(...filteredRecords.value.map(r => Number(r.realizedPnl) || 0))
    }

    const getMaxLoss = () => {
      if (!filteredRecords.value || filteredRecords.value.length === 0) return 0
      return Math.min(...filteredRecords.value.map(r => Number(r.realizedPnl) || 0))
    }

    return {
      loading,
      dateRange,
      selectedSymbol,
      selectedCloseType,
      symbolList,
      filteredRecords,
      handleDateRangeChange,
      handleFilterChange,
      formatDateTime,
      formatNumber,
      formatPercentage,
      getPnlClass,
      getTotalPnl,
      getTotalPnlClass,
      getProfitCount,
      getLossCount,
      getWinRate,
      getAveragePnl,
      getMaxProfit,
      getMaxLoss
    }
  }
}
</script>

<style scoped>
.history-card {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-actions {
  display: flex;
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
  transition: all 0.3s;
}

.stat-item:hover {
  background: #e9ecef;
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
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

