# HerbScript 技术实现方案

## 1. 目标与原则

本文档将 `PRD.md` 中的产品需求拆解为可落地的技术实现方案，用于指导系统设计、开发排期与毕业设计汇报。

技术方案遵循以下原则：

- 优先满足 MVP 闭环，保证系统可演示、可交付
- 采用前后端分离架构，便于模块解耦与后续扩展
- 将 AI 视觉识别模块与业务模块隔离，便于替换第三方服务
- 保留原始识别结果、人工修正结果与操作日志，确保可追溯
- 控制实现复杂度，选择适合中小型后台系统的技术路线

## 2. 总体技术路线

### 2.1 推荐架构

采用经典三层架构 + 独立 AI 视觉识别适配层：

1. 前端表现层
   负责登录、列表、表单录入、图片上传、识别结果校对、详情展示等交互。
2. 后端业务层
   负责权限认证、处方管理、药材字典、日志记录、文件管理、识别任务编排。
3. 数据存储层
   负责结构化业务数据、原始识别结果、用户信息、日志信息存储。
4. AI 视觉识别适配层
   负责直接对接多模态模型识别服务，将图片理解结果转换为统一结构。

### 2.2 推荐技术栈

为兼顾开发效率、展示效果和实现难度，推荐以下组合：

- 前端：`Vue 3 + TypeScript + Vite + Element Plus`
- 后端：`Spring Boot 3 + Spring Web + Spring Security + MyBatis-Plus`
- 数据库：`MySQL 8`
- 文件存储：本地文件系统，后续可替换为对象存储
- 缓存：MVP 可不强依赖，后续可增加 `Redis`
- AI 识别：先做统一接口封装，默认接入 `doubao-seed-2-0-pro`，后续可扩展其他多模态模型
- 部署：`Nginx + Spring Boot Jar + MySQL`

如果希望降低 Java 开发成本，也可以使用：

- 前端：`Vue 3 + TypeScript + Element Plus`
- 后端：`Node.js + NestJS / Express`
- 数据库：`MySQL 8`

但如果项目面向课程设计、毕业设计答辩，`Spring Boot + Vue` 的表达会更稳定、规范，也更容易展示“系统设计能力”。

## 3. 系统架构设计

### 3.1 架构分层

#### 前端层

前端建议划分为以下页面与能力模块：

- 登录模块
- 首页控制台模块
- 处方列表模块
- 处方详情模块
- 手动新增处方模块
- 图片识别录入模块
- 药材字典模块
- 用户管理模块

前端核心职责：

- 表单录入与校验
- 图片上传与预览
- 原图和识别结果同屏对照
- 药味明细动态编辑
- 列表筛选、分页、排序
- 基于角色的页面按钮权限控制

#### 后端层

后端按业务域拆分为以下模块：

- `auth`：登录、鉴权、用户会话
- `user`：用户管理、角色管理、状态管理
- `prescription`：处方主流程管理
- `recognition`：图片识别任务与结果解析
- `herb`：药材字典管理
- `file`：处方图片上传与访问
- `audit-log`：操作日志记录
- `dashboard`：首页统计数据聚合

#### 存储层

- `MySQL`：结构化业务数据
- `本地 uploads 目录`：原始处方图片
- `数据库 JSON/Text 字段`：模型原始返回内容、原始结构化识别结果

### 3.2 模块边界

系统中最需要解耦的是“识别”和“业务保存”：

- 识别模块只负责接收图片并返回结构化草稿
- 业务模块负责人工校对、保存正式处方、维护状态流转
- 不把识别结果直接当作正式数据落库

这样可以确保：

- AI 识别失败时仍可人工录入
- 后期替换多模态模型服务时不影响业务表结构
- 识别准确率不高时仍可演示完整流程

## 4. 功能模块拆解

### 4.1 登录与权限模块

#### 功能范围

- 用户登录
- 用户退出
- 基于角色的接口权限控制
- 页面菜单权限控制
- 用户启用/停用
- 密码加密存储

#### 技术实现

- 使用 `Spring Security + JWT` 或 `Spring Security + Session`
- 对于毕业设计场景，推荐 `JWT`，更适合前后端分离
- 密码采用 `BCrypt`
- 登录成功后返回：
  - 用户基础信息
  - 角色信息
  - token
  - 可访问菜单或权限标识

#### 角色模型建议

- `ADMIN`
- `EDITOR`
- `DOCTOR`
- `PHARMACIST`

#### 权限控制粒度

- 页面级：是否可见菜单
- 操作级：是否可新增、编辑、删除、校对、管理用户
- 数据级：MVP 可暂不做复杂行级权限

### 4.2 图片上传与识别模块

#### 功能范围

