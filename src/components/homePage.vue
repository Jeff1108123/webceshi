<template>
  <section class="home-card">
    <h1>{{ msg || '医疗冷链运输箱监控系统' }}</h1>
    <p>请通过登录页进入系统。</p>
  </section>
</template>

<script>
export default {
  name: 'homePage',
  props: {
    msg: String
  }
}
</script>

<style scoped>
.home-card {
  width: min(520px, calc(100vw - 32px));
  margin: 80px auto;
  padding: 28px;
  border-radius: 18px;
  background: #fff;
  text-align: center;
}

h1 {
  margin-bottom: 12px;
  font-size: 26px;
}

p {
  color: #5f6c7b;
}
</style>
