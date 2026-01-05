import { createRouter, createWebHistory } from 'vue-router'
import Dashboard from '../views/Dashboard.vue'

const routes = [
  {
    path: '/',
    name: 'Dashboard',
    component: Dashboard
  },
  {
    path: '/user/:userId',
    name: 'UserDetail',
    component: Dashboard,
    props: true
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router