- 上传 JPG/PNG 处方图片
- 保存原始图片
- 调用多模态模型直接识别处方图片
- 返回识别草稿
- 保存原始文本、结构化结果、置信度

#### 处理流程

1. 前端上传图片到后端
2. 后端校验文件类型、大小、分辨率
3. 后端生成文件名并保存原图
4. 后端调用视觉识别适配器
5. 适配器返回统一识别结构
6. 后端将结果保存为“识别草稿”
7. 前端进入人工校对页面

#### 识别模块内部设计

建议定义统一接口：

```java
public interface RecognitionProvider {
    RecognitionDraft recognize(File imageFile);
}
```

不同供应商都实现该接口，例如：

- `MockRecognitionProvider`
- `DoubaoVisionRecognitionProvider`
- `CustomVisionRecognitionProvider`

#### 默认模型方案

当前默认模型定为 `doubao-seed-2-0-pro`，采用“直接输入处方图片，由模型输出结构化草稿”的方式处理，不再单独经过 OCR 中间层。

建议模型输出内容包含：

- 处方基础信息字段
- 药味明细数组
- 字段置信度
- 低置信提示
- 无法识别项说明

建议后端对模型输出做两层处理：

1. 提示词约束模型按固定 JSON 结构返回
2. 服务端再次做字段校验、类型纠正与兜底默认值处理

#### 统一输出结构

- 处方基础字段
- 药味明细列表
- 原始文本
- 字段置信度
- 识别异常信息

### 4.3 人工校对模块

#### 功能范围

- 原图与结构化结果同屏展示
- 逐字段编辑
- 药味动态新增、删除、排序
- 标记字段是否已确认
- 保存人工修正值
- 标记校对完成

#### 技术实现重点

- 前端使用动态表单组件渲染药味列表
- 后端保存以下三类值：
  - 原始识别值
  - 最终确认值
  - 置信度

#### 建议交互设计

- 低置信字段高亮
- 支持“一键确认全部”
- 支持单个药味快速删除
- 支持拖拽调整药味顺序

### 4.4 手动新增处方模块

#### 功能范围

- 完全手工录入处方
- 药味动态维护
- 表单校验
- 保存正式处方

#### 技术实现重点

- 与“识别校对”共用同一份处方编辑表单模型
- 区别只在初始数据来源不同：
  - 手动新增：空表单
  - AI 录入：识别草稿预填充

#### 表单校验

- 患者姓名不能为空
- 性别不能为空
- 年龄需为合法数字
- 处方日期不能为空
- 剂数不能为空
- 药味至少一条
- 单条药味需包含名称和剂量

### 4.5 处方管理模块

#### 功能范围

- 列表查询
- 多条件筛选
- 查看详情
- 编辑
- 删除
- 状态流转
- 导出

#### 技术实现重点

- 使用分页查询接口
- 查询条件通过 DTO 统一接收
- 列表页默认按创建时间倒序
- 删除操作推荐逻辑删除，避免彻底丢失记录

#### 状态流转建议

- 新建手动处方可直接保存为 `verified`
- AI 识别结果先保存为 `pending_review`
- 用户确认后变更为 `verified`
- 后续需要封存时可变更为 `archived`

### 4.6 药材字典模块

#### 功能范围

- 药材字典新增、编辑、启停用
- 支持手动录入时自动补全
- 支持 AI 识别结果标准化映射

#### 技术实现重点

- 维护标准药材名称表
- 提供模糊搜索接口供前端自动补全
- 识别结果可先按“完全匹配 + 别名匹配”映射

#### MVP 实现建议

- 先支持名称、别名、拼音搜索
- 暂不做复杂词典知识图谱

### 4.7 日志与审计模块

#### 功能范围

- 记录登录、上传、识别、保存、编辑、删除、校对等操作
- 支持详情页查看操作记录

#### 技术实现重点

- 采用 AOP 或统一业务日志服务
- 关键操作写入独立审计表
- 编辑处方时记录变更前后摘要

## 5. 数据库设计方案

### 5.1 核心表清单

建议最少包含以下数据表：

- `sys_user`：用户表
- `sys_role`：角色表
- `sys_user_role`：用户角色关联表
- `prescription`：处方主表
- `prescription_item`：处方药味明细表
- `recognition_task`：识别任务表
- `recognition_field`：识别字段明细表
- `herb_dictionary`：药材字典表
- `operation_log`：操作日志表

### 5.2 关键表设计说明

#### `prescription`

用于存储最终业务视角的处方主信息。

关键字段建议：

- `id`
- `prescription_no`
- `hospital_name`
- `prescription_type`
- `patient_name`
- `gender`
- `age`
- `department`
- `visit_no`
- `bed_no`
- `diagnosis`
- `dose_count`
- `prescription_date`
- `payment_type`
- `doctor_name`
- `usage_method`
- `remark`
- `entry_mode`
- `status`
- `source_image_url`
- `raw_recognition_text`
- `created_by`
- `created_at`
- `updated_by`
- `updated_at`
- `deleted`

