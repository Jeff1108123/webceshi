<template>
  <div id="app">
    <router-view />
  </div>
</template>

<script>
export default {
  name: 'App'
}
</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

html,
body {
  min-height: 100%;
}

body {
  position: relative;
  min-height: 100vh;
  overflow-x: hidden;
  color: #e6f2ff;
  font-family: "Microsoft YaHei", "PingFang SC", "Hiragino Sans GB", "Noto Sans CJK SC", "Source Han Sans SC", Arial, sans-serif;
  background:
    radial-gradient(circle at 12% 8%, rgba(34, 211, 238, 0.22), transparent 26%),
    radial-gradient(circle at 82% 12%, rgba(47, 140, 255, 0.18), transparent 28%),
    radial-gradient(circle at 70% 82%, rgba(33, 208, 122, 0.1), transparent 24%),
    linear-gradient(135deg, #020711 0%, #061326 48%, #020914 100%);
}

body::before {
  content: "";
  position: fixed;
  inset: 0;
  z-index: -1;
  pointer-events: none;
  background-image:
    linear-gradient(rgba(113, 206, 255, 0.055) 1px, transparent 1px),
    linear-gradient(90deg, rgba(113, 206, 255, 0.055) 1px, transparent 1px),
    radial-gradient(circle at 50% 50%, rgba(255, 255, 255, 0.035) 0 1px, transparent 1px);
  background-size: 44px 44px, 44px 44px, 3px 3px;
  -webkit-mask-image: linear-gradient(to bottom, rgba(0, 0, 0, 0.9), rgba(0, 0, 0, 0.42));
  mask-image: linear-gradient(to bottom, rgba(0, 0, 0, 0.9), rgba(0, 0, 0, 0.42));
}

#app {
  min-height: 100vh;
}
</style>
