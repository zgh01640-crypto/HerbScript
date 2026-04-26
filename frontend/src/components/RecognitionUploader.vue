<script setup lang="ts">
const emit = defineEmits<{
  uploaded: [file: File];
}>();

const handleChange = (event: Event) => {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  if (!file) {
    return;
  }
  emit("uploaded", file);
  input.value = "";
};
</script>

<template>
  <label class="upload-dropzone">
    <input class="upload-input" type="file" accept=".png,.jpg,.jpeg" @change="handleChange" />
    <div class="upload-dropzone-head">
      <strong>上传新处方图片</strong>
      <span class="upload-dropzone-action">点击选择图片</span>
    </div>
    <span>支持 JPG / PNG，上传后会立即生成新的识别草稿并切换到当前工作台</span>
  </label>
</template>
