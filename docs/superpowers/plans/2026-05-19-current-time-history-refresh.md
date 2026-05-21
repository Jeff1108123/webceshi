# Current-Time History Refresh Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the history page explicitly refresh its data window to the current time on manual refresh while improving the page layout and readability.

**Architecture:** Reuse the existing backend history endpoint because `TelemetryService.getHistoryRecords(...)` already extends the timeline to the current aligned time before returning points. The change stays local to `src/components/sixthPage.vue`: clarify the refresh semantics in the UI, show the history cutoff time more prominently, tighten the derived display state, and refine spacing/visual hierarchy so users can read the page faster.

**Tech Stack:** Vue 2 Options API, existing Axios API wrapper, existing Spring Boot history endpoint, existing date formatting utility

---

## File Structure

- **Modify:** `src/components/sixthPage.vue`
  - Clarify manual refresh behavior, show “data current through” information, refine layout spacing and visual grouping, and keep all current-time history logic in one page component.
- **Read/verify only:** `medical-cold-chain-backend/src/main/java/com/medicalcoldchain/backend/service/TelemetryService.java`
  - Confirm manual refresh already causes the backend to extend data up to the current aligned time.
- **Read/verify only:** `src/utils/dateTime.js`
  - Reuse existing `formatDateTime()` and `formatMonthDayTime()` helpers without adding a new utility unless formatting truly blocks the layout.

---

### Task 1: Make manual refresh semantics explicit in the history page logic

**Files:**
- Modify: `src/components/sixthPage.vue`

- [ ] **Step 1: Read the current refresh path and anchor the exact behavior to preserve**

Verify that `loadHistory()` in `src/components/sixthPage.vue` remains the only manual update trigger:

```js
async loadHistory() {
  if (!this.selectedDeviceId) return
  this.loading = true
  try {
    this.historyData = await fetchHistory(this.selectedDeviceId, this.hours)
    this.loadedAt = formatDateTime()
    if (!['temperature', 'humidity', 'light'].includes(this.activeMetric)) {
      this.activeMetric = 'temperature'
    }
  } catch (error) {
    this.$message.error(error.message)
  } finally {
    this.loading = false
  }
}
```

Expected: one refresh action updates the entire page payload.

- [ ] **Step 2: Add a computed field for the actual data cutoff time returned by the backend**

Add this computed property to `src/components/sixthPage.vue` near `latestSnapshot()`:

```js
dataCurrentAt() {
  return this.latestSnapshot ? this.latestSnapshot.recordedAt : ''
}
```

Expected: the UI can distinguish “I clicked refresh at X” from “history data currently extends through Y”.

- [ ] **Step 3: Show both refresh time and data cutoff in the toolbar copy**

Replace the toolbar meta text block in `src/components/sixthPage.vue` with:

```vue
<div class="toolbar-group toolbar-meta">
  <div class="status-copy">
    <strong>{{ dataCurrentAt ? `数据更新到：${formatDateTime(dataCurrentAt)}` : '等待加载历史数据' }}</strong>
    <small>{{ loadedAt ? `页面刷新时间：${loadedAt}` : '点击刷新可拉取当前时间范围内的最新历史数据' }}</small>
  </div>
  <button :disabled="!selectedDeviceId || loading" @click="loadHistory">{{ loading ? '加载中...' : '刷新到当前时间' }}</button>
</div>
```

Expected: the user can immediately see that the button advances the history window to the current time, not just redraws the page.

- [ ] **Step 4: Update the latest snapshot title to match the new semantics**

Replace this block in `src/components/sixthPage.vue`:

```vue
<span>最新采样</span>
<strong>{{ formatMonthDayTime(latestSnapshot.recordedAt) }}</strong>
```

with:

```vue
<span>当前历史截止点</span>
<strong>{{ formatMonthDayTime(latestSnapshot.recordedAt) }}</strong>
```

Expected: the snapshot panel now communicates that it is the end of the returned history range.

- [ ] **Step 5: Run a frontend build to verify the current-time copy compiles cleanly**

Run:

```bash
cd "D:/web作业/vue_kgy_frontend" && npm run build
```

Expected: PASS.

---

### Task 2: Improve the history page layout and visual hierarchy

**Files:**
- Modify: `src/components/sixthPage.vue`

- [ ] **Step 1: Tighten toolbar grouping so status copy reads as a compact status panel**

Add this markup wrapper if it is not already present in `src/components/sixthPage.vue`:

```vue
<div class="toolbar-group toolbar-meta">
  <div class="status-copy">
    <strong>{{ dataCurrentAt ? `数据更新到：${formatDateTime(dataCurrentAt)}` : '等待加载历史数据' }}</strong>
    <small>{{ loadedAt ? `页面刷新时间：${loadedAt}` : '点击刷新可拉取当前时间范围内的最新历史数据' }}</small>
  </div>
  <button :disabled="!selectedDeviceId || loading" @click="loadHistory">{{ loading ? '加载中...' : '刷新到当前时间' }}</button>
</div>
```

Expected: the right side of the toolbar becomes a compact status area rather than a single flat line of text.

