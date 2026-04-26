<script setup lang="ts">
import { computed } from "vue";

const props = defineProps<{
  value: number;
  compact?: boolean;
}>();

const formatted = computed(() => `${Math.round(props.value * 100)}%`);
const level = computed(() => {
  if (props.value >= 0.9) return "high";
  if (props.value >= 0.75) return "medium";
  return "low";
});
</script>

<template>
  <span class="confidence-badge" :class="[level, { compact: props.compact }]">
    <template v-if="props.compact">
      <span class="confidence-dot" />
    </template>
    <template v-else>
      {{ formatted }}
    </template>
  </span>
</template>
