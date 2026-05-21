# 合并历史趋势图与默认阈值收窄设计

## 目标

历史数据页将温度、湿度、光照三个独立折线统计图合并成一个组合趋势图，同时收窄新设备的默认阈值区间，让演示数据的异常更自然，避免图表视觉上显得突兀。

## 组合历史图

历史页保留顶部筛选、摘要卡、当前历史截止点和异常时刻面板。原来的三个 `TrendChartCard` 替换为一个组合图组件。

组合图同时显示三条真实数值曲线：

- 温度：蓝色，单位 `°C`
- 湿度：绿色，单位 `%`
- 光照：橙色，单位 `Lux`

三种指标单位和数值范围不同，因此不共用同一个真实 y 轴。每条曲线按自己的真实数值范围和阈值范围独立计算 y 坐标，再绘制到同一个 SVG 图框中。图例和统计信息显示真实数值，避免把 Lux、百分比和摄氏度混成一个坐标轴。

## 折线平滑

组合图使用 SVG `path` 的三次贝塞尔曲线绘制，而不是用 `polyline` 直连采样点。

这样即使某个指标在一段时间内快速升高，视觉过渡也会更平缓。后端模拟数据继续使用平滑脉冲，不恢复硬阶跃式的“满足条件后突然加固定值”。

## 默认阈值

只调整新生成默认阈值，不覆盖数据库中用户已经保存过的阈值。

新的默认值：

- 温度：`3 ~ 7°C`
- 湿度：`45 ~ 70%`
- 光照：`9 Lux`

## 前端组件边界

新增 `src/components/common/CombinedTrendChartCard.vue`：

- 输入：`labels`、`temperatureValues`、`humidityValues`、`lightValues`、`threshold`
- 负责：组合图绘制、三条线图例、最新/最低/最高统计
- 不负责：接口请求、设备选择、历史数据加载

修改 `src/components/sixthPage.vue`：

- 保留历史数据加载逻辑。
- 保留摘要卡和异常时刻面板。
- 用 `CombinedTrendChartCard` 替换三个 `TrendChartCard`。

## 后端边界

修改 `ThresholdService` 的默认阈值常量：

- `DEFAULT_TEMP_MIN = 3D`
- `DEFAULT_TEMP_MAX = 7D`
- `DEFAULT_HUMIDITY_MIN = 45D`
- `DEFAULT_HUMIDITY_MAX = 70D`
- `DEFAULT_LIGHT_MAX = 9D`

已有阈值记录不做迁移，避免覆盖用户设置。

## 验证

后端：

- 新增或更新测试，验证新设备默认阈值为 `3~7°C`、`45~70%`、`9Lux`。
- 保持模拟数据平滑性测试通过。
- 运行 `./mvnw test`。

前端：

- 运行 `npm run lint`。
- 运行 `npm run build`。
- 启动页面后确认历史页只显示一个组合趋势图，且温度、湿度、光照三条线同时显示。
