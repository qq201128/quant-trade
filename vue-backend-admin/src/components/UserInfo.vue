<template>
  <el-card class="user-info-card" shadow="hover">
    <template #header>
      <div class="card-header">
        <span>
          <el-icon><User /></el-icon>
          用户信息
        </span>
        <el-button type="text" @click="$emit('refresh')">
          <el-icon><Refresh /></el-icon>
        </el-button>
      </div>
    </template>
    
    <div v-if="userInfo" class="user-info-content">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="用户ID">
          <el-tag type="info">{{ userInfo.userId }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="用户名">
          {{ userInfo.username }}
        </el-descriptions-item>
        <el-descriptions-item label="邮箱">
          {{ userInfo.email || '未设置' }}
        </el-descriptions-item>
        <el-descriptions-item label="手机号">
          {{ userInfo.phone || '未设置' }}
        </el-descriptions-item>
        <el-descriptions-item label="交易所类型">
          <el-tag :type="getExchangeTypeTag(userInfo.exchangeType)">
            {{ userInfo.exchangeType || '未设置' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="账户状态">
          <el-tag :type="userInfo.enabled ? 'success' : 'danger'">
            {{ userInfo.enabled ? '启用' : '禁用' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="注册时间">
          {{ formatDateTime(userInfo.createdAt) }}
        </el-descriptions-item>
        <el-descriptions-item label="更新时间">
          {{ formatDateTime(userInfo.updatedAt) }}
        </el-descriptions-item>
      </el-descriptions>
    </div>
    
    <div v-if="accountInfo" class="account-info-content" style="margin-top: 20px;">
      <el-divider>账户信息</el-divider>
      <el-row :gutter="20">
        <el-col :span="6">
          <div class="stat-card">
            <div class="stat-label">总资产 (USDT)</div>
            <div class="stat-value primary">
              {{ formatNumber(accountInfo.totalBalance) }}
            </div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-card">
            <div class="stat-label">可用余额 (USDT)</div>
            <div class="stat-value success">
              {{ formatNumber(accountInfo.availableBalance) }}
            </div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-card">
            <div class="stat-label">冻结余额 (USDT)</div>
            <div class="stat-value warning">
              {{ formatNumber(accountInfo.frozenBalance) }}
            </div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-card">
            <div class="stat-label">账户权益 (USDT)</div>
            <div class="stat-value info">
              {{ formatNumber(accountInfo.equity) }}
            </div>
          </div>
        </el-col>
      </el-row>
      <el-row :gutter="20" style="margin-top: 15px;">
        <el-col :span="12">
          <div class="stat-card">
            <div class="stat-label">未实现盈亏 (USDT)</div>
            <div class="stat-value" :class="getPnlClass(accountInfo.unrealizedPnl)">
              {{ formatNumber(accountInfo.unrealizedPnl) }}
            </div>
          </div>
        </el-col>
        <el-col :span="12">
          <div class="stat-card">
            <div class="stat-label">更新时间</div>
            <div class="stat-value">
              {{ formatTimestamp(accountInfo.timestamp) }}
            </div>
          </div>
        </el-col>
      </el-row>
    </div>
    
    <el-empty v-if="!userInfo" description="暂无用户信息" />
  </el-card>
</template>

<script>
import { User, Refresh } from '@element-plus/icons-vue'

export default {
  name: 'UserInfo',
  components: {
    User,
    Refresh
  },
  props: {
    userInfo: {
      type: Object,
      default: null
    },
    accountInfo: {
      type: Object,
      default: null
    }
  },
  emits: ['refresh'],
  methods: {
    formatDateTime(dateTime) {
      if (!dateTime) return '-'
      return new Date(dateTime).toLocaleString('zh-CN')
    },
    
    formatTimestamp(timestamp) {
      if (!timestamp) return '-'
      return new Date(timestamp).toLocaleString('zh-CN')
    },
    
    formatNumber(value) {
      if (value === null || value === undefined) return '0.00'
      return Number(value).toLocaleString('zh-CN', {
        minimumFractionDigits: 2,
        maximumFractionDigits: 8
      })
    },
    
    getExchangeTypeTag(type) {
      if (type === 'OKX') return 'success'
      if (type === 'BINANCE') return 'warning'
      return 'info'
    },
    
    getPnlClass(pnl) {
      if (!pnl) return ''
      const value = Number(pnl)
      if (value > 0) return 'profit'
      if (value < 0) return 'loss'
      return ''
    }
  }
}
</script>

<style scoped>
.user-info-card {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.user-info-content {
  margin-top: 10px;
}

.stat-card {
  background: #f8f9fa;
  padding: 20px;
  border-radius: 8px;
  text-align: center;
  transition: all 0.3s;
}

.stat-card:hover {
  background: #e9ecef;
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.stat-label {
  font-size: 14px;
  color: #666;
  margin-bottom: 10px;
}

.stat-value {
  font-size: 24px;
  font-weight: bold;
  color: #333;
}

.stat-value.primary {
  color: #409EFF;
}

.stat-value.success {
  color: #67C23A;
}

.stat-value.warning {
  color: #E6A23C;
}

.stat-value.info {
  color: #909399;
}

.stat-value.profit {
  color: #67C23A;
}

.stat-value.loss {
  color: #F56C6C;
}
</style>

