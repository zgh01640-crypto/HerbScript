<script setup lang="ts">
defineProps<{
  label: string;
  value: string;
  trend: string;
}>();
</script>

<template>
  <article class="stat-card">
    <div class="stat-label">{{ label }}</div>
    <div class="stat-value">{{ value }}</div>
    <div class="stat-trend">{{ trend }}</div>
  </article>
</template>
