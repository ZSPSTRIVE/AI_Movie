<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import {
  checkin,
  getSignStatus,
  getPointBalance,
  getPointLogs,
  getCouponTemplates,
  exchangeCoupon,
  getMyCoupons,
  useCoupon,
  type SignStatusVO,
  type CouponTemplateVO,
  type UserCouponVO,
  type PointLogVO
} from '@/api/growth'

const userStore = useUserStore()
const activeTab = ref('mall')
const loading = ref(false)

const signStatus = ref<SignStatusVO | null>(null)
const signLoading = ref(false)
const pointBalance = ref(0)
const pointLogs = ref<PointLogVO[]>([])
const logsLoading = ref(false)
const couponTemplates = ref<CouponTemplateVO[]>([])
const myCoupons = ref<UserCouponVO[]>([])
const templatesLoading = ref(false)
const couponsLoading = ref(false)

const isLogin = computed(() => userStore.isLogin)

const currentMonth = computed(() => {
  const now = new Date()
  return `${now.getFullYear()}年${now.getMonth() + 1}月`
})

const calendarDays = computed(() => {
  const now = new Date()
  const daysInMonth = new Date(now.getFullYear(), now.getMonth() + 1, 0).getDate()
  const signedDays = signStatus.value?.signedDays || []
  return Array.from({ length: daysInMonth }, (_, i) => ({
    day: i + 1,
    signed: signedDays.includes(i + 1),
    isToday: i + 1 === now.getDate()
  }))
})

onMounted(() => {
  if (isLogin.value) {
    loadData()
  }
})

async function loadData() {
  await Promise.all([
    loadSignStatus(),
    loadPointBalance(),
    loadCouponTemplates(),
    loadMyCoupons()
  ])
}

async function loadSignStatus() {
  try {
    const res = await getSignStatus()
    signStatus.value = res.data
  } catch (e) {
    console.error('加载签到状态失败', e)
  }
}

async function loadPointBalance() {
  try {
    const res = await getPointBalance()
    pointBalance.value = res.data || 0
  } catch (e) {
    console.error('加载积分余额失败', e)
  }
}

async function loadPointLogs() {
  logsLoading.value = true
  try {
    const res = await getPointLogs(1, 50)
    pointLogs.value = res.data?.rows || []
  } catch (e) {
    console.error('加载积分流水失败', e)
  } finally {
    logsLoading.value = false
  }
}

async function loadCouponTemplates() {
  templatesLoading.value = true
  try {
    const res = await getCouponTemplates()
    couponTemplates.value = res.data || []
  } catch (e) {
    console.error('加载优惠券模板失败', e)
  } finally {
    templatesLoading.value = false
  }
}

async function loadMyCoupons() {
  couponsLoading.value = true
  try {
    const res = await getMyCoupons()
    myCoupons.value = res.data || []
  } catch (e) {
    console.error('加载我的优惠券失败', e)
  } finally {
    couponsLoading.value = false
  }
}

async function handleCheckin() {
  if (signStatus.value?.signedToday) {
    ElMessage.info('今天已经签到过了~')
    return
  }
  signLoading.value = true
  try {
    await checkin()
    ElMessage.success('签到成功! +10积分')
    await loadSignStatus()
    await loadPointBalance()
  } catch (e: any) {
    ElMessage.error(e.message || '签到失败')
  } finally {
    signLoading.value = false
  }
}

async function handleExchange(template: CouponTemplateVO) {
  if (template.remainStock <= 0) {
    ElMessage.warning('库存不足')
    return
  }
  if (pointBalance.value < template.pointsRequired) {
    ElMessage.warning('积分不足')
    return
  }

  try {
    await ElMessageBox.confirm(
      `确定使用 ${template.pointsRequired} 积分兑换「${template.title}」吗？`,
      '确认兑换',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'info' }
    )
  } catch {
    return
  }

  loading.value = true
  try {
    await exchangeCoupon(template.id)
    ElMessage.success('兑换成功!')
    await Promise.all([loadPointBalance(), loadCouponTemplates(), loadMyCoupons()])
  } catch (e: any) {
    ElMessage.error(e.message || '兑换失败')
  } finally {
    loading.value = false
  }
}

