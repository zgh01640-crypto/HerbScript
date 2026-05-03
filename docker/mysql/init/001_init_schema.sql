SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS herbscript DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE herbscript;

CREATE TABLE IF NOT EXISTS sys_user (
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

CREATE TABLE IF NOT EXISTS sys_role (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  role_code VARCHAR(64) NOT NULL UNIQUE,
  role_name VARCHAR(64) NOT NULL,
  remark VARCHAR(255) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sys_user_role (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_role (user_id, role_id)
);

CREATE TABLE IF NOT EXISTS patient (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  patient_no VARCHAR(64) NOT NULL,
  name VARCHAR(64) NOT NULL,
  gender VARCHAR(16) NOT NULL,
  age INT NOT NULL,
  phone VARCHAR(32) NULL,
  remark VARCHAR(255) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_patient_no (patient_no),
  KEY idx_patient_name (name),
  KEY idx_patient_phone (phone)
);

CREATE TABLE IF NOT EXISTS prescription (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  prescription_no VARCHAR(64) NOT NULL,
  hospital_name VARCHAR(128) NULL,
  prescription_type VARCHAR(64) NULL,
  patient_id BIGINT NULL,
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

CREATE TABLE IF NOT EXISTS prescription_item (
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

CREATE TABLE IF NOT EXISTS recognition_task (
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

CREATE TABLE IF NOT EXISTS recognition_field (
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

CREATE TABLE IF NOT EXISTS herb_dictionary (
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

CREATE TABLE IF NOT EXISTS operation_log (
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

CREATE TABLE IF NOT EXISTS system_setting (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  setting_key VARCHAR(128) NOT NULL,
  setting_value TEXT NULL,
  setting_group VARCHAR(64) NOT NULL DEFAULT 'system',
  remark VARCHAR(255) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_setting_key (setting_key),
  KEY idx_setting_group (setting_group)
);

CREATE TABLE IF NOT EXISTS model_profile (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  profile_name VARCHAR(128) NOT NULL,
  provider VARCHAR(64) NOT NULL,
  doubao_base_url VARCHAR(255) NOT NULL,
  doubao_model VARCHAR(128) NOT NULL,
  doubao_chat_path VARCHAR(255) NOT NULL,
  doubao_api_key VARCHAR(255) NULL,
  fallback_to_mock_on_error TINYINT NOT NULL DEFAULT 1,
  is_active TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  KEY idx_model_profile_active (is_active)
);

CREATE TABLE IF NOT EXISTS agent_session (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  anchor_type VARCHAR(32) NOT NULL,
  anchor_id BIGINT NULL,
  title VARCHAR(255) NOT NULL,
  session_status VARCHAR(32) NOT NULL DEFAULT 'active',
  last_message_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  KEY idx_agent_session_anchor (anchor_type, anchor_id),
  KEY idx_agent_session_user (user_id),
  KEY idx_agent_session_last_message (last_message_at)
);

CREATE TABLE IF NOT EXISTS agent_message (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  session_id BIGINT NOT NULL,
  role VARCHAR(32) NOT NULL,
  content LONGTEXT NOT NULL,
  structured_payload LONGTEXT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_agent_message_session (session_id),
  KEY idx_agent_message_created_at (created_at)
);

CREATE TABLE IF NOT EXISTS agent_tool_call (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  session_id BIGINT NOT NULL,
  message_id BIGINT NULL,
  tool_name VARCHAR(128) NOT NULL,
  tool_label VARCHAR(255) NULL,
  input_json LONGTEXT NULL,
  output_json LONGTEXT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'success',
  error_message VARCHAR(500) NULL,
  latency_ms INT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_agent_tool_call_session (session_id),
  KEY idx_agent_tool_call_message (message_id),
  KEY idx_agent_tool_call_created_at (created_at)
);

CREATE TABLE IF NOT EXISTS agent_trace (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  session_id BIGINT NOT NULL,
  message_id BIGINT NULL,
  model_name VARCHAR(128) NULL,
  prompt_tokens INT NULL,
  completion_tokens INT NULL,
  total_tokens INT NULL,
  latency_ms INT NULL,
  trace_payload LONGTEXT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_agent_trace_session (session_id),
  KEY idx_agent_trace_message (message_id),
  KEY idx_agent_trace_created_at (created_at)
);

CREATE TABLE IF NOT EXISTS agent_memory (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  session_id BIGINT NULL,
  anchor_type VARCHAR(32) NULL,
  anchor_id BIGINT NULL,
  memory_scope VARCHAR(32) NOT NULL DEFAULT 'session',
  memory_key VARCHAR(128) NOT NULL,
  memory_value LONGTEXT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  KEY idx_agent_memory_session (session_id),
  KEY idx_agent_memory_anchor (anchor_type, anchor_id),
  KEY idx_agent_memory_scope (memory_scope, memory_key)
);

CREATE TABLE IF NOT EXISTS agent_skill_run (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  session_id BIGINT NOT NULL,
  message_id BIGINT NULL,
  skill_name VARCHAR(128) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'success',
  output_json LONGTEXT NULL,
  latency_ms INT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_agent_skill_run_session (session_id),
  KEY idx_agent_skill_run_message (message_id),
  KEY idx_agent_skill_run_created_at (created_at)
);

CREATE TABLE IF NOT EXISTS agent_note (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  session_id BIGINT NULL,
  anchor_type VARCHAR(32) NOT NULL,
  anchor_id BIGINT NOT NULL,
  note_type VARCHAR(64) NOT NULL,
  title VARCHAR(255) NOT NULL,
  content LONGTEXT NOT NULL,
  answer_confidence VARCHAR(16) NULL,
  remaining_uncertainties_json LONGTEXT NULL,
  is_pinned TINYINT NOT NULL DEFAULT 0,
  created_by BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  KEY idx_agent_note_anchor (anchor_type, anchor_id),
  KEY idx_agent_note_session (session_id),
  KEY idx_agent_note_note_type (note_type),
  KEY idx_agent_note_created_at (created_at)
);

INSERT IGNORE INTO sys_role (id, role_code, role_name, remark)
VALUES
  (1, 'ADMIN', '管理员', '系统管理员'),
  (2, 'EDITOR', '录入员', '负责识别和录入'),
  (3, 'DOCTOR', '医师', '负责查看和补充'),
  (4, 'PHARMACIST', '药房人员', '负责调剂核对');

INSERT IGNORE INTO sys_user (id, username, password, real_name, status)
VALUES
  (1, 'admin', '$2a$10$abcdefghijklmnopqrstuv', CONVERT(0xE7B3BBE7BB9FE7AEA1E79086E59198 USING utf8mb4), 'enabled');

INSERT IGNORE INTO sys_user_role (id, user_id, role_id)
VALUES
  (1, 1, 1);