#### `prescription_item`

用于存储每味药的结构化明细。

关键字段建议：

- `id`
- `prescription_id`
- `sort_no`
- `herb_code`
- `herb_name`
- `raw_herb_name`
- `dosage`
- `unit`
- `special_instruction`
- `confidence`
- `confirm_status`
- `created_at`
- `updated_at`

#### `recognition_task`

用于保存图片识别过程信息，便于追溯和失败重试。

关键字段建议：

- `id`
- `image_url`
- `provider_name`
- `status`
- `raw_text`
- `raw_json`
- `error_message`
- `started_at`
- `finished_at`
- `created_by`

#### `recognition_field`

用于保存字段级识别与人工修正痕迹。

关键字段建议：

- `id`
- `task_id`
- `field_key`
- `field_label`
- `raw_value`
- `corrected_value`
- `confidence`
- `confirm_status`
- `confirmed_by`
- `confirmed_at`

#### `herb_dictionary`

- `id`
- `herb_code`
- `herb_name`
- `alias_name`
- `pinyin`
- `default_unit`
- `status`
- `remark`

#### `operation_log`

- `id`
- `operator_id`
- `operator_name`
- `module_name`
- `operation_type`
- `target_id`
- `target_type`
- `detail`
- `created_at`

### 5.3 数据建模建议

- 处方主表和药味明细使用一对多关系
- 识别任务独立建表，避免污染正式业务表
- 原始识别结果建议同时保存 `raw_text` 和 `raw_json`
- 删除操作采用逻辑删除
- 时间字段统一使用 `datetime`

## 6. 后端接口设计

### 6.1 认证接口

- `POST /api/auth/login`
- `POST /api/auth/logout`
- `GET /api/auth/me`

### 6.2 处方接口

- `GET /api/prescriptions`
  - 分页查询处方列表
- `POST /api/prescriptions`
  - 手动新增处方
- `GET /api/prescriptions/{id}`
  - 获取处方详情
- `PUT /api/prescriptions/{id}`
  - 更新处方
- `DELETE /api/prescriptions/{id}`
  - 删除处方
- `PUT /api/prescriptions/{id}/status`
  - 修改处方状态

### 6.3 图片识别接口

- `POST /api/recognitions/upload`
  - 上传图片并创建识别任务
- `GET /api/recognitions/{taskId}`
  - 获取识别草稿详情
- `POST /api/recognitions/{taskId}/confirm`
  - 将校对后的草稿转正式处方

### 6.4 药材字典接口

- `GET /api/herbs`
- `POST /api/herbs`
- `PUT /api/herbs/{id}`
- `PUT /api/herbs/{id}/status`
- `GET /api/herbs/search`

### 6.5 用户管理接口

- `GET /api/users`
- `POST /api/users`
- `PUT /api/users/{id}`
- `PUT /api/users/{id}/status`
- `PUT /api/users/{id}/reset-password`

### 6.6 日志与统计接口

- `GET /api/logs`
- `GET /api/dashboard/summary`

## 7. 前端页面实现方案

### 7.1 页面结构

建议页面如下：

- `/login`
- `/dashboard`
- `/prescriptions`
- `/prescriptions/new`
- `/prescriptions/recognize`
- `/prescriptions/:id`
- `/prescriptions/:id/edit`
- `/herbs`
- `/users`

### 7.2 页面组件拆分建议

#### 处方相关复用组件

- `PrescriptionForm`
- `PrescriptionItemTable`
- `ImageUploader`
- `RecognitionResultPanel`
- `ConfidenceTag`
- `StatusTag`

#### 列表相关组件

- `SearchFilterBar`
- `PrescriptionTable`
- `PaginationBar`

#### 系统通用组件

- `AppLayout`
- `AppHeader`
- `SideMenu`
- `PermissionButton`

### 7.3 前端状态管理

推荐使用：

- `Pinia` 管理用户信息、token、菜单状态
- 页面级查询条件保存在路由 query 或局部 store

### 7.4 前端表单处理建议

- 使用统一 DTO 与后端字段命名对齐
- 手动新增和识别校对复用同一编辑组件
- 药味编辑支持行内新增、删除、修改

## 8. AI 视觉识别适配方案

### 8.1 设计目标

不要将业务逻辑直接耦合到某一个模型厂商，而是设计统一适配层。

### 8.2 识别流程抽象

1. 上传图片
2. 调用 `doubao-seed-2-0-pro`
3. 获取模型原始响应或原始 JSON
4. 解析成统一字段结构
5. 返回前端进行人工校对

