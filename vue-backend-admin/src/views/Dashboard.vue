<template>
  <el-container class="dashboard-container">
    <el-header class="dashboard-header">
      <div class="header-content">
        <h1>
          <el-icon><DataLine /></el-icon>
          量化交易系统 - 后端管理
        </h1>
        <div class="header-actions">
          <el-button 
            v-if="currentUserId" 
            @click="handleBackToList" 
            type="info"
          >
            <el-icon><ArrowLeft /></el-icon>
            返回用户列表
          </el-button>
          <el-button @click="handleRefresh" :loading="loading">
            <el-icon><Refresh /></el-icon>
            刷新
          </el-button>
        </div>
      </div>
    </el-header>
    
    <el-main class="dashboard-main" v-loading="loading">
      <transition name="fade" mode="out-in">
        <!-- 用户列表视图 -->
        <div v-if="!currentUserId" class="user-list-view" key="user-list">
          <UserList @view-detail="handleViewUserDetail" />
        </div>
        
        <!-- 用户详情视图 -->
        <div v-else class="user-detail-view" key="user-detail">
          <div class="user-detail-header">
            <h2>
              <el-icon><User /></el-icon>
              用户详情 - {{ currentUserId }}
            </h2>
          </div>
          <el-tabs v-model="activeTab" type="border-card" class="detail-tabs">
            <el-tab-pane label="用户信息" name="info">
              <template #label>
                <span>
                  <el-icon><User /></el-icon> 用户信息
                </span>
              </template>
              <UserInfo 
                :user-info="userInfo" 
                :account-info="accountInfo"
                @refresh="loadUserData"
              />
            </el-tab-pane>
            <el-tab-pane label="当前持仓" name="positions">
              <template #label>
                <span>
                  <el-icon><Tickets /></el-icon> 当前持仓
                </span>
              </template>
              <PositionList 
                :positions="positions"
                :user-id="currentUserId"
                @refresh="loadUserData"
              />
            </el-tab-pane>
            <el-tab-pane label="平仓历史" name="history">
              <template #label>
                <span>
                  <el-icon><Finished /></el-icon> 平仓历史
                </span>
              </template>
              <CloseHistory 
                :user-id="currentUserId"
                :records="closePositionRecords"
                @refresh="loadClosePositionHistory"
              />
            </el-tab-pane>
          </el-tabs>
        </div>
      </transition>
    </el-main>
  </el-container>
</template>

<script>
import { ref, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Refresh, User, DataLine, ArrowLeft, Tickets, Finished } from '@element-plus/icons-vue'
import UserList from '../components/UserList.vue'
import UserInfo from '../components/UserInfo.vue'
import PositionList from '../components/PositionList.vue'
import CloseHistory from '../components/CloseHistory.vue'
import api from '../services/api'

export default {
  name: 'Dashboard',
  components: {
    UserList,
    UserInfo,
    PositionList,
    CloseHistory,
    Refresh,
    User,
    DataLine,
    ArrowLeft,
    Tickets,
    Finished
  },
  setup() {
    const route = useRoute()
    const router = useRouter()
    const currentUserId = ref('')
    const loading = ref(false)
    const userInfo = ref(null)
    const accountInfo = ref(null)
    const positions = ref([])
    const closePositionRecords = ref([])
    const activeTab = ref('info')

    // 从路由参数获取 userId
    onMounted(() => {
      if (route.params.userId) {
        currentUserId.value = route.params.userId
        loadUserData()
      }
    })

    // 监听路由变化
    watch(() => route.params.userId, (newUserId) => {
      if (newUserId) {
        activeTab.value = 'info'; // 切换用户时，重置到第一个tab
        currentUserId.value = newUserId
        loadUserData()
      } else {
        currentUserId.value = ''
      }
    })

    const handleViewUserDetail = (userId) => {
      currentUserId.value = userId
      router.push({ name: 'UserDetail', params: { userId } })
      // loadUserData is called by the watcher
    }

    const handleBackToList = () => {
      currentUserId.value = ''
      router.push({ name: 'Dashboard' })
      // 清空详情数据
      userInfo.value = null
      accountInfo.value = null
      positions.value = []
      closePositionRecords.value = []
    }

    const handleRefresh = () => {
      if (currentUserId.value) {
        loadUserData()
      }
    }

    const loadUserData = async () => {
      if (!currentUserId.value) return
      
      loading.value = true
      try {
        // 并行加载用户信息、账户信息和持仓
        const [userRes, accountRes, positionsRes] = await Promise.all([
          api.getUserInfo(currentUserId.value),
          api.getAccountInfo(currentUserId.value),
          api.getPositions(currentUserId.value)
        ])
        
        userInfo.value = userRes.data
        accountInfo.value = accountRes.data
        positions.value = positionsRes.data || []
        
        // 加载平仓历史
        await loadClosePositionHistory()
      } catch (error) {
        console.error('加载用户数据失败:', error)
        ElMessage.error('加载用户数据失败: ' + (error.response?.data?.message || error.message))
      } finally {
        loading.value = false
      }
    }

    const loadClosePositionHistory = async () => {
      if (!currentUserId.value) return
      
      try {
        const res = await api.getClosePositionRecords(currentUserId.value)
        closePositionRecords.value = res.data || []
      } catch (error) {
        console.error('加载平仓历史失败:', error)
        ElMessage.error('加载平仓历史失败: ' + (error.response?.data?.message || error.message))
      }
    }

    return {
      currentUserId,
      loading,
      userInfo,
      accountInfo,
      positions,
      closePositionRecords,
      activeTab,
      handleViewUserDetail,
      handleBackToList,
      handleRefresh,
      loadUserData,
      loadClosePositionHistory
    }
  }
}
</script>

<style scoped>
.dashboard-container {
  min-height: 100vh;
}

.dashboard-header {
  background: #fff;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  padding: 0;
  height: 70px !important;
}

.header-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
  height: 100%;
  padding: 0 30px;
}

.header-content h1 {
  font-size: 20px;
  font-weight: 600;
  color: #303133;
  display: flex;
  align-items: center;
  gap: 10px;
}

.header-actions {
  display: flex;
  align-items: center;
}

.dashboard-main {
  padding: 20px;
}

.user-detail-header {
  margin-bottom: 20px;
}
.user-detail-header h2 {
  font-size: 22px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 10px;
}


/* 切换动画 */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
