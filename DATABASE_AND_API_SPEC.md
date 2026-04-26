# HerbScript 数据库与接口设计说明

## 1. 文档目的

本文档基于 `PRD.md` 与 `TECHNICAL_PLAN.md`，进一步细化 HerbScript 的数据库设计与接口规范，用于指导后端开发、前端联调与系统答辩材料准备。

本文档目标：

- 明确核心实体及其关系
- 输出可直接建表的字段建议
- 定义关键接口输入输出结构
- 为前后端协作提供统一约定

## 2. 核心实体关系

系统核心实体包括：

- 用户 `User`
- 角色 `Role`
- 处方主表 `Prescription`
- 处方药味明细 `PrescriptionItem`
- 识别任务 `RecognitionTask`
- 识别字段记录 `RecognitionField`
- 药材字典 `HerbDictionary`
- 操作日志 `OperationLog`

实体关系说明：

- 一个用户可以拥有多个角色
- 一个处方包含多条药味明细
- 一个识别任务可生成一份识别草稿
- 一个识别任务包含多条字段识别记录
- 一个处方可以关联一个来源识别任务
- 一个药味可关联到一个标准药材字典项

## 3. 数据库设计

### 3.1 命名规范

- 表名统一使用小写下划线命名
- 主键统一为 `bigint`
- 时间字段统一使用：
  - `created_at`
  - `updated_at`
- 逻辑删除字段统一使用 `deleted`
- 状态字段尽量使用英文枚举值

### 3.2 枚举设计

#### 处方状态 `prescription.status`

- `draft`
- `pending_review`
- `verified`
- `archived`

#### 录入方式 `prescription.entry_mode`

- `manual`
- `ai_recognition`

#### 药味确认状态 `prescription_item.confirm_status`

- `pending`
- `confirmed`
- `corrected`

#### 识别任务状态 `recognition_task.status`

- `created`
- `processing`
- `success`
- `failed`

#### 用户状态 `sys_user.status`

- `enabled`
- `disabled`

### 3.3 表结构设计

#### 3.3.1 用户表 `sys_user`

```sql
CREATE TABLE sys_user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(64) NOT NULL UNIQUE,
  password VARCHAR(255) NOT NULL,
  real_name VARCHAR(64) NOT NULL,
  phone VARCHAR(32) NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'enabled',
  last_login_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0
);
```

#### 3.3.2 角色表 `sys_role`

