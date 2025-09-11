-- PostgreSQL Schema for Smart Attendance System
-- Deploy this on Azure Database for PostgreSQL

-- Create database (run separately if needed)
-- CREATE DATABASE smart_attendance;

-- Extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Employees table
CREATE TABLE IF NOT EXISTS employees (
    employee_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    employee_code VARCHAR(50) UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    department VARCHAR(100),
    designation VARCHAR(100),
    phone VARCHAR(20),
    embedding BYTEA NOT NULL,
    face_image BYTEA,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Attendance records table
CREATE TABLE IF NOT EXISTS attendance (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    client_uuid VARCHAR(255) UNIQUE NOT NULL,
    device_id VARCHAR(100) NOT NULL,
    employee_id UUID REFERENCES employees(employee_id),
    employee_code VARCHAR(50),
    check_type VARCHAR(10) NOT NULL CHECK (check_type IN ('IN', 'OUT')),
    timestamp TIMESTAMPTZ NOT NULL,
    confidence DECIMAL(5,4) NOT NULL,
    liveness BOOLEAN NOT NULL,
    thumbnail_base64 TEXT,
    location_lat DECIMAL(10,8),
    location_lng DECIMAL(11,8),
    synced_at TIMESTAMPTZ DEFAULT NOW(),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    
    INDEX idx_employee_timestamp (employee_id, timestamp),
    INDEX idx_device_timestamp (device_id, timestamp),
    INDEX idx_client_uuid (client_uuid)
);

-- Device registration table
CREATE TABLE IF NOT EXISTS devices (
    device_id VARCHAR(100) PRIMARY KEY,
    device_name VARCHAR(255),
    location VARCHAR(255),
    last_sync TIMESTAMPTZ,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Sync logs for monitoring
CREATE TABLE IF NOT EXISTS sync_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    device_id VARCHAR(100) NOT NULL,
    sync_type VARCHAR(50),
    records_synced INTEGER,
    status VARCHAR(20),
    error_message TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Function to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger for employees table
CREATE TRIGGER update_employees_updated_at BEFORE UPDATE ON employees
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Views for reporting
CREATE OR REPLACE VIEW daily_attendance AS
SELECT 
    e.employee_code,
    e.name,
    DATE(a.timestamp) as attendance_date,
    MIN(CASE WHEN a.check_type = 'IN' THEN a.timestamp END) as first_check_in,
    MAX(CASE WHEN a.check_type = 'OUT' THEN a.timestamp END) as last_check_out,
    COUNT(CASE WHEN a.check_type = 'IN' THEN 1 END) as check_in_count,
    COUNT(CASE WHEN a.check_type = 'OUT' THEN 1 END) as check_out_count
FROM attendance a
JOIN employees e ON a.employee_id = e.employee_id
GROUP BY e.employee_code, e.name, DATE(a.timestamp);

-- View for attendance summary
CREATE OR REPLACE VIEW attendance_summary AS
SELECT 
    e.employee_code,
    e.name,
    e.department,
    COUNT(DISTINCT DATE(a.timestamp)) as days_present,
    AVG(a.confidence) as avg_confidence,
    COUNT(CASE WHEN NOT a.liveness THEN 1 END) as failed_liveness_count
FROM attendance a
JOIN employees e ON a.employee_id = e.employee_id
WHERE a.timestamp >= DATE_TRUNC('month', CURRENT_DATE)
GROUP BY e.employee_code, e.name, e.department;

-- Indexes for performance
CREATE INDEX idx_attendance_timestamp ON attendance(timestamp);
CREATE INDEX idx_attendance_employee_date ON attendance(employee_id, DATE(timestamp));
CREATE INDEX idx_employees_active ON employees(is_active) WHERE is_active = true;

-- Sample stored procedure for conflict resolution
CREATE OR REPLACE FUNCTION upsert_attendance(
    p_client_uuid VARCHAR,
    p_device_id VARCHAR,
    p_employee_id UUID,
    p_employee_code VARCHAR,
    p_check_type VARCHAR,
    p_timestamp TIMESTAMPTZ,
    p_confidence DECIMAL,
    p_liveness BOOLEAN,
    p_thumbnail TEXT
) RETURNS VOID AS $$
BEGIN
    INSERT INTO attendance (
        client_uuid, device_id, employee_id, employee_code, 
        check_type, timestamp, confidence, liveness, thumbnail_base64
    ) VALUES (
        p_client_uuid, p_device_id, p_employee_id, p_employee_code,
        p_check_type, p_timestamp, p_confidence, p_liveness, p_thumbnail
    )
    ON CONFLICT (client_uuid) 
    DO UPDATE SET 
        synced_at = NOW()
    WHERE attendance.client_uuid = p_client_uuid;
END;
$$ LANGUAGE plpgsql;

-- Grant permissions (adjust as needed)
-- GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO your_app_user;
-- GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO your_app_user;
-- GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO your_app_user;