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
body,
#app {
  min-height: 100%;
}
body {
  font-family: "Microsoft Yahei", sans-serif;
  background:
    radial-gradient(circle at top left, rgba(37, 99, 235, 0.18), transparent 32%),
    radial-gradient(circle at bottom right, rgba(14, 165, 233, 0.2), transparent 28%),
    #f5f8fc;
  color: #102a43;
}
</style>
