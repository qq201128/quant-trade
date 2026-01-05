import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json'
  }
})

// 请求拦截器
api.interceptors.request.use(
  config => {
    // 可以在这里添加 token 等认证信息
    const token = localStorage.getItem('admin_token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  error => {
    return Promise.reject(error)
  }
)

// 响应拦截器
api.interceptors.response.use(
  response => {
    return response
  },
  error => {
    if (error.response) {
      const { status, data } = error.response
      if (status === 401) {
        console.error('未授权访问')
      } else if (status === 404) {
        console.warn('接口不存在:', error.config.url)
      }
    }
    return Promise.reject(error)
  }
)

export default {
  // 获取所有用户列表
  getAllUsers() {
    return api.get('/user/list')
  },

  // 获取用户信息
  getUserInfo(userId) {
    return api.get(`/user/${userId}`)
  },

  // 获取账户信息
  getAccountInfo(userId) {
    return api.get(`/account/info/${userId}`)
  },

  // 获取持仓列表
  getPositions(userId) {
    return api.get(`/account/positions/${userId}`)
  },

  // 获取平仓历史记录
  getClosePositionRecords(userId) {
    return api.get(`/account/close-positions/${userId}`)
  },

  // 刷新账户信息
  refreshAccountInfo(userId) {
    return api.post(`/account/refresh`, null, {
      headers: {
        'X-User-Id': userId
      }
    })
  }
}

