<template>
  <main class="content">
    <section class="panel center-page">
      <h1>权限不足</h1>
      <p>你没有访问当前页面的权限。</p>
      <el-button type="primary" @click="$router.back()">返回</el-button>
    </section>
  </main>
</template>
