-- Create system_settings table (missing from V1)
-- This table holds global configuration for health status policies
-- The mandatory_fence_days and encounter_window_days columns will be added by V2

CREATE TABLE IF NOT EXISTS system_settings (
    id SERIAL PRIMARY KEY,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
