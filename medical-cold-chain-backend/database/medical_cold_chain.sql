CREATE DATABASE IF NOT EXISTS medical_cold_chain
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE medical_cold_chain;

CREATE TABLE IF NOT EXISTS user_account (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  phone VARCHAR(20) NOT NULL UNIQUE,
  name VARCHAR(50) NOT NULL,
  organization VARCHAR(100) NOT NULL,
  role VARCHAR(20) NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL
);

CREATE TABLE IF NOT EXISTS login_code (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  phone VARCHAR(20) NOT NULL,
  code VARCHAR(6) NOT NULL,
  expires_at DATETIME NOT NULL,
  used BIT NOT NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  INDEX idx_login_code_phone (phone)
);

CREATE TABLE IF NOT EXISTS app_setting (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  setting_key VARCHAR(100) NOT NULL UNIQUE,
  setting_value VARCHAR(200) NOT NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL
);

CREATE TABLE IF NOT EXISTS transport_device (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  device_code VARCHAR(32) NOT NULL UNIQUE,
  device_name VARCHAR(100) NOT NULL,
  medicine_name VARCHAR(100) NOT NULL,
  route_name VARCHAR(100) NOT NULL,
  status VARCHAR(20) NOT NULL,
  current_user_id BIGINT NULL,
  borrowed_at DATETIME NULL,
  battery_level INT NOT NULL,
  signal_status BIT NOT NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  CONSTRAINT fk_transport_device_user
    FOREIGN KEY (current_user_id) REFERENCES user_account(id)
);

CREATE TABLE IF NOT EXISTS device_borrow_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  device_id BIGINT NOT NULL,
  borrower_id BIGINT NOT NULL,
  borrow_time DATETIME NOT NULL,
  temp_min DOUBLE NOT NULL DEFAULT 20,
  temp_max DOUBLE NOT NULL DEFAULT 30,
  humidity_min DOUBLE NOT NULL DEFAULT 40,
  humidity_max DOUBLE NOT NULL DEFAULT 70,
  light_max DOUBLE NOT NULL DEFAULT 13,
  return_time DATETIME NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  CONSTRAINT fk_device_borrow_record_device
    FOREIGN KEY (device_id) REFERENCES transport_device(id),
  CONSTRAINT fk_device_borrow_record_user
    FOREIGN KEY (borrower_id) REFERENCES user_account(id),
  INDEX idx_borrow_record_device_time (device_id, borrow_time),
  INDEX idx_borrow_record_user_time (borrower_id, borrow_time)
);

CREATE TABLE IF NOT EXISTS telemetry_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  device_id BIGINT NOT NULL,
  temperature DOUBLE NOT NULL,
  humidity DOUBLE NOT NULL,
  light DOUBLE NOT NULL,
  battery_level INT NOT NULL,
  signal_status BIT NOT NULL,
  recorded_at DATETIME NOT NULL,
  CONSTRAINT fk_telemetry_device
    FOREIGN KEY (device_id) REFERENCES transport_device(id),
  INDEX idx_telemetry_device_time (device_id, recorded_at)
);

CREATE TABLE IF NOT EXISTS device_location (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  device_id BIGINT NOT NULL,
  longitude DOUBLE NOT NULL,
  latitude DOUBLE NOT NULL,
  city VARCHAR(50) NOT NULL,
  address VARCHAR(200) NOT NULL,
  recorded_at DATETIME NOT NULL,
  CONSTRAINT fk_location_device
    FOREIGN KEY (device_id) REFERENCES transport_device(id),
  INDEX idx_location_device_time (device_id, recorded_at)
);

ALTER TABLE user_account
  ADD COLUMN IF NOT EXISTS role VARCHAR(20) NULL;

ALTER TABLE user_account
  ADD COLUMN IF NOT EXISTS borrow_limit_override INT NULL;

INSERT INTO app_setting (setting_key, setting_value, created_at, updated_at)
SELECT 'device.borrow.limit.default', '3', NOW(), NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM app_setting WHERE setting_key = 'device.borrow.limit.default'
);

ALTER TABLE transport_device
  ADD COLUMN IF NOT EXISTS borrowed_at DATETIME NULL;

ALTER TABLE device_borrow_record
  ADD COLUMN IF NOT EXISTS temp_min DOUBLE NOT NULL DEFAULT 20;

ALTER TABLE device_borrow_record
  ADD COLUMN IF NOT EXISTS temp_max DOUBLE NOT NULL DEFAULT 30;

ALTER TABLE device_borrow_record
  ADD COLUMN IF NOT EXISTS humidity_min DOUBLE NOT NULL DEFAULT 40;

ALTER TABLE device_borrow_record
  ADD COLUMN IF NOT EXISTS humidity_max DOUBLE NOT NULL DEFAULT 70;

ALTER TABLE device_borrow_record
  ADD COLUMN IF NOT EXISTS light_max DOUBLE NOT NULL DEFAULT 13;

ALTER TABLE device_borrow_record
  MODIFY COLUMN temp_min DOUBLE NOT NULL DEFAULT 20;

ALTER TABLE device_borrow_record
  MODIFY COLUMN temp_max DOUBLE NOT NULL DEFAULT 30;

ALTER TABLE device_borrow_record
  MODIFY COLUMN humidity_min DOUBLE NOT NULL DEFAULT 40;

ALTER TABLE device_borrow_record
  MODIFY COLUMN humidity_max DOUBLE NOT NULL DEFAULT 70;

ALTER TABLE device_borrow_record
  MODIFY COLUMN light_max DOUBLE NOT NULL DEFAULT 13;

UPDATE device_borrow_record
SET temp_min = 20, temp_max = 30, humidity_min = 40, humidity_max = 70, light_max = 13
WHERE temp_min = 3 AND temp_max = 7 AND humidity_min = 45 AND humidity_max = 70 AND light_max = 9;

ALTER TABLE device_borrow_record
  DROP COLUMN IF EXISTS duration_limit_hours;

DROP TABLE IF EXISTS device_threshold;
