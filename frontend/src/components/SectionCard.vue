<script setup lang="ts">
defineProps<{
  title: string;
  subtitle?: string;
  inlineSubtitle?: boolean;
}>();
</script>

<template>
  <section class="section-card">
    <header class="section-head" :class="{ 'inline-subtitle': inlineSubtitle }">
      <div>
        <h3>{{ title }}</h3>
        <p v-if="subtitle">{{ subtitle }}</p>
      </div>
      <slot name="extra" />
    </header>
    <div class="section-body">
      <slot />
    </div>
  </section>
</template>
