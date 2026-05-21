# Dark Command Center Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Convert the existing Vue 2 medical cold-chain frontend into a cohesive dark, modern command-center interface without changing business logic.

**Architecture:** Keep Vue components, router, API calls, Pinia stores, and refresh behavior intact. Centralize the new visual system in `src/styles/theme.css` and `src/App.vue`, then update shell, page, and shared visualization components to consume the same variables and dark UI patterns.

**Tech Stack:** Vue 2.6, Vue Router 3, Pinia 2, Vue CLI 5, CSS variables, scoped component CSS, npm build verification.

---

## File Structure

- Modify: `src/styles/theme.css` — global dark theme variables, shared surface, buttons, inputs, tables, message, scrollbar, and utility styles.
- Modify: `src/App.vue` — app-level reset, font stack, animated dark background, base text rendering.
- Modify: `src/components/common/AppShell.vue` — dashboard header, account controls, navigation pills, content layout.
- Modify: `src/components/loginPage.vue` — dark security-access login panel.
- Modify: `src/components/homePage.vue` — dark landing card for unauthenticated fallback.
- Modify: `src/components/secondPage.vue` — KPI cards, device panels, toolbar, device metrics, state badges.
- Modify: `src/components/thirdPage.vue` — threshold control panel and form grid.
- Modify: `src/components/fourthPage.vue` — dark realtime data table.
- Modify: `src/components/fifthPage.vue` — realtime monitoring metric panels.
- Modify: `src/components/sixthPage.vue` — dark historical analytics cards and alarm panel.
- Modify: `src/components/seventhPage.vue` — dark map container and location strip.
- Modify: `src/components/adminDeviceBorrowPage.vue` — admin dark table/panel styling.
- Modify: `src/components/adminBorrowLimitPage.vue` — admin dark settings styling.
- Modify if needed: `src/components/common/MetricCard.vue`, `src/components/common/TrendChartCard.vue`, `src/components/common/CombinedTrendChartCard.vue`, `src/components/common/GaugeRing.vue` — align shared visualization cards with dark theme.
- Do not create new runtime dependencies or change backend files.

## Task 1: Establish the Global Dark Theme

**Files:**
- Modify: `vue_kgy_frontend/src/styles/theme.css`
- Modify: `vue_kgy_frontend/src/App.vue`

- [ ] **Step 1: Replace global design tokens and shared primitives**

In `src/styles/theme.css`, replace the existing content with a dark command-center theme containing these concrete primitives: CSS variables for surfaces, text, lines, blue/cyan/green/red/yellow status colors; global anchors; form font inheritance; `.page-section`; `.control-field`; `.primary-btn`; `.danger-btn`; `.ghost-btn`; `.empty-state`; dark app messages; custom scrollbar.

- [ ] **Step 2: Update app background and base typography**

In `src/App.vue`, keep the template and script unchanged. Replace only the `<style>` block so `body` uses a deep navy/black layered background with radial cold glows and a subtle grid/noise effect through `body::before`. Use a strong Chinese-capable font stack and set `#app` to full viewport minimum height.

- [ ] **Step 3: Verify the app still compiles at CSS parsing level**

Run from `vue_kgy_frontend`:

```bash
npm run build
```

Expected: build reaches Vue CLI compilation. If it fails, the error should point to CSS syntax; fix syntax before continuing.

## Task 2: Redesign the Application Shell

**Files:**
- Modify: `vue_kgy_frontend/src/components/common/AppShell.vue`

- [ ] **Step 1: Update shell template structure without changing behavior**

Keep imports, props, data, computed, and methods intact. Adjust the template classes to support: `.shell`, `.topbar`, `.brand-block`, `.eyebrow`, `.title-row`, `.system-dot`, `.account`, `.nav`, `.nav-item`, and `.content`. Keep `handleLogout`, `visibleNavigationItems`, route links, and account text unchanged.

- [ ] **Step 2: Replace scoped CSS with dashboard shell styling**

Implement a sticky-feeling top dashboard card with dark translucent background, cyan border glow, compact account chip, red logout button, and scrollable/wrapping navigation pills. Use responsive rules so the header stacks on narrow screens.