- [ ] **Step 2: Rebalance summary and snapshot card styles for cleaner scanning**

Update the scoped styles in `src/components/sixthPage.vue` with these additions/refinements:

```css
.toolbar-meta {
  justify-content: space-between;
  flex: 1;
  min-width: 280px;
}

.status-copy {
  display: grid;
  gap: 4px;
}

.status-copy strong {
  font-size: 14px;
  color: var(--text-main, #0f172a);
}

.status-copy small {
  color: var(--text-muted);
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.summary-card {
  padding: 18px;
  display: grid;
  gap: 10px;
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease;
}

.summary-card:hover {
  transform: translateY(-1px);
}

.snapshot-card {
  padding: 20px;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.snapshot-card > div,
.summary-card {
  background: rgba(248, 250, 252, 0.65);
  border-radius: 14px;
}

.snapshot-card > div {
  padding: 14px;
  display: grid;
  gap: 8px;
}

.alarm-panel {
  padding: 20px;
}

.alarm-list li {
  padding: 14px 16px;
  border-radius: 14px;
  background: rgba(214, 69, 69, 0.06);
  border: 1px solid rgba(214, 69, 69, 0.08);
}
```

Expected: the page reads more like an intentionally designed dashboard and less like stacked raw sections.

- [ ] **Step 3: Improve small-screen layout for the new toolbar copy**

Replace the narrow-screen section in `src/components/sixthPage.vue` with:

```css
@media (max-width: 560px) {
  .toolbar,
  .toolbar-group,
  .toolbar-meta {
    flex-direction: column;
    align-items: stretch;
  }

  .summary-grid,
  .snapshot-card {
    grid-template-columns: 1fr;
  }

  .status-copy {
    text-align: left;
  }

  select,
  button {
    width: 100%;
  }

  select {
    min-width: 0;
  }
}
```

Expected: the refresh status copy and button stack cleanly on phones instead of competing for one line.

- [ ] **Step 4: Keep the charts unchanged unless layout changes expose a concrete defect**

Do not modify `TrendChartCard` in this task. The requirement is current-time refresh clarity plus layout polish, not chart rearchitecture.

Expected: the scope stays focused and low-risk.

- [ ] **Step 5: Run a frontend build to verify the layout changes**

Run:

```bash
cd "D:/web作业/vue_kgy_frontend" && npm run build
```

Expected: PASS.

---

### Task 3: Verify the manual current-time refresh end-to-end

**Files:**
- Modify only if needed: `src/components/sixthPage.vue`
- Verify: `medical-cold-chain-backend/src/main/java/com/medicalcoldchain/backend/service/TelemetryService.java`

- [ ] **Step 1: Confirm the backend still extends history to the current aligned time on each request**

Review this section in `TelemetryService.java`:

```java
public List<TelemetryRecord> getHistoryRecords(TransportDevice device, int hours) {
    LocalDateTime end = alignTime(LocalDateTime.now(), 5);
    ensureTimeline(device, end);
    LocalDateTime start = end.minusHours(hours);
    return telemetryRecordRepository.findByDeviceIdAndRecordedAtBetweenOrderByRecordedAtAsc(device.getId(), start, end);
}
```

Expected: clicking refresh on the frontend will pull a history window whose last point is aligned to the current time bucket.

- [ ] **Step 2: Manually verify the page behavior in the browser**

Check this exact flow:

```text
1. 打开历史数据页
2. 记录“数据更新到”显示的时间
3. 等待几分钟进入新的 5 分钟对齐区间
4. 点击“刷新到当前时间”
5. 确认：
   - “数据更新到”前进
   - 最新快照时间前进
   - 图表最后一个点前进
   - 异常列表最后的时间也同步前进（若存在异常）
```

Expected: the full history page advances to the latest available time, not just the toolbar timestamp.

- [ ] **Step 3: Run the final frontend build**

Run:

```bash
cd "D:/web作业/vue_kgy_frontend" && npm run build
```

Expected: PASS.

- [ ] **Step 4: Commit the focused change if git is available in the execution environment**

```bash
git add src/components/sixthPage.vue
git commit -m "feat: clarify current-time history refresh"
```

If the directory is not a git repo, skip commit and note that limitation in the handoff.

- [ ] **Step 5: Summarize the user-visible behavior**

Use this structure in the final handoff:

```text
历史数据页现在：
1. 刷新按钮会明确拉取到当前时间的历史数据
2. 页面会显示“数据更新到”而不只是页面刷新时间
3. 最新快照、图表末尾和异常列表会一起前进
4. 工具栏和卡片排版更清晰
```

Expected: the handoff precisely matches the delivered behavior.

---

## Self-Review

- **Spec coverage:** The plan covers both requested outcomes: update history data to current time on manual refresh, and optimize the page layout.
- **Placeholder scan:** No TODO/TBD placeholders remain; each task contains exact files, code, commands, and expected outcomes.
- **Type consistency:** The plan consistently uses `loadHistory`, `loadedAt`, `latestSnapshot`, `dataCurrentAt`, `formatDateTime`, and `formatMonthDayTime` with names already present or introduced in the plan.