```sql
CREATE TABLE sys_role (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  role_code VARCHAR(64) NOT NULL UNIQUE,
  role_name VARCHAR(64) NOT NULL,
  remark VARCHAR(255) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

#### 3.3.3 用户角色关联表 `sys_user_role`

```sql
CREATE TABLE sys_user_role (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_role (user_id, role_id)
);
```

#### 3.3.4 处方主表 `prescription`

```sql
CREATE TABLE prescription (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  prescription_no VARCHAR(64) NOT NULL,
  hospital_name VARCHAR(128) NULL,
  prescription_type VARCHAR(64) NULL,
  patient_name VARCHAR(64) NOT NULL,
  gender VARCHAR(16) NOT NULL,
  age INT NOT NULL,
  department VARCHAR(64) NULL,
  visit_no VARCHAR(64) NULL,
  bed_no VARCHAR(32) NULL,
  diagnosis VARCHAR(255) NULL,
  dose_count INT NOT NULL,
  prescription_date DATE NOT NULL,
  payment_type VARCHAR(64) NULL,
  doctor_name VARCHAR(64) NULL,
  usage_method VARCHAR(255) NULL,
  remark VARCHAR(255) NULL,
  entry_mode VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL,
  source_image_url VARCHAR(255) NULL,
  source_task_id BIGINT NULL,
  raw_recognition_text TEXT NULL,
  verified_by BIGINT NULL,
  verified_at DATETIME NULL,
  created_by BIGINT NOT NULL,
  updated_by BIGINT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  KEY idx_prescription_no (prescription_no),
  KEY idx_patient_name (patient_name),
  KEY idx_prescription_date (prescription_date),
  KEY idx_status (status),
  KEY idx_created_at (created_at)
);
```

#### 3.3.5 处方药味明细表 `prescription_item`

```sql
CREATE TABLE prescription_item (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  prescription_id BIGINT NOT NULL,
  sort_no INT NOT NULL,
  herb_code VARCHAR(64) NULL,
  herb_name VARCHAR(64) NOT NULL,
  raw_herb_name VARCHAR(64) NULL,
  dosage DECIMAL(10,2) NOT NULL,
  unit VARCHAR(16) NOT NULL,
  special_instruction VARCHAR(128) NULL,
  confidence DECIMAL(5,4) NULL,
  confirm_status VARCHAR(32) NOT NULL DEFAULT 'confirmed',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_prescription_id (prescription_id),
  KEY idx_herb_name (herb_name)
);
```

#### 3.3.6 识别任务表 `recognition_task`

```sql
CREATE TABLE recognition_task (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  image_url VARCHAR(255) NOT NULL,
  provider_name VARCHAR(64) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'created',
  raw_text TEXT NULL,
  raw_json LONGTEXT NULL,
  parsed_json LONGTEXT NULL,
  warning_message VARCHAR(500) NULL,
  error_message VARCHAR(500) NULL,
  started_at DATETIME NULL,
  finished_at DATETIME NULL,
  created_by BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_status (status),
  KEY idx_created_by (created_by)
);
```

#### 3.3.7 识别字段记录表 `recognition_field`

```sql
CREATE TABLE recognition_field (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_id BIGINT NOT NULL,
  field_key VARCHAR(64) NOT NULL,
  field_label VARCHAR(64) NOT NULL,
  raw_value VARCHAR(255) NULL,
  corrected_value VARCHAR(255) NULL,
  confidence DECIMAL(5,4) NULL,
  confirm_status VARCHAR(32) NOT NULL DEFAULT 'pending',
  confirmed_by BIGINT NULL,
  confirmed_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_task_id (task_id),
  KEY idx_field_key (field_key)
);
```

#### 3.3.8 药材字典表 `herb_dictionary`

```sql
CREATE TABLE herb_dictionary (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  herb_code VARCHAR(64) NOT NULL UNIQUE,
  herb_name VARCHAR(64) NOT NULL,
  alias_name VARCHAR(255) NULL,
  pinyin VARCHAR(128) NULL,
  default_unit VARCHAR(16) NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'enabled',
  remark VARCHAR(255) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  KEY idx_herb_name (herb_name),
  KEY idx_pinyin (pinyin)
);
```

#### 3.3.9 操作日志表 `operation_log`

```sql
CREATE TABLE operation_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  operator_id BIGINT NOT NULL,
  operator_name VARCHAR(64) NOT NULL,
  module_name VARCHAR(64) NOT NULL,
  operation_type VARCHAR(64) NOT NULL,
  target_type VARCHAR(64) NOT NULL,
  target_id BIGINT NULL,
  detail TEXT NULL,
  ip_address VARCHAR(64) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_operator_id (operator_id),
  KEY idx_module_name (module_name),
  KEY idx_operation_type (operation_type),
  KEY idx_created_at (created_at)
);
```

## 4. 字段设计补充建议

### 4.1 关于 `prescription_no`

- 如果上传识别时无法从图片中准确识别处方号，可以临时生成系统编号
- 建议编号格式：
  - `HS202604120001`

### 4.2 关于 `raw_json` 和 `parsed_json`

- `raw_json` 保存 `doubao-seed-2-0-pro` 原始返回结果
- `parsed_json` 保存适配器解析后的统一结构
- 这样便于后续排查识别问题

### 4.3 关于药味剂量字段

- `dosage` 建议使用 `DECIMAL(10,2)`
- 单位单独存储到 `unit`
- 避免把 `10g` 存成混合字符串，不利于统计

## 5. 索引与查询设计

### 5.1 处方列表高频查询

建议对以下字段建立索引：

- `prescription_no`
- `patient_name`
- `prescription_date`
- `status`
- `created_at`

### 5.2 药材搜索高频查询

建议对以下字段建立索引：

- `herb_name`
- `pinyin`

### 5.3 日志查询高频条件

- `operator_id`
- `module_name`
- `created_at`

## 6. 后端接口规范

### 6.1 通用响应结构

建议统一响应格式：

```json
{
  "code": 0,
  "message": "success",
  "data": {}
}
```

分页返回建议：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "list": [],
    "pageNum": 1,
    "pageSize": 10,
    "total": 100
  }
}
```

### 6.2 认证接口

#### `POST /api/auth/login`

请求：

```json
{
  "username": "admin",
  "password": "123456"
}
```

