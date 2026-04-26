<script setup lang="ts">
import { computed } from "vue";

const props = defineProps<{
  status: "draft" | "pending_review" | "verified" | "archived";
}>();

const label = computed(() => {
  return {
    draft: "草稿",
    pending_review: "待校对",
    verified: "已校对",
    archived: "已归档"
  }[props.status];
});
</script>

<template>
  <span class="status-pill" :class="status">{{ label }}</span>
</template>
