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
  position: relative;
  width: min(560px, calc(100vw - 32px));
  margin: 88px auto;
  padding: 34px;
  border: 1px solid rgba(125, 211, 252, 0.18);
  border-radius: 24px;
  background: linear-gradient(145deg, rgba(15, 23, 42, 0.9), rgba(2, 8, 23, 0.78));
  box-shadow: 0 24px 80px rgba(0, 0, 0, 0.3), inset 0 1px 0 rgba(255, 255, 255, 0.06);
  text-align: center;
  backdrop-filter: blur(18px);
  overflow: hidden;
}

.home-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 32px;
  right: 32px;
  height: 2px;
  background: linear-gradient(90deg, transparent, #22d3ee, transparent);
  box-shadow: 0 0 18px rgba(34, 211, 238, 0.68);
}

h1 {
  margin-bottom: 12px;
  color: #67e8f9;
  font-size: 28px;
  letter-spacing: -0.03em;
}

p {
  color: #94a3b8;
  line-height: 1.7;
}
</style>
