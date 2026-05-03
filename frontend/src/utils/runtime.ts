export const resolveApiBaseUrl = () => {
  const envBaseUrl = import.meta.env.VITE_API_BASE_URL;
  if (envBaseUrl && envBaseUrl.trim()) {
    return envBaseUrl;
  }

  if (typeof window !== "undefined" && window.location?.origin) {
    return window.location.origin;
  }

  return "http://localhost:8081";
};