async function handleUseCoupon(coupon: UserCouponVO) {
  if (coupon.status !== 0) {
    ElMessage.warning(coupon.status === 1 ? '优惠券已使用' : '优惠券已过期')
    return
  }

  try {
    await ElMessageBox.confirm(
      `确定使用「${coupon.title}」吗？使用后无法恢复。`,
      '确认使用',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }

  loading.value = true
  try {
    await useCoupon(coupon.id)
    ElMessage.success('使用成功!')
    await loadMyCoupons()
  } catch (e: any) {
    ElMessage.error(e.message || '使用失败')
  } finally {
    loading.value = false
  }
}

function getCouponStatusText(status: number) {
  switch (status) {
    case 0: return '可使用'
    case 1: return '已使用'
    case 2: return '已过期'
    default: return '未知'
  }
}

function formatDate(dateStr: string) {
  if (!dateStr) return ''
  return new Date(dateStr).toLocaleDateString('zh-CN')
}

function onTabChange(tab: string) {
  activeTab.value = tab
  if (tab === 'logs' && pointLogs.value.length === 0) {
    loadPointLogs()
  }
}
</script>

<template>
  <div class="growth-page">
    <!-- 动态背景 -->
    <div class="dynamic-bg">
      <div class="gradient-layer"></div>
      <div class="particles">
        <div v-for="i in 20" :key="i" class="particle" :style="{ '--delay': i * 0.5 + 's', '--x': Math.random() * 100 + '%', '--duration': 15 + Math.random() * 10 + 's' }"></div>
      </div>
    </div>

    <div class="content-wrapper">
      <!-- 页面标题 -->
      <div class="page-header">
        <h1 class="page-title">积分中心</h1>
        <p class="page-subtitle">签到赚积分，兑换专属好礼</p>
      </div>

      <!-- 主布局：左右分栏 -->
      <div class="main-layout">
        <!-- 左侧：用户信息 + 签到日历 -->
        <div class="left-panel">
          <!-- 用户卡片 -->
          <div class="glass-card user-card">
            <div class="user-header">
              <div class="avatar-wrapper">
                <img :src="userStore.userInfo?.avatar || 'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png'" class="avatar" />
                <div class="avatar-ring"></div>
              </div>
              <div class="user-info">
                <div class="nickname">{{ userStore.userInfo?.nickname || '游客' }}</div>
                <div class="user-level">Lv.{{ Math.floor(pointBalance / 100) + 1 }}</div>
              </div>
            </div>
            <div class="points-display">
              <div class="points-value">{{ pointBalance.toLocaleString() }}</div>
              <div class="points-label">可用积分</div>
            </div>
            <div class="stats-row">
              <div class="stat-item">
                <span class="stat-num">{{ signStatus?.monthTotalDays || 0 }}</span>
                <span class="stat-text">本月签到</span>
              </div>
              <div class="stat-divider"></div>
              <div class="stat-item">
                <span class="stat-num">{{ signStatus?.continuousDays || 0 }}</span>
                <span class="stat-text">连续天数</span>
              </div>
            </div>
          </div>

          <!-- 签到日历 -->
          <div class="glass-card calendar-card">
            <div class="calendar-header">
              <span class="calendar-title">{{ currentMonth }} 签到</span>
              <button
                class="checkin-btn"
                :class="{ checked: signStatus?.signedToday }"
                :disabled="signLoading || signStatus?.signedToday"
                @click="handleCheckin"
              >
                <span class="btn-icon">{{ signStatus?.signedToday ? '✓' : '☀' }}</span>
                <span class="btn-text">{{ signLoading ? '签到中' : (signStatus?.signedToday ? '已签到' : '签到 +10') }}</span>
              </button>
            </div>
            <div class="calendar-grid">
              <div
                v-for="day in calendarDays"
                :key="day.day"
                class="calendar-day"
                :class="{ signed: day.signed, today: day.isToday }"
              >
                <span class="day-num">{{ day.day }}</span>
                <span v-if="day.signed" class="day-check">✓</span>
              </div>
            </div>
          </div>
        </div>

        <!-- 右侧：商城内容 -->
        <div class="right-panel">
          <!-- 标签导航 -->
          <div class="glass-card tabs-card">
            <div class="tabs-nav">
              <button
                class="tab-item"
                :class="{ active: activeTab === 'mall' }"
                @click="onTabChange('mall')"
              >
                <span class="tab-icon">✦</span>
                <span>积分商城</span>
              </button>
              <button
                class="tab-item"
                :class="{ active: activeTab === 'my' }"
                @click="onTabChange('my')"
              >
                <span class="tab-icon">♦</span>
                <span>我的券包</span>
              </button>
              <button
                class="tab-item"
                :class="{ active: activeTab === 'logs' }"
                @click="onTabChange('logs')"
              >
                <span class="tab-icon">◈</span>
                <span>积分明细</span>
              </button>
            </div>
          </div>

          <!-- 积分商城 -->
          <div v-show="activeTab === 'mall'" class="content-section">
            <div v-if="templatesLoading" class="glass-card loading-card">
              <div class="loading-spinner"></div>
              <span>加载中...</span>
            </div>
            <div v-else-if="couponTemplates.length === 0" class="glass-card empty-card">
              <div class="empty-icon">🎁</div>
              <span>暂无可兑换的优惠券</span>
            </div>
            <div v-else class="coupon-grid">
              <div
                v-for="template in couponTemplates"
                :key="template.id"
                class="glass-card coupon-card"
                :class="{ 'sold-out': template.remainStock <= 0 }"
              >
                <div class="coupon-badge" v-if="template.remainStock <= 5 && template.remainStock > 0">限量</div>
                <div class="coupon-icon-wrap">
                  <span class="coupon-icon">🎫</span>
                </div>
                <div class="coupon-info">
                  <h3 class="coupon-title">{{ template.title }}</h3>
                  <div class="coupon-meta">
                    <span class="stock">剩余 {{ template.remainStock }}</span>
                    <span class="expire">{{ formatDate(template.endTime) }} 到期</span>
                  </div>
                </div>
                <div class="coupon-footer">
                  <div class="price-tag">
                    <span class="price-num">{{ template.pointsRequired }}</span>
                    <span class="price-unit">积分</span>
                  </div>
                  <button
                    class="exchange-btn"
                    :disabled="loading || template.remainStock <= 0 || pointBalance < template.pointsRequired"
                    @click="handleExchange(template)"
                  >
                    {{ template.remainStock <= 0 ? '已兑完' : (pointBalance < template.pointsRequired ? '积分不足' : '立即兑换') }}
                  </button>
                </div>
              </div>
            </div>
          </div>

          <!-- 我的优惠券 -->
          <div v-show="activeTab === 'my'" class="content-section">
            <div v-if="couponsLoading" class="glass-card loading-card">
              <div class="loading-spinner"></div>
              <span>加载中...</span>
            </div>
            <div v-else-if="myCoupons.length === 0" class="glass-card empty-card">
              <div class="empty-icon">🎫</div>
              <span>暂无优惠券，快去兑换吧~</span>
            </div>
            <div v-else class="my-coupon-list">
              <div
                v-for="coupon in myCoupons"
                :key="coupon.id"
                class="glass-card my-coupon-card"
                :class="{ used: coupon.status === 1, expired: coupon.status === 2 }"
              >
                <div class="coupon-left">
                  <div class="coupon-icon-sm">🎫</div>
                </div>
                <div class="coupon-content">
                  <div class="coupon-name">{{ coupon.title }}</div>
                  <div class="coupon-date">
                    {{ coupon.status === 1 ? '使用于 ' + formatDate(coupon.useTime || '') : '有效期至 ' + formatDate(coupon.expireTime) }}
                  </div>
                </div>
                <div class="coupon-action">
                  <span class="status-tag" :class="{ available: coupon.status === 0 }">
                    {{ getCouponStatusText(coupon.status) }}
                  </span>
                  <button
                    v-if="coupon.status === 0"
                    class="use-btn"
                    :disabled="loading"
                    @click="handleUseCoupon(coupon)"
                  >
                    立即使用
                  </button>
                </div>
              </div>
            </div>
          </div>

          <!-- 积分明细 -->
          <div v-show="activeTab === 'logs'" class="content-section">
            <div v-if="logsLoading" class="glass-card loading-card">
              <div class="loading-spinner"></div>
              <span>加载中...</span>
            </div>
            <div v-else-if="pointLogs.length === 0" class="glass-card empty-card">
              <div class="empty-icon">📜</div>
              <span>暂无积分记录</span>
            </div>
            <div v-else class="logs-list">
              <div v-for="log in pointLogs" :key="log.id" class="glass-card log-item">
                <div class="log-icon" :class="{ income: log.amount > 0 }">
                  {{ log.amount > 0 ? '↑' : '↓' }}
                </div>
                <div class="log-info">
                  <div class="log-title">{{ log.typeName }}</div>
                  <div class="log-desc">{{ log.remark }}</div>
                </div>
                <div class="log-amount" :class="{ positive: log.amount > 0 }">
                  {{ log.amount > 0 ? '+' : '' }}{{ log.amount }}
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* ═══════════════════════════════════════════════════════════
   Apple Glassmorphism + Anime Aesthetic UI System
   ═══════════════════════════════════════════════════════════ */

.growth-page {
  position: relative;
  min-height: 100vh;
  overflow: hidden;
  font-family: -apple-system, BlinkMacSystemFont, 'SF Pro Display', 'SF Pro Text', sans-serif;
}

/* ─── Dynamic Background ─── */
.dynamic-bg {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 0;
  overflow: hidden;
}

.gradient-layer {
  position: absolute;
  inset: 0;
  background: 
    radial-gradient(ellipse at 20% 20%, rgba(14, 165, 233, 0.2) 0%, transparent 50%),
    radial-gradient(ellipse at 80% 30%, rgba(6, 182, 212, 0.18) 0%, transparent 50%),
    radial-gradient(ellipse at 40% 80%, rgba(56, 189, 248, 0.15) 0%, transparent 50%),
    linear-gradient(135deg, #e0f2fe 0%, #f0fdfa 30%, #ecfeff 60%, #f0f9ff 100%);
}

.particles {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.particle {
  position: absolute;
  width: 6px;
  height: 6px;
  background: rgba(255, 255, 255, 0.9);
  border-radius: 50%;
  left: var(--x);
  animation: float var(--duration) ease-in-out infinite;
  animation-delay: var(--delay);
  box-shadow: 0 0 12px rgba(14, 165, 233, 0.5);
}

@keyframes float {
  0%, 100% { transform: translateY(100vh) scale(0); opacity: 0; }
  10% { opacity: 1; transform: translateY(90vh) scale(1); }
  90% { opacity: 1; transform: translateY(10vh) scale(1); }
  100% { opacity: 0; transform: translateY(0) scale(0); }
}

/* ─── Content Wrapper ─── */
.content-wrapper {
  position: relative;
  z-index: 1;
  max-width: 1200px;
  margin: 0 auto;
  padding: 32px 24px;
}

/* ─── Page Header ─── */
.page-header {
  text-align: center;
  margin-bottom: 40px;
}

.page-title {
  font-size: 34px;
  font-weight: 700;
  background: linear-gradient(135deg, #0ea5e9 0%, #06b6d4 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  letter-spacing: -0.5px;
  margin: 0 0 8px 0;
}

.page-subtitle {
  font-size: 17px;
  color: #94a3b8;
  margin: 0;
  font-weight: 400;
}

/* ─── Glass Card Base ─── */
.glass-card {
  background: rgba(255, 255, 255, 0.15);
  backdrop-filter: blur(24px);
  -webkit-backdrop-filter: blur(24px);
  border-radius: 24px;
  border: 1.5px solid rgba(255, 255, 255, 0.35);
  box-shadow: 
    0 20px 40px rgba(0, 0, 0, 0.06),
    inset 0 1px 0 rgba(255, 255, 255, 0.6),
    inset 0 -1px 0 rgba(255, 255, 255, 0.1),
    inset 1px 0 0 rgba(255, 255, 255, 0.2),
    inset -1px 0 0 rgba(255, 255, 255, 0.2);
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
}

.glass-card:hover {
  transform: translateY(-3px);
  border-color: rgba(255, 255, 255, 0.5);
  box-shadow: 
    0 28px 56px rgba(0, 0, 0, 0.1),
    inset 0 1px 0 rgba(255, 255, 255, 0.7),
    inset 0 -1px 0 rgba(255, 255, 255, 0.15),
    inset 1px 0 0 rgba(255, 255, 255, 0.25),
    inset -1px 0 0 rgba(255, 255, 255, 0.25);
}

/* ─── Main Layout ─── */
.main-layout {
  display: grid;
  grid-template-columns: 340px 1fr;
  gap: 24px;
  align-items: start;
}

.left-panel {
  display: flex;
  flex-direction: column;
  gap: 20px;
  position: sticky;
  top: 24px;
}

.right-panel {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* ─── User Card ─── */
.user-card {
  padding: 28px;
  text-align: center;
}

.user-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 24px;
}

.avatar-wrapper {
  position: relative;
  flex-shrink: 0;
}

.avatar {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  object-fit: cover;
  border: 3px solid rgba(255, 255, 255, 0.6);
}

.avatar-ring {
  position: absolute;
  inset: -6px;
  border-radius: 50%;
  border: 2px solid transparent;
  background: linear-gradient(135deg, #0ea5e9, #06b6d4) border-box;
  -webkit-mask: linear-gradient(#fff 0 0) padding-box, linear-gradient(#fff 0 0);
  mask: linear-gradient(#fff 0 0) padding-box, linear-gradient(#fff 0 0);
  -webkit-mask-composite: xor;
  mask-composite: exclude;
  animation: ring-rotate 8s linear infinite;
}

@keyframes ring-rotate {
  to { transform: rotate(360deg); }
}

.user-info {
  text-align: left;
}

.nickname {
  font-size: 20px;
  font-weight: 600;
  color: #1e293b;
  margin-bottom: 4px;
}

.user-level {
  font-size: 13px;
  color: #64748b;
  background: linear-gradient(135deg, rgba(14, 165, 233, 0.12), rgba(6, 182, 212, 0.12));
  padding: 4px 10px;
  border-radius: 12px;
  display: inline-block;
}

.points-display {
  background: linear-gradient(135deg, rgba(14, 165, 233, 0.1), rgba(6, 182, 212, 0.08));
  border-radius: 16px;
  padding: 20px;
  margin-bottom: 20px;
}

.points-display .points-value {
  font-size: 42px;
  font-weight: 700;
  background: linear-gradient(135deg, #0ea5e9, #06b6d4);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  line-height: 1.1;
}

.points-display .points-label {
  font-size: 14px;
  color: #94a3b8;
  margin-top: 4px;
}

.stats-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 24px;
}

.stat-item {
  text-align: center;
}

.stat-num {
  display: block;
  font-size: 24px;
  font-weight: 600;
  color: #334155;
}

.stat-text {
  font-size: 12px;
  color: #94a3b8;
}

.stat-divider {
  width: 1px;
  height: 32px;
  background: rgba(0, 0, 0, 0.08);
}

/* ─── Calendar Card ─── */
.calendar-card {
  padding: 24px;
}

.calendar-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.calendar-title {
  font-size: 17px;
  font-weight: 600;
  color: #334155;
}

.checkin-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 18px;
  border: none;
  border-radius: 20px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s;
  background: linear-gradient(135deg, #0ea5e9, #06b6d4);
  color: white;
  box-shadow: 0 4px 16px rgba(14, 165, 233, 0.35);
}

.checkin-btn:hover:not(:disabled) {
  transform: scale(1.05);
  box-shadow: 0 6px 24px rgba(14, 165, 233, 0.45);
}

.checkin-btn.checked {
  background: rgba(0, 0, 0, 0.06);
  color: #94a3b8;
  box-shadow: none;
}

.checkin-btn:disabled {
  cursor: default;
}

.btn-icon {
  font-size: 16px;
}

.calendar-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 6px;
}

.calendar-day {
  aspect-ratio: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
  background: rgba(0, 0, 0, 0.02);
  position: relative;
  font-size: 13px;
  color: #64748b;
  transition: all 0.2s;
}

.calendar-day.today {
  background: rgba(14, 165, 233, 0.12);
  color: #0ea5e9;
  font-weight: 600;
}

.calendar-day.signed {
  background: linear-gradient(135deg, rgba(14, 165, 233, 0.15), rgba(6, 182, 212, 0.12));
  color: #0ea5e9;
}

.day-check {
  position: absolute;
  bottom: 2px;
  font-size: 10px;
  color: #06b6d4;
}

/* ─── Tabs Card ─── */
.tabs-card {
  padding: 8px;
}

.tabs-nav {
  display: flex;
  gap: 4px;
}

.tab-item {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 14px 20px;
  border: none;
  border-radius: 18px;
  font-size: 15px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s;
  background: transparent;
  color: #64748b;
}

.tab-item:hover {
  background: rgba(0, 0, 0, 0.03);
}

.tab-item.active {
  background: rgba(255, 255, 255, 0.7);
  color: #0ea5e9;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.06);
}

.tab-icon {
  font-size: 14px;
}

/* ─── Content Section ─── */
.content-section {
  min-height: 300px;
}

/* ─── Coupon Grid ─── */
.coupon-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 16px;
}

.coupon-card {
  padding: 24px;
  position: relative;
  overflow: hidden;
}

.coupon-card.sold-out {
  opacity: 0.5;
}

.coupon-badge {
  position: absolute;
  top: 16px;
  right: 16px;
  background: linear-gradient(135deg, #f97316, #fb923c);
  color: white;
  font-size: 11px;
  font-weight: 600;
  padding: 4px 10px;
  border-radius: 10px;
}

.coupon-icon-wrap {
  width: 56px;
  height: 56px;
  background: linear-gradient(135deg, rgba(14, 165, 233, 0.12), rgba(6, 182, 212, 0.1));
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 16px;
}

.coupon-icon {
  font-size: 28px;
}

.coupon-info .coupon-title {
  font-size: 17px;
  font-weight: 600;
  color: #1e293b;
  margin: 0 0 8px 0;
}

.coupon-meta {
  display: flex;
  gap: 12px;
  font-size: 13px;
  color: #94a3b8;
}

.coupon-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid rgba(0, 0, 0, 0.05);
}

.price-tag {
  display: flex;
  align-items: baseline;
  gap: 4px;
}

.price-num {
  font-size: 28px;
  font-weight: 700;
  background: linear-gradient(135deg, #0ea5e9, #06b6d4);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.price-unit {
  font-size: 13px;
  color: #94a3b8;
}

.exchange-btn {
  padding: 12px 24px;
  border: none;
  border-radius: 16px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s;
  background: linear-gradient(135deg, #0ea5e9, #06b6d4);
  color: white;
  box-shadow: 0 4px 16px rgba(14, 165, 233, 0.3);
}

.exchange-btn:hover:not(:disabled) {
  transform: scale(1.03);
  box-shadow: 0 6px 24px rgba(14, 165, 233, 0.4);
}

.exchange-btn:disabled {
  background: rgba(0, 0, 0, 0.08);
  color: #475569;
  box-shadow: none;
  cursor: not-allowed;
}

/* ─── My Coupon List ─── */
.my-coupon-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.my-coupon-card {
  display: flex;
  align-items: center;
  padding: 20px;
  gap: 16px;
}

.my-coupon-card.used,
.my-coupon-card.expired {
  opacity: 0.5;
}

.coupon-left {
  flex-shrink: 0;
}

.coupon-icon-sm {
  width: 48px;
  height: 48px;
  background: linear-gradient(135deg, rgba(14, 165, 233, 0.12), rgba(6, 182, 212, 0.1));
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
}

.coupon-content {
  flex: 1;
}

.coupon-name {
  font-size: 16px;
  font-weight: 600;
  color: #1e293b;
  margin-bottom: 4px;
}

.coupon-date {
  font-size: 13px;
  color: #94a3b8;
}

.coupon-action {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
}

.status-tag {
  font-size: 12px;
  color: #94a3b8;
  padding: 4px 10px;
  background: rgba(0, 0, 0, 0.04);
  border-radius: 8px;
}

.status-tag.available {
  background: linear-gradient(135deg, rgba(14, 165, 233, 0.1), rgba(6, 182, 212, 0.1));
  color: #0ea5e9;
}

.use-btn {
  padding: 10px 20px;
  border: none;
  border-radius: 14px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s;
  background: linear-gradient(135deg, #0ea5e9, #06b6d4);
  color: white;
}

.use-btn:hover:not(:disabled) {
  transform: scale(1.05);
}

/* ─── Logs List ─── */
.logs-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.log-item {
  display: flex;
  align-items: center;
  padding: 18px 20px;
  gap: 16px;
}

.log-icon {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  font-weight: 600;
  background: rgba(239, 68, 68, 0.1);
  color: #ef4444;
}

.log-icon.income {
  background: rgba(14, 165, 233, 0.1);
  color: #0ea5e9;
}

.log-info {
  flex: 1;
}

.log-title {
  font-size: 15px;
  font-weight: 600;
  color: #334155;
  margin-bottom: 2px;
}

.log-desc {
  font-size: 13px;
  color: #94a3b8;
}

.log-amount {
  font-size: 20px;
  font-weight: 700;
  color: #ef4444;
}

.log-amount.positive {
  color: #0ea5e9;
}

/* ─── Loading & Empty States ─── */
.loading-card,
.empty-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  gap: 16px;
  color: #94a3b8;
  font-size: 15px;
}

.loading-spinner {
  width: 32px;
  height: 32px;
  border: 3px solid rgba(14, 165, 233, 0.2);
  border-top-color: #0ea5e9;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.empty-icon {
  font-size: 48px;
  opacity: 0.6;
}

/* ─── Responsive ─── */
@media (max-width: 900px) {
  .main-layout {
    grid-template-columns: 1fr;
  }

  .left-panel {
    position: static;
    display: grid;
    grid-template-columns: repeat(2, 1fr);
  }

  .user-card {
    grid-column: 1 / -1;
  }
}

@media (max-width: 600px) {
  .content-wrapper {
    padding: 20px 16px;
  }

  .page-title {
    font-size: 28px;
  }

  .left-panel {
    grid-template-columns: 1fr;
  }

  .coupon-grid {
    grid-template-columns: 1fr;
  }

  .tabs-nav {
    flex-direction: column;
  }

  .tab-item {
    justify-content: flex-start;
    padding: 12px 16px;
  }

  .calendar-grid {
    gap: 4px;
  }

  .calendar-day {
    font-size: 11px;
  }
}
</style>
