SET FOREIGN_KEY_CHECKS=0;
DELETE FROM device_location;
DELETE FROM telemetry_record;
DELETE FROM device_borrow_record;
DELETE FROM transport_device;
SET FOREIGN_KEY_CHECKS=1;
SELECT 'transport_device' AS table_name, COUNT(*) AS row_count FROM transport_device
UNION ALL
SELECT 'device_borrow_record', COUNT(*) FROM device_borrow_record
UNION ALL
SELECT 'telemetry_record', COUNT(*) FROM telemetry_record
UNION ALL
SELECT 'device_location', COUNT(*) FROM device_location;
