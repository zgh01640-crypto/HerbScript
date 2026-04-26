import { http } from "./http";
import type { CurrentUser, LoginRequest, LoginResponse } from "../types/auth";

type ApiResponse<T> = {
  code: number;
  message: string;
  data: T;
};

const TOKEN_KEY = "herbscript_token";
const USER_KEY = "herbscript_user";

export const authService = {
  async login(payload: LoginRequest): Promise<LoginResponse> {
    const response = await http.post<ApiResponse<LoginResponse>>("/api/auth/login", payload);
    localStorage.setItem(TOKEN_KEY, response.data.token);
    localStorage.setItem(USER_KEY, JSON.stringify(response.data.user));
    return response.data;
  },

  async fetchMe(): Promise<CurrentUser> {
    const response = await http.get<ApiResponse<CurrentUser>>("/api/auth/me");
    localStorage.setItem(USER_KEY, JSON.stringify(response.data));
    return response.data;
  },

  getStoredUser(): CurrentUser | null {
    const raw = localStorage.getItem(USER_KEY);
    return raw ? (JSON.parse(raw) as CurrentUser) : null;
  },

  getToken(): string {
    return localStorage.getItem(TOKEN_KEY) ?? "";
  },

  isLoggedIn(): boolean {
    return Boolean(this.getToken());
  },

  logout() {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
  },

  async initializeSession(): Promise<CurrentUser | null> {
    if (!this.isLoggedIn()) {
      return null;
    }

    const storedUser = this.getStoredUser();
    if (storedUser) {
      return storedUser;
    }

    try {
      return await this.fetchMe();
    } catch {
      this.logout();
      return null;
    }
  }
};