- [ ] **Step 3: Build-check shell changes**

Run from `vue_kgy_frontend`:

```bash
npm run build
```

Expected: build succeeds or fails only from unrelated existing lint warnings. Fix any template/CSS errors introduced by the shell.

## Task 3: Redesign Login and Home Pages

**Files:**
- Modify: `vue_kgy_frontend/src/components/loginPage.vue`
- Modify: `vue_kgy_frontend/src/components/homePage.vue`

- [ ] **Step 1: Update login visual layout**

Keep the login script unchanged. In the template, wrap the login form in a `.login-stage` with two children: `.access-brief` for system title/status text and `.login-card` for the existing form. Preserve `v-model`, `handleSendCode`, `handleLogin`, disabled states, and demo code rendering.

- [ ] **Step 2: Replace login scoped CSS**

Style the login page as a security access panel: dark glass card, cyan line accents, large title, dark inputs, code-row responsiveness, luminous primary button, and subdued demo code.

- [ ] **Step 3: Update home fallback card**

Change `homePage.vue` scoped CSS so the fallback card uses the same dark glass surface, centered width, cyan accent title, and muted paragraph text. Do not change props or component name.

- [ ] **Step 4: Build-check login/home**

Run from `vue_kgy_frontend`:

```bash
npm run build
```

Expected: build succeeds.

## Task 4: Redesign Device Management

**Files:**
- Modify: `vue_kgy_frontend/src/components/secondPage.vue`

- [ ] **Step 1: Preserve device-management logic**

Do not change `data`, lifecycle hooks, or methods. Keep all existing buttons and actions: apply devices, return all, refresh, threshold, monitor, and return single.

- [ ] **Step 2: Update summary and device-card styling**

Replace scoped CSS so `.summary` becomes four KPI cards with dark glass surfaces and cyan/red accents. `.device-card` becomes a dark device panel with status badge, metric tiles, route metadata, and compact action buttons.

- [ ] **Step 3: Improve responsive behavior**

Ensure `.summary` and `.metrics` collapse from four columns to two columns and then one column. Keep action buttons wrapping.

- [ ] **Step 4: Build-check device management**

Run from `vue_kgy_frontend`:

```bash
npm run build
```

Expected: build succeeds.

## Task 5: Redesign Threshold, Realtime Data, and Monitor Pages

**Files:**
- Modify: `vue_kgy_frontend/src/components/thirdPage.vue`
- Modify: `vue_kgy_frontend/src/components/fourthPage.vue`
- Modify: `vue_kgy_frontend/src/components/fifthPage.vue`

- [ ] **Step 1: Update threshold page CSS**

Keep the threshold script and template fields unchanged. Replace CSS so the toolbar, select, save button, and form grid use dark command-center controls with focus glow and responsive single-column layout.

- [ ] **Step 2: Update realtime data table CSS**

Keep the realtime data template and script unchanged. Replace CSS so the panel, toolbar, table wrapper, table, table header, alarm rows, and action buttons are dark, readable, and horizontally scrollable.

- [ ] **Step 3: Update realtime monitor CSS**

Keep computed alarm logic unchanged. Replace CSS so `.monitor-grid` uses four dark metric panels with large values, cyan normal styling, red/orange alarm styling, and responsive two-column/one-column collapse.

- [ ] **Step 4: Build-check these pages**

Run from `vue_kgy_frontend`:

```bash
npm run build
```

Expected: build succeeds.

## Task 6: Redesign History and Location Pages

**Files:**
- Modify: `vue_kgy_frontend/src/components/sixthPage.vue`
- Modify: `vue_kgy_frontend/src/components/seventhPage.vue`

- [ ] **Step 1: Update history page CSS**

Keep all history computed properties, watchers, and methods unchanged. Replace CSS so summary cards, snapshot cards, chart container area, and alarm list use dark analytic panels with active cyan state and red alarm rows.

- [ ] **Step 2: Update location page CSS and marker label colors**

Keep Baidu Map setup and polling unchanged. Update marker label style colors in `renderMarker()` from light blue to dark/cyan values that match the theme. Replace CSS so map container has dark border glow and location info is a dark floating information strip.

