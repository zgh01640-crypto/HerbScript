const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || "http://localhost:8081";

export const http = {
  async get<T>(path: string, params?: Record<string, string>) {
    const url = new URL(path, API_BASE_URL);

    if (params) {
      Object.entries(params).forEach(([key, value]) => {
        if (value) {
          url.searchParams.set(key, value);
        }
      });
    }

    const response = await fetch(url.toString(), {
      headers: {
        "Content-Type": "application/json",
        ...this.authHeader()
      } as Record<string, string>
    });

    if (!response.ok) {
      throw new Error(`请求失败: ${response.status}`);
    }

    return (await response.json()) as T;
  },

  async post<T>(path: string, body: unknown) {
    const url = new URL(path, API_BASE_URL);
    const response = await fetch(url.toString(), {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        ...this.authHeader()
      } as Record<string, string>,
      body: JSON.stringify(body)
    });

    if (!response.ok) {
      throw new Error(`请求失败: ${response.status}`);
    }

    return (await response.json()) as T;
  },

  async postForm<T>(path: string, formData: FormData) {
    const url = new URL(path, API_BASE_URL);
    const response = await fetch(url.toString(), {
      method: "POST",
      headers: {
        ...this.authHeader()
      },
      body: formData
    });

    if (!response.ok) {
      throw new Error(`请求失败: ${response.status}`);
    }

    return (await response.json()) as T;
  },

  async put<T>(path: string, body: unknown) {
    const url = new URL(path, API_BASE_URL);
    const response = await fetch(url.toString(), {
      method: "PUT",
      headers: {
        "Content-Type": "application/json",
        ...this.authHeader()
      } as Record<string, string>,
      body: JSON.stringify(body)
    });

    if (!response.ok) {
      throw new Error(`请求失败: ${response.status}`);
    }

    return (await response.json()) as T;
  },

  async delete<T>(path: string) {
    const url = new URL(path, API_BASE_URL);
    const response = await fetch(url.toString(), {
      method: "DELETE",
      headers: {
        "Content-Type": "application/json",
        ...this.authHeader()
      } as Record<string, string>
    });

    if (!response.ok) {
      throw new Error(`请求失败: ${response.status}`);
    }

    return (await response.json()) as T;
  },

  authHeader(): Record<string, string> {
    const token = localStorage.getItem("herbscript_token");
    return token ? { Authorization: `Bearer ${token}` } : {};
  }
};