### 8.3 MVP 推荐策略

MVP 阶段建议分两步实现：

#### 第一阶段

- 提供模拟识别数据或固定样例识别
- 先完成前后端流程、页面与数据闭环

#### 第二阶段

- 接入 `doubao-seed-2-0-pro` 真实图片识别
- 增加置信度、模糊提示、药材标准化映射

这样做的好处是：

- 不会被真实模型接入阻塞整体项目
- 能先完成完整业务演示
- 后续替换识别服务成本低

### 8.4 Doubao 接入建议

后端建议封装 `DoubaoVisionRecognitionProvider`，内部职责如下：

- 接收本地图片路径或上传后的文件 URL
- 构造固定提示词，要求模型严格返回 JSON
- 调用 `doubao-seed-2-0-pro`
- 解析 JSON 并映射到 `RecognitionDraft`
- 对字段进行基础校验
- 记录原始响应，便于审计和调试

推荐提示词约束方向：

- 明确输出字段名
- 明确药味数组结构
- 明确剂量必须拆成数值和单位
- 明确无法判断时返回空值并写入 `warnings`
- 禁止输出额外解释性自然语言

### 8.5 识别结果标准结构建议

```json
{
  "basicInfo": {
    "hospitalName": "",
    "prescriptionNo": "",
    "patientName": "",
    "gender": "",
    "age": 0,
    "diagnosis": "",
    "doseCount": 0,
    "prescriptionDate": "",
    "doctorName": "",
    "usageMethod": ""
  },
  "items": [
    {
      "rawHerbName": "",
      "mappedHerbName": "",
      "dosage": "",
      "unit": "g",
      "confidence": 0.0
    }
  ],
  "rawText": "",
  "provider": "",
  "warnings": []
}
```

## 9. 非功能实现方案

### 9.1 性能

- 列表查询采用分页
- 图片文件限制大小，例如 `10MB`
- 原图只保存路径，不直接存入数据库二进制字段
- 首页统计做简单聚合查询，避免复杂实时分析

### 9.2 安全

- 登录接口限流可后续补充
- 密码加密存储
- 重要接口鉴权
- 上传文件校验 MIME 和后缀
- 访问图片时校验登录态

### 9.3 可维护性

- DTO、Entity、VO 分层
- AI 适配器独立接口
- 日志模块独立服务
- 配置项集中管理，例如上传路径、识别服务地址

## 10. 开发迭代建议

### 10.1 第一阶段：项目骨架

- 初始化前端项目
- 初始化后端项目
- 建立数据库表
- 完成登录与基础布局

### 10.2 第二阶段：处方核心闭环

- 完成处方列表
- 完成处方详情
- 完成手动新增
- 完成处方编辑与删除

### 10.3 第三阶段：识别闭环

- 完成图片上传
- 完成识别任务接口
- 完成人工校对页面
- 完成草稿转正式处方

### 10.4 第四阶段：辅助管理

- 完成药材字典
- 完成用户管理
- 完成操作日志
- 完成首页统计

### 10.5 第五阶段：优化与答辩准备

- 接入真实 OCR 或模拟识别升级版
- 优化校验与交互
- 补充测试数据
- 整理系统架构图、ER 图、流程图

## 11. 测试方案

### 11.1 后端测试重点

- 登录鉴权测试
- 处方新增、编辑、删除测试
- 药味明细增删改测试
- 识别草稿确认测试
- 权限边界测试

### 11.2 前端测试重点

- 登录流程
- 表单校验
- 图片上传
- 动态药味编辑
- 列表筛选和分页

### 11.3 业务验证重点

- AI 识别失败后能否切换手动录入
- 低置信字段是否提示明确
- 原图、原始识别结果、最终结果是否都可追溯

## 12. 风险与应对

### 12.1 模型识别准确率不足

应对方式：

- 不把识别结果直接作为最终结果
- 强化人工校对页面
- 保留原始识别文本和置信度

### 12.2 开发周期有限

应对方式：

- 先实现 MVP 闭环
- 字典标准化和导出功能可后置
- 首页统计先做基础版

### 12.3 需求扩展导致耦合

应对方式：

- 提前拆分识别模块
- 使用统一 DTO 和状态枚举
- 数据表预留扩展字段或备注字段

## 13. 最终落地建议

如果当前目标是“尽快做出一个能演示、能答辩、能继续开发的版本”，推荐按下面顺序落地：

1. 先完成 `登录 + 处方列表 + 手动新增 + 详情`
2. 再完成 `图片上传 + 模拟识别 + 人工校对`
3. 最后补 `药材字典 + 用户管理 + 日志 + 首页统计`

这条路径能够用最少的技术风险完成 PRD 中最关键的业务闭环，也最适合作为 HerbScript 的第一版实现路线。
