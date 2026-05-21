# 超级管理员强制归还与借用上限管理设计

## 目标

为医疗冷链运输箱系统增加两项能力：

1. 超级管理员可以在管理端强制归还用户正在借用的设备。
2. 系统限制每个用户可同时借用的设备数量，默认每人最多 3 台；超级管理员可以修改全局默认上限，也可以为单个用户设置覆盖上限。

## 整体架构

借用限制规则由后端统一校验，前端只负责展示、输入和提交配置。这样即使用户绕过页面直接调用接口，也无法突破借用上限。

后端新增借用上限配置能力：

- 全局默认上限：默认 3，可由超级管理员修改。
- 用户覆盖上限：某个用户可以单独设置上限；为空时使用全局上限。
- 申请设备校验：用户申请设备时，校验 `当前借用数 + 本次申请数 <= 有效上限`。
- 强制归还：复用现有 `/api/devices/force-return` 后端能力，补齐前端操作入口。

前端新增超级管理员独立页面 `/admin/borrow-limits`，用于管理全局和用户级借用限制。现有 `/admin/device-borrows` 页面增加“强制归还”操作。

## 用户流程

### 普通用户申请设备

1. 用户在设备管理页输入申请数量。
2. 前端调用现有 `applyDevices(count)`。
3. 后端在申请逻辑中查询该用户当前借用数量和有效上限。
4. 如果超限，返回明确业务错误，例如“当前最多可借 3 台设备，已借 2 台，本次最多还能申请 1 台”。
5. 前端沿用现有 `$message.error(error.message)` 展示错误。

### 超级管理员强制归还

1. 超级管理员进入“设备借用总览”。
2. 表格新增“操作”列。
3. 对 `BORROWED` 状态记录展示“强制归还”按钮。
4. 点击后调用 `forceReturnDevices({ deviceIds: [deviceId] })`。
5. 成功后刷新借用记录列表。

### 超级管理员配置借用上限

1. 超级管理员进入新增“借用限制管理”页面。
2. 页面顶部展示全局默认上限，可修改保存。
3. 用户表格展示用户姓名、手机号、当前借用数、用户覆盖上限、有效上限。
4. 管理员可设置某个用户的覆盖上限，也可清除覆盖值，使其回到全局默认上限。

## 后端设计

### 数据模型

新增 `app_setting` 表保存全局设置：

- `id`
- `setting_key`
- `setting_value`
- `created_at`
- `updated_at`

使用配置键：`device.borrow.limit.default`，默认值为 `3`。

扩展 `user_account` 表：

- `borrow_limit_override INT NULL`

为空表示该用户使用全局默认上限；有值时使用该覆盖值。

### DTO

新增借用限制相关 DTO：

- `BorrowLimitOverviewResponse`
  - `defaultLimit`
  - `users`
- `UserBorrowLimitResponse`
  - `userId`
  - `phone`
  - `name`
  - `organization`
  - `role`
  - `currentBorrowCount`
  - `borrowLimitOverride`
  - `effectiveBorrowLimit`
- `BorrowLimitRequest`
  - `limit`
- `UserBorrowLimitRequest`
  - `limit`

用户覆盖上限接口中 `limit` 允许为空，用于清除覆盖值。

### 接口

新增超级管理员接口，全部使用 `authService.requireAdmin(user)` 保护：

- `GET /api/devices/borrow-limits`
  - 返回全局默认上限和用户列表。
- `PUT /api/devices/borrow-limits/default`
  - 修改全局默认上限。
- `PUT /api/devices/users/{userId}/borrow-limit`
  - 设置或清除指定用户覆盖上限。

### 申请校验

在 `DeviceService.applyDevices()` 中新增校验：

1. 读取申请数量。
2. 查询当前用户在用设备数量。
3. 读取有效上限：用户覆盖上限优先，否则使用全局默认上限。
4. 计算剩余可申请数量。
5. 如果本次申请数量超过剩余数量，抛出 `BusinessException`。

配置值必须是正整数。全局默认上限不允许为空。用户覆盖上限为空表示清除覆盖，非空时也必须是正整数。

## 前端设计

### API 封装

在 `src/api/medicalColdChain.js` 新增：

- `fetchBorrowLimits()`
- `updateDefaultBorrowLimit(limit)`
- `updateUserBorrowLimit(userId, limit)`

复用已有 `forceReturnDevices(payload)`。

### 路由与导航

- `src/router/index.js` 新增 `/admin/borrow-limits` 路由。
- 路由 meta 使用 `requiresAuth: true` 和 `requiresSuperAdmin: true`。
- `src/config/navigation.js` 新增“借用限制管理”菜单项，仅超级管理员可见。

### 借用限制管理页

新增 `src/components/adminBorrowLimitPage.vue`：

- 顶部卡片：展示和编辑全局默认上限。
- 用户表格：展示用户信息、当前借用数、覆盖上限、有效上限。
- 每行提供输入框和保存按钮。
- 每行提供“清除覆盖”按钮。
- 前端校验输入为正整数；后端仍做最终校验。

### 设备借用总览页

修改 `src/components/adminDeviceBorrowPage.vue`：

- 记录归一化时保留 `deviceId`。
- 表头新增“操作”。
- `BORROWED` 状态显示“强制归还”按钮。
- 点击后调用 `forceReturnDevices({ deviceIds: [item.deviceId] })`。
- 成功后提示并刷新列表。

## 错误处理

- 借用超限：后端返回业务错误文案，前端直接展示。
- 配置非法：前端阻止明显非法输入，后端返回最终错误。
- 非超级管理员访问：前端路由守卫拦截，后端接口再次校验。
- 强制归还空目标或已归还设备：复用现有后端业务错误。

## 验证计划

后端验证：

- 编译或运行 Maven 测试。
- 验证默认全局上限为 3。
- 验证申请数量超过有效上限时失败。
- 验证用户覆盖上限优先于全局默认上限。
- 验证清除用户覆盖后回到全局默认上限。

前端验证：

- 运行 lint/build。
- 启动前后端并手动验证 UI：
  1. 普通用户默认最多 3 台。
  2. 超级管理员可以修改全局上限。
  3. 超级管理员可以设置和清除单用户覆盖上限。
  4. 单用户覆盖值影响该用户申请设备。
  5. 超级管理员可以在借用总览强制归还借用中设备。