- [ ] **Step 3: Build-check history/location**

Run from `vue_kgy_frontend`:

```bash
npm run build
```

Expected: build succeeds.

## Task 7: Redesign Admin Pages and Shared Visualization Components

**Files:**
- Modify: `vue_kgy_frontend/src/components/adminDeviceBorrowPage.vue`
- Modify: `vue_kgy_frontend/src/components/adminBorrowLimitPage.vue`
- Modify if needed after inspection: `vue_kgy_frontend/src/components/common/MetricCard.vue`
- Modify if needed after inspection: `vue_kgy_frontend/src/components/common/TrendChartCard.vue`
- Modify if needed after inspection: `vue_kgy_frontend/src/components/common/CombinedTrendChartCard.vue`
- Modify if needed after inspection: `vue_kgy_frontend/src/components/common/GaugeRing.vue`

- [ ] **Step 1: Inspect admin and shared component style blocks**

Read the listed files and identify only light-theme scoped CSS that remains visible after Tasks 1-6.

- [ ] **Step 2: Update admin page styling**

Keep admin page logic unchanged. Convert admin panels, tables, forms, status badges, and buttons to the same dark glass, cyan action, green normal, and red danger system.

- [ ] **Step 3: Update shared visualization styling**

If shared cards still contain white surfaces or low-contrast text, update only their scoped CSS and static color constants. Preserve props, computed data, and rendering logic.

- [ ] **Step 4: Build-check admin/shared components**

Run from `vue_kgy_frontend`:

```bash
npm run build
```

Expected: build succeeds.

## Task 8: Final Verification and Commit

**Files:**
- Verify all modified frontend files.
- Commit all redesign changes, including spec and plan documents.

- [ ] **Step 1: Run final production build**

Run from `vue_kgy_frontend`:

```bash
npm run build
```

Expected: successful production build with no introduced errors.

- [ ] **Step 2: Check repository status**

Run from `vue_kgy_frontend`:

```bash
git status --short
```

Expected: modified frontend files plus new spec/plan docs. No secrets, `.env`, build output, dependency folders, or unrelated files should be staged.

- [ ] **Step 3: Review diff summary**

Run from `vue_kgy_frontend`:

```bash
git diff --stat
```

Expected: CSS-heavy Vue component changes and docs only.

- [ ] **Step 4: Stage relevant files**

Run from `vue_kgy_frontend`:

```bash
git add src/styles/theme.css src/App.vue src/components/common/AppShell.vue src/components/loginPage.vue src/components/homePage.vue src/components/secondPage.vue src/components/thirdPage.vue src/components/fourthPage.vue src/components/fifthPage.vue src/components/sixthPage.vue src/components/seventhPage.vue src/components/adminDeviceBorrowPage.vue src/components/adminBorrowLimitPage.vue src/components/common/MetricCard.vue src/components/common/TrendChartCard.vue src/components/common/CombinedTrendChartCard.vue src/components/common/GaugeRing.vue docs/superpowers/specs/2026-05-22-dark-command-center-redesign-design.md docs/superpowers/plans/2026-05-22-dark-command-center-redesign.md
```

If any optional shared component was not modified, remove it from the `git add` command and stage only existing modified files.

- [ ] **Step 5: Commit**

Run from `vue_kgy_frontend`:

```bash
git commit -m "$(cat <<'EOF'
style: modernize cold-chain dashboard UI

Apply a dark command-center visual system across the Vue frontend while preserving existing monitoring, borrowing, threshold, history, and location workflows.

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>
EOF
)"
```

Expected: new commit created. If hooks fail, fix the underlying issue, re-run build, re-stage, and create a new commit.

---

## Self-Review

- Spec coverage: global theme, shell, login, device management, threshold, realtime data, monitor, history, location, admin pages, shared components, responsiveness, and build verification are all assigned to tasks.
- Placeholder scan: no TBD/TODO/fill-in-later placeholders remain. Optional shared component modifications are explicitly gated by inspection because those files may not need changes.
- Type consistency: no new functions, props, store fields, routes, or API contracts are introduced. All tasks preserve existing Vue scripts unless specifically changing marker label colors in the location page.
