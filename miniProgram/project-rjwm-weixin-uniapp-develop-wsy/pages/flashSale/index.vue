<template>
  <view class="flash-sale-page">
    <uni-nav-bar
      @clickLeft="goBack"
      left-icon="back"
      leftIcon="arrowleft"
      title="限量套餐活动"
      statusBar="true"
      fixed="true"
      color="#ffffff"
      backgroundColor="#ff6b35"
    ></uni-nav-bar>

    <scroll-view scroll-y class="page-scroll" :style="{ height: scrollH + 'px' }">
      <view class="section">
        <view class="section-title">当前可抢活动</view>
        <view v-if="activityList.length > 0">
          <view class="activity-card" v-for="item in activityList" :key="item.id">
            <image class="activity-image" :src="item.setmealImage" mode="aspectFill"></image>
            <view class="activity-main">
              <view class="activity-name">{{ item.activityName }}</view>
              <view class="setmeal-name">{{ item.setmealName }}</view>
              <view class="activity-meta">库存：{{ item.stock }}</view>
              <view class="activity-meta">时间：{{ item.startTime }} - {{ item.endTime }}</view>
              <view class="activity-bottom">
                <view class="price-wrap">
                  <text class="price-symbol">￥</text>
                  <text class="price">{{ toPrice(item.setmealPrice) }}</text>
                </view>
                <button
                  class="seize-btn"
                  size="mini"
                  :disabled="!canSeize(item) || loadingActivityId === item.id"
                  @click="handleSeize(item)"
                >
                  {{ loadingActivityId === item.id ? "抢购中" : buttonText(item) }}
                </button>
              </view>
            </view>
          </view>
        </view>
        <view v-else class="empty-card">当前暂无可抢活动</view>
      </view>

      <view class="section">
        <view class="section-title">我的抢购记录</view>
        <view v-if="orderList.length > 0">
          <view class="order-card" v-for="item in orderList" :key="item.id">
            <view class="order-top">
              <text class="order-name">{{ item.activityName }}</text>
              <text class="order-status">{{ orderStatusText(item.status) }}</text>
            </view>
            <view class="order-line">{{ item.setmealName }}</view>
            <view class="order-line">订单号：{{ item.orderNo }}</view>
            <view class="order-line">抢购时间：{{ item.createTime }}</view>
          </view>
        </view>
        <view v-else class="empty-card">你还没有抢购记录</view>
      </view>
    </scroll-view>
  </view>
</template>

<script>
import {
  getFlashSaleActivityList,
  getFlashSaleOrders,
  seizeFlashSaleActivity,
} from "../api/api.js";

export default {
  data() {
    return {
      activityList: [],
      orderList: [],
      loadingActivityId: null,
      scrollH: 0,
    };
  },
  onShow() {
    this.loadAll();
  },
  onReady() {
    uni.getSystemInfo({
      success: (res) => {
        this.scrollH = res.windowHeight - uni.upx2px(88);
      },
    });
  },
  onPullDownRefresh() {
    this.loadAll(true);
  },
  methods: {
    async loadAll(stopRefresh = false) {
      try {
        const [activityRes, orderRes] = await Promise.all([
          getFlashSaleActivityList(),
          getFlashSaleOrders(),
        ]);
        if (activityRes.code === 1) {
          this.activityList = activityRes.data || [];
        }
        if (orderRes.code === 1) {
          this.orderList = orderRes.data || [];
        }
      } catch (e) {
        uni.showToast({
          title: (e && (e.msg || (e.data && e.data.msg))) || "加载活动失败",
          icon: "none",
        });
      } finally {
        if (stopRefresh) {
          uni.stopPullDownRefresh();
        }
      }
    },
    async handleSeize(item) {
      if (!this.canSeize(item)) {
        uni.showToast({
          title: this.buttonText(item),
          icon: "none",
        });
        return;
      }
      this.loadingActivityId = item.id;
      try {
        const res = await seizeFlashSaleActivity(item.id);
        if (res.code === 1) {
          uni.showToast({
            title: "抢购成功",
            icon: "success",
          });
          await this.loadAll();
        }
      } catch (e) {
        uni.showToast({
          title: (e && (e.msg || (e.data && e.data.msg))) || "抢购失败",
          icon: "none",
        });
      } finally {
        this.loadingActivityId = null;
      }
    },
    canSeize(item) {
      const now = Date.now();
      const start = new Date((item.startTime || "").replace(/-/g, "/")).getTime();
      const end = new Date((item.endTime || "").replace(/-/g, "/")).getTime();
      return item.status === 1 && item.stock > 0 && now >= start && now <= end;
    },
    buttonText(item) {
      const now = Date.now();
      const start = new Date((item.startTime || "").replace(/-/g, "/")).getTime();
      const end = new Date((item.endTime || "").replace(/-/g, "/")).getTime();
      if (item.status !== 1) return "已下架";
      if (item.stock <= 0) return "已抢完";
      if (now < start) return "未开始";
      if (now > end) return "已结束";
      return "立即抢购";
    },
    orderStatusText(status) {
      return status === 1 ? "已抢购" : "处理中";
    },
    toPrice(price) {
      const num = Number(price || 0);
      return num.toFixed(2);
    },
    goBack() {
      uni.navigateBack({
        fail: () => {
          uni.redirectTo({
            url: "/pages/my/my",
          });
        },
      });
    },
  },
};
</script>

<style lang="scss" scoped>
.flash-sale-page {
  background: #f7f8fa;
  min-height: 100%;
}

.page-scroll {
  box-sizing: border-box;
  padding: 24rpx;
  margin-top: 88rpx;
}

.section {
  margin-bottom: 24rpx;
}

.section-title {
  font-size: 32rpx;
  font-weight: 600;
  color: #222;
  margin-bottom: 20rpx;
}

.activity-card,
.order-card,
.empty-card {
  background: #fff;
  border-radius: 24rpx;
  padding: 24rpx;
  box-shadow: 0 8rpx 24rpx rgba(0, 0, 0, 0.05);
  margin-bottom: 20rpx;
}

.activity-card {
  display: flex;
}

.activity-image {
  width: 180rpx;
  height: 180rpx;
  border-radius: 18rpx;
  flex-shrink: 0;
  background: #f2f3f5;
}

.activity-main {
  margin-left: 20rpx;
  flex: 1;
}

.activity-name {
  font-size: 30rpx;
  font-weight: 600;
  color: #222;
}

.setmeal-name {
  margin-top: 8rpx;
  font-size: 26rpx;
  color: #555;
}

.activity-meta,
.order-line {
  margin-top: 10rpx;
  font-size: 24rpx;
  color: #666;
}

.activity-bottom {
  margin-top: 18rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.price-wrap {
  color: #ff6b35;
  display: flex;
  align-items: baseline;
}

.price-symbol {
  font-size: 24rpx;
}

.price {
  font-size: 36rpx;
  font-weight: 700;
}

.seize-btn {
  margin: 0;
  padding: 0 28rpx;
  height: 64rpx;
  line-height: 64rpx;
  border-radius: 32rpx;
  border: none;
  color: #fff;
  background: linear-gradient(135deg, #ff7a45, #ff4d4f);
}

.seize-btn[disabled] {
  background: #d9d9d9;
  color: #fff;
}

.order-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.order-name {
  font-size: 28rpx;
  font-weight: 600;
  color: #222;
}

.order-status {
  font-size: 24rpx;
  color: #ff6b35;
}

.empty-card {
  text-align: center;
  color: #888;
  font-size: 26rpx;
}
</style>
