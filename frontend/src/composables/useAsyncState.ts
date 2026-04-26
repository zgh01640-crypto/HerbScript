import { ref } from "vue";

export const useAsyncState = <T>() => {
  const data = ref<T | null>(null);
  const loading = ref(false);
  const error = ref<string>("");

  const run = async (runner: () => Promise<T>) => {
    loading.value = true;
    error.value = "";

    try {
      data.value = await runner();
    } catch (err) {
      error.value = err instanceof Error ? err.message : "请求失败";
    } finally {
      loading.value = false;
    }
  };

  return {
    data,
    loading,
    error,
    run
  };
};
