# 腾讯云 Lighthouse 生产部署说明

## 目标

这套生产部署文件用于将 HerbScript 部署到一台带公网 IP 的腾讯云 `Lighthouse` 或 `CVM` Linux 服务器上，并且：

- 不影响本地开发环境
- 通过 `Docker + Docker Compose` 单机部署
- 前端通过 `Nginx` 对外提供访问
- 后端与 MySQL 仅在容器内部互通，不直接暴露公网

## 推荐服务器配置

- 架构：`x86`
- 系统：`Ubuntu 22.04 LTS`
- 最低可用：`2核2G`
- 更稳妥：`2核4G`
- 系统盘：`50GB+`
- 公网 IP：已分配

## 安全组建议

仅开放以下端口：

- `22`：SSH 登录
- `80`：HTTP 访问
- `443`：未来接 HTTPS 时使用

不建议开放：

- `3306`
- `8081`

## 生产文件说明

- `docker-compose.prod.yml`
  用于生产环境启动 `mysql / backend / frontend`

- `frontend/Dockerfile.prod`
  生产前端镜像，先构建静态资源，再用 `Nginx` 提供访问

- `deploy/nginx/frontend.prod.conf`
  前端站点 Nginx 配置，同时反向代理：
  - `/api/*`
  - `/uploads/*`

- `.env.prod.example`
  生产环境变量模板

## 首次部署步骤

### 1. 安装 Docker

在服务器上安装：

- `docker`
- `docker compose`

### 2. 上传项目代码

将整个项目目录上传到服务器，例如：

```bash
/opt/herbscript
```

### 3. 准备生产环境变量

在服务器项目根目录执行：

```bash
cp .env.prod.example .env.prod
```

然后编辑 `.env.prod`，至少要填写：

- `MYSQL_ROOT_PASSWORD`
- `MYSQL_PASSWORD`
- `DOUBAO_API_KEY`

如果希望前端和后端同域访问，`VITE_API_BASE_URL` 保持空即可。

### 4. 启动生产服务

在项目根目录执行：

```bash
docker compose --env-file .env.prod -f docker-compose.prod.yml up -d --build
```

### 5. 验证服务

启动后可先检查：

```bash
docker compose --env-file .env.prod -f docker-compose.prod.yml ps
```

查看日志：

```bash
docker compose --env-file .env.prod -f docker-compose.prod.yml logs -f backend
docker compose --env-file .env.prod -f docker-compose.prod.yml logs -f frontend
```

浏览器访问：

```text
http://你的公网IP
```

## 持久化目录

当前生产编排使用以下持久化：

- MySQL 数据：
  - Docker volume：`herbscript-prod-mysql-data`

- 上传文件：
  - 宿主机目录：`./storage/uploads`

建议你在正式服务器上定期备份：

- `storage/uploads`
- MySQL 数据卷

## 更新部署

代码更新后，在服务器项目根目录执行：

```bash
git pull
docker compose --env-file .env.prod -f docker-compose.prod.yml up -d --build
```

## HTTPS 建议

当前生产文件默认只开放 `80`。

后续如果接域名，建议：

- 域名解析到公网 IP
- 用 `Nginx + Let's Encrypt`
- 或接腾讯云现成证书/负载均衡

## 当前部署结构

```text
公网IP:80
   ->
frontend (Nginx)
   -> /            前端静态资源
   -> /api/*       backend:8081
   -> /uploads/*   backend:8081

backend
   -> mysql:3306
```

## 开发环境不受影响

这套生产文件不会替换你当前的开发方式。

本地开发仍然使用：

```bash
docker compose up
cd frontend && npm run dev
```

生产环境才使用：

```bash
docker compose --env-file .env.prod -f docker-compose.prod.yml up -d --build
```
