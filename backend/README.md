# HerbScript Backend

这是 HerbScript 的 Spring Boot 后端骨架，当前已切到 `MySQL + JdbcTemplate` 的第一版真实查询：

- `POST /api/auth/login`
- `GET /api/auth/me`
- `POST /api/auth/logout`
- `GET /api/dashboard/summary`
- `GET /api/prescriptions`
- `GET /api/prescriptions/{id}`
- `GET /api/recognitions/draft`
- `GET /api/recognitions/{taskId}`
- `POST /api/recognitions/upload`
- `POST /api/recognitions/{taskId}/confirm`

## 运行要求

- JDK `17+`
- Maven `3.9+`
- 或者使用 Docker

## 启动方式

```bash
cd backend
mvn spring-boot:run
```

如果本机未安装 Java / Maven，可以直接使用 Docker Compose：

```bash
docker compose up --build
```

启动后默认服务：

- MySQL: `localhost:3306`
- Backend API: `http://localhost:8081`

数据库初始化脚本位于：

```bash
docker/mysql/init/001_init_schema.sql
```

如果 `docker compose build` 失败且日志里出现镜像源 `EOF`，说明当前 Docker daemon 的镜像加速源不可用，需要在 Docker Desktop 中调整 registry mirror 后重试。

## 当前说明

- 默认识别模型配置为 `doubao-seed-2-0-pro`
- 已提供 `RecognitionProvider` 抽象和 `DoubaoVisionRecognitionProvider` 骨架
- 若未配置 `DOUBAO_API_KEY`，上传识别会返回本地 mock 草稿，便于先跑通业务闭环
- 若已配置 `DOUBAO_API_KEY`，Provider 会按 OpenAI 兼容的 `chat/completions` 方式发送图文消息
- 默认图片上传大小限制为 `10MB`
- 当前已接入数据库查询
- 当前还未接真实鉴权
- 当前还未接真实 Doubao API
- 当前 `docker-compose.yml` 已预置 MySQL 和 backend 容器
- 已允许前端开发服务器 `http://localhost:5174` 跨域访问 `/api/**`

样例数据策略：

- 首次建库时由 `docker/mysql/init/001_init_schema.sql` 建表
- 应用启动时如果发现 `prescription` 表为空，会自动插入一组演示数据

下一步建议：

1. 引入 `Spring Security + JWT`
2. 将 `JdbcTemplate` 查询继续演进为 repository/service 分层
3. 增加真实登录鉴权
4. 增加 `DoubaoVisionRecognitionProvider`
