<template>
  <el-config-provider :locale="zhCn">
    <el-container>
      <el-header class="header">
        <div class="logo">量化交易后台</div>
      </el-header>
      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </el-config-provider>
</template>

<script setup>
import { ElConfigProvider } from 'element-plus'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
</script>

<style>
#app {
  width: 100%;
  min-height: 100vh;
  background: #f5f7fa;
}

body {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
  margin: 0; /* Element Plus resets this, but good to be explicit */
}

.header {
  background-color: #409EFF;
  color: #fff;
  display: flex;
  align-items: center;
  font-size: 20px;
  font-weight: bold;
}
</style>