响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "token": "jwt-token",
    "user": {
      "id": 1,
      "username": "admin",
      "realName": "系统管理员",
      "roles": ["ADMIN"]
    }
  }
}
```

#### `GET /api/auth/me`

返回当前登录用户信息及角色。

#### `POST /api/auth/logout`

用于前端退出登录，若使用 JWT 可只做客户端登出。

## 7. 处方接口设计

### 7.1 处方列表查询

#### `GET /api/prescriptions`

查询参数：

- `pageNum`
- `pageSize`
- `prescriptionNo`
- `patientName`
- `gender`
- `dateStart`
- `dateEnd`
- `entryMode`
- `status`
- `doctorName`

返回示例：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "list": [
      {
        "id": 1,
        "prescriptionNo": "HS202604120001",
        "patientName": "张三",
        "gender": "男",
        "age": 45,
        "prescriptionDate": "2026-04-12",
        "doseCount": 7,
        "entryMode": "ai_recognition",
        "status": "pending_review",
        "createdByName": "录入员A",
        "createdAt": "2026-04-12 10:00:00"
      }
    ],
    "pageNum": 1,
    "pageSize": 10,
    "total": 1
  }
}
```

### 7.2 新增处方

#### `POST /api/prescriptions`

请求体：

```json
{
  "hospitalName": "某中医院",
  "prescriptionType": "中药饮片",
  "patientName": "张三",
  "gender": "男",
  "age": 45,
  "department": "内科",
  "visitNo": "MZ001",
  "bedNo": "",
  "diagnosis": "脾胃虚弱",
  "doseCount": 7,
  "prescriptionDate": "2026-04-12",
  "paymentType": "自费",
  "doctorName": "李医生",
  "usageMethod": "水煎服",
  "remark": "",
  "entryMode": "manual",
  "status": "verified",
  "items": [
    {
      "sortNo": 1,
      "herbCode": "HB001",
      "herbName": "黄芪",
      "rawHerbName": "黄芪",
      "dosage": 15,
      "unit": "g",
      "specialInstruction": "",
      "confirmStatus": "confirmed"
    }
  ]
}
```

返回：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": 1001
  }
}
```

### 7.3 处方详情

#### `GET /api/prescriptions/{id}`

返回内容建议包含：

- 主表信息
- 药味明细
- 来源识别任务概要
- 操作日志

### 7.4 更新处方

#### `PUT /api/prescriptions/{id}`

请求体与新增处方结构相同。

更新时要求：

- 全量更新主表基础字段
- 采用“先删后插”或差异更新的方式处理药味明细
- 写入变更日志

### 7.5 删除处方

#### `DELETE /api/prescriptions/{id}`

建议逻辑删除，同时记录删除人和删除日志。

### 7.6 修改处方状态

#### `PUT /api/prescriptions/{id}/status`

请求：

```json
{
  "status": "archived"
}
```

## 8. 识别接口设计

### 8.1 上传并识别

#### `POST /api/recognitions/upload`

请求方式：

- `multipart/form-data`

字段：

- `file`

返回：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "taskId": 2001,
    "status": "success",
    "imageUrl": "/uploads/prescriptions/20260412/xxx.png",
    "draft": {
      "hospitalName": "某中医院",
      "patientName": "张三",
      "gender": "男",
      "age": 45,
      "diagnosis": "脾胃虚弱",
      "doseCount": 7,
      "prescriptionDate": "2026-04-12",
      "doctorName": "李医生",
      "usageMethod": "水煎服",
      "items": [
        {
          "sortNo": 1,
          "rawHerbName": "黄芪",
          "mappedHerbName": "黄芪",
          "dosage": 15,
          "unit": "g",
          "confidence": 0.98,
          "confirmStatus": "pending"
        }
      ],
      "lowConfidenceFields": ["diagnosis"]
    }
  }
}
```

说明：

- 后端接收图片后直接调用 `doubao-seed-2-0-pro`
- 不单独经过 OCR 中间层
- 模型需按固定 JSON 格式返回结构化草稿
- 服务端负责字段纠偏、默认值兜底和原始响应留存

### 8.2 获取识别任务详情

#### `GET /api/recognitions/{taskId}`

返回：

- 图片地址
- 原始文本
- 原始结构化结果
- 字段级置信度
- 药味明细

### 8.3 确认识别草稿

#### `POST /api/recognitions/{taskId}/confirm`

作用：

- 接收人工校对后的正式数据
- 创建正式处方记录
- 将识别任务转为已完成状态

请求体建议与 `POST /api/prescriptions` 保持一致，但额外带上：

- `taskId`

## 9. 药材字典接口设计

### 9.1 药材分页查询

#### `GET /api/herbs`

