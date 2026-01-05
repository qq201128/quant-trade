<template>
  <el-card class="user-list-card" shadow="hover">
    <template #header>
      <div class="card-header">
        <span>
          <el-icon><UserFilled /></el-icon>
          用户列表
        </span>
        <el-button type="text" @click="loadUsers" :loading="loading">
          <el-icon><Refresh /></el-icon>
        </el-button>
      </div>
    </template>
    
    <el-table 
      :data="users" 
      stripe
      style="width: 100%"
      :default-sort="{ prop: 'createdAt', order: 'descending' }"
      v-loading="loading"
      @row-click="handleRowClick"
      class="user-table"
    >
      <el-table-column prop="userId" label="用户ID" width="180" />
      <el-table-column prop="username" label="用户名" width="150" />
      <el-table-column prop="email" label="邮箱" width="200" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.email || '-' }}
        </template>
      </el-table-column>
      <el-table-column prop="phone" label="手机号" width="150">
        <template #default="{ row }">
          {{ row.phone || '-' }}
        </template>
      </el-table-column>
      <el-table-column prop="exchangeType" label="交易所" width="120">
        <template #default="{ row }">
          <el-tag :type="getExchangeTypeTag(row.exchangeType)" v-if="row.exchangeType">
            {{ row.exchangeType }}
          </el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column prop="enabled" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'danger'">
            {{ row.enabled ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="注册时间" width="180" sortable>
        <template #default="{ row }">
          {{ formatDateTime(row.createdAt) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link @click.stop="handleViewDetail(row.userId)">
            <el-icon><View /></el-icon>
            查看详情
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    
    <div v-if="users && users.length > 0" class="statistics-summary">
      <el-divider>统计信息</el-divider>
      <el-row :gutter="20">
        <el-col :span="6">
          <div class="stat-item">
            <div class="stat-label">总用户数</div>
            <div class="stat-value">{{ users.length }}</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-item">
            <div class="stat-label">启用用户</div>
            <div class="stat-value success">{{ getEnabledCount() }}</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-item">
            <div class="stat-label">禁用用户</div>
            <div class="stat-value danger">{{ getDisabledCount() }}</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-item">
            <div class="stat-label">已配置交易所</div>
            <div class="stat-value">{{ getConfiguredCount() }}</div>
          </div>
        </el-col>
      </el-row>
    </div>
    
    <el-empty v-if="!users || users.length === 0" description="暂无用户数据" />
  </el-card>
</template>

<script>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { UserFilled, Refresh, View } from '@element-plus/icons-vue'
import api from '../services/api'

export default {
  name: 'UserList',
  components: {
    UserFilled,
    Refresh,
    View
  },
  emits: ['view-detail'],
  setup(props, { emit }) {
    const users = ref([])
    const loading = ref(false)

    const loadUsers = async () => {
      loading.value = true
      try {
        const res = await api.getAllUsers()
        users.value = res.data || []
      } catch (error) {
        console.error('加载用户列表失败:', error)
        ElMessage.error('加载用户列表失败: ' + (error.response?.data?.message || error.message))
      } finally {
        loading.value = false
      }
    }

    const handleRowClick = (row) => {
      emit('view-detail', row.userId)
    }

    const handleViewDetail = (userId) => {
      emit('view-detail', userId)
    }

    const formatDateTime = (dateTime) => {
      if (!dateTime) return '-'
      return new Date(dateTime).toLocaleString('zh-CN')
    }

    const getExchangeTypeTag = (type) => {
      if (type === 'OKX') return 'success'
      if (type === 'BINANCE') return 'warning'
      return 'info'
    }

    const getEnabledCount = () => {
      return users.value.filter(u => u.enabled).length
    }

    const getDisabledCount = () => {
      return users.value.filter(u => !u.enabled).length
    }

    const getConfiguredCount = () => {
      return users.value.filter(u => u.exchangeType).length
    }

    onMounted(() => {
      loadUsers()
    })

    return {
      users,
      loading,
      loadUsers,
      handleRowClick,
      handleViewDetail,
      formatDateTime,
      getExchangeTypeTag,
      getEnabledCount,
      getDisabledCount,
      getConfiguredCount
    }
  }
}
</script>

<style scoped>
.user-list-card {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.user-table {
  cursor: pointer;
}

.user-table :deep(.el-table__row) {
  cursor: pointer;
}

.user-table :deep(.el-table__row:hover) {
  background-color: #f5f7fa;
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

.stat-value.success {
  color: #67C23A;
}

.stat-value.danger {
  color: #F56C6C;
}
</style>

