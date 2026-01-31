<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps({
  // 图标名称，例如 'icon-home'
  name: {
    type: String,
    required: true
  },
  // 图标颜色
  color: {
    type: String,
    default: ''
  },
  // 图标大小，支持 '20px' 或 20
  size: {
    type: [Number, String],
    default: '1em'
  }
})

const symbolId = computed(() => `#${props.name}`)

const iconStyle = computed(() => {
  const s = typeof props.size === 'number' ? `${props.size}px` : props.size
  return {
    fontSize: s,
    width: s,
    height: s,
    color: props.color
  }
})
</script>

<template>
  <svg class="svg-icon" aria-hidden="true" :style="iconStyle">
    <use :href="symbolId" />
  </svg>
</template>

<style scoped>
.svg-icon {
  vertical-align: -0.15em;
  fill: currentColor;
  overflow: hidden;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  line-height: 1;
}
</style>