查询参数：

- `pageNum`
- `pageSize`
- `keyword`
- `status`

### 9.2 新增药材

#### `POST /api/herbs`

```json
{
  "herbCode": "HB001",
  "herbName": "黄芪",
  "aliasName": "北芪",
  "pinyin": "huangqi",
  "defaultUnit": "g",
  "status": "enabled",
  "remark": ""
}
```

### 9.3 编辑药材

#### `PUT /api/herbs/{id}`

### 9.4 自动补全搜索

#### `GET /api/herbs/search?keyword=huang`

返回建议精简为：

```json
[
  {
    "id": 1,
    "herbCode": "HB001",
    "herbName": "黄芪",
    "aliasName": "北芪",
    "defaultUnit": "g"
  }
]
```

## 10. 用户管理接口设计

### 10.1 用户列表

#### `GET /api/users`

### 10.2 新增用户

#### `POST /api/users`

```json
{
  "username": "editor1",
  "realName": "录入员甲",
  "password": "123456",
  "status": "enabled",
  "roleCodes": ["EDITOR"]
}
```

### 10.3 编辑用户

#### `PUT /api/users/{id}`

### 10.4 启停用用户

#### `PUT /api/users/{id}/status`

### 10.5 重置密码

#### `PUT /api/users/{id}/reset-password`

## 11. 首页统计接口设计

### 11.1 概览统计

#### `GET /api/dashboard/summary`

返回建议：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "todayNewCount": 12,
    "pendingReviewCount": 5,
    "verifiedCount": 120,
    "recentPrescriptions": [
      {
        "id": 1,
        "prescriptionNo": "HS202604120001",
        "patientName": "张三",
        "status": "verified",
        "createdAt": "2026-04-12 10:00:00"
      }
    ]
  }
}
```

## 12. 操作日志接口设计

### 12.1 日志分页查询

#### `GET /api/logs`

查询参数：

- `pageNum`
- `pageSize`
- `moduleName`
- `operationType`
- `operatorName`
- `dateStart`
- `dateEnd`

## 13. 前后端 DTO 建议

### 13.1 PrescriptionSaveRequest

```json
{
  "hospitalName": "",
  "prescriptionType": "",
  "patientName": "",
  "gender": "",
  "age": 0,
  "department": "",
  "visitNo": "",
  "bedNo": "",
  "diagnosis": "",
  "doseCount": 0,
  "prescriptionDate": "",
  "paymentType": "",
  "doctorName": "",
  "usageMethod": "",
  "remark": "",
  "entryMode": "",
  "status": "",
  "sourceImageUrl": "",
  "items": []
}
```

### 13.2 PrescriptionItemRequest

```json
{
  "sortNo": 1,
  "herbCode": "",
  "herbName": "",
  "rawHerbName": "",
  "dosage": 0,
  "unit": "g",
  "specialInstruction": "",
  "confidence": 1.0,
  "confirmStatus": "confirmed"
}
```

### 13.3 RecognitionDraftResponse

```json
{
  "taskId": 2001,
  "providerName": "doubao-seed-2-0-pro",
  "imageUrl": "/uploads/prescriptions/20260421/1.png",
  "rawResponse": "...",
  "warnings": ["服用说明识别置信度较低"],
  "draft": {
    "patientName": "王秀兰",
    "gender": "女",
    "age": 58,
    "diagnosis": "脾胃虚弱",
    "doseCount": 7,
    "prescriptionDate": "2026-04-21",
    "doctorName": "李医生",
    "usageMethod": "水煎服",
    "items": [
      {
        "sortNo": 1,
        "rawHerbName": "黄芪",
        "mappedHerbName": "黄芪",
        "dosage": 15,
        "unit": "g",
        "confidence": 0.98
      }
    ]
  }
}
```

## 14. MVP 最终建议

如果当前要立即进入开发，最先建议落地的是以下最小集合：

### 后端先做

- 登录接口
- 处方列表接口
- 处方新增接口
- 处方详情接口
- 图片上传识别接口
- 草稿确认接口

### 前端先做

- 登录页
- 主布局
- 处方列表页
- 处方新增页
- 图片识别校对页
- 处方详情页

### 数据库先建

- `sys_user`
- `sys_role`
- `sys_user_role`
- `prescription`
- `prescription_item`
- `recognition_task`
- `operation_log`

其中 `recognition_field` 和 `herb_dictionary` 可以稍后补充，但如果时间允许，建议第一轮就建好，后续扩展更顺。
