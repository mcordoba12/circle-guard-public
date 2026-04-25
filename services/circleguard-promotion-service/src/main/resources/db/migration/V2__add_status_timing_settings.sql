ALTER TABLE system_settings
ADD COLUMN mandatory_fence_days INTEGER NOT NULL DEFAULT 14,
ADD COLUMN encounter_window_days INTEGER NOT NULL DEFAULT 14;

-- Insert default configuration record if table is empty
INSERT INTO system_settings (mandatory_fence_days, encounter_window_days, created_at, updated_at)
SELECT 14, 14, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM system_settings);
