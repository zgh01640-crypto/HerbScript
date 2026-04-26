export interface CurrentUser {
  id: number;
  username: string;
  realName: string;
  roles: string[];
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface LoginResponse {
  token: string;
  user: CurrentUser;
}
