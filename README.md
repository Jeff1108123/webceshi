# 医疗冷链运输箱监控系统

这个目录现在包含两部分：

1. `src/` 是 Vue 2 前端，已经改造成真实接口驱动版本。
2. `medical-cold-chain-backend/` 是 Spring Boot + MySQL 后端，可直接导入 IntelliJ IDEA。

## 前端运行

```bash
npm install
npm run serve
```

开发环境请优先访问：

```text
http://127.0.0.1:8081
```

不要优先使用 `localhost`。这样可以避开浏览器对 `localhost` 域名积累的旧 Cookie，减少 `431 Request Header Fields Too Large` 问题。

## 后端运行

进入 `medical-cold-chain-backend/` 后运行：

```bash
mvnw.cmd spring-boot:run
```

默认数据库连接：

- MySQL: `127.0.0.1:3306`
- Database: `medical_cold_chain`
- Username: `root`
- Password: `123456`

数据库初始化脚本在：

```text
medical-cold-chain-backend/database/medical_cold_chain.sql
```

## IntelliJ IDEA 使用方法

1. 打开 IDEA
2. 选择 `Open`
3. 打开 `medical-cold-chain-backend/`
4. 等待 Maven 依赖自动导入完成
5. 打开 `src/main/resources/application.properties`，确认数据库用户名和密码是你的本机配置
6. 找到 `MedicalColdChainBackendApplication`
7. 点击运行即可启动后端

如果 IDEA 提示 JDK 配置：

1. 打开 `File -> Project Structure -> Project`
2. 把 `SDK` 设为本机已安装的 JDK
3. Maven 项目刷新一次

## 已实现功能

- 手机号验证码登录
- 设备申领与归还
- 每台设备独立阈值设置
- 实时温湿光、电量、信号状态展示
- 实时监测仪表盘
- 历史曲线查询
- 百度地图定位展示

## 这次额外优化

- 修复验证码请求时旧登录头误带入的问题
- 增加登录态清理逻辑，避免异常请求头导致 431
- 开发服务器改为 `127.0.0.1:8081`
- 路由改成懒加载
- 移除整包 `Element UI`
- `ECharts` 改成按需引入
