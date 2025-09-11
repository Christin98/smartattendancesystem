# Smart Attendance System

An Android-based employee attendance system with face recognition, liveness detection, and offline capabilities that syncs with PostgreSQL on Azure.

## Features

### Core Functionality
- **Face Recognition**: Uses ML Kit for face detection and custom embedding model for identification
- **Liveness Detection**: Advanced anti-spoofing with multiple checks:
  - Eye blink detection
  - Head pose variation analysis  
  - Facial expression monitoring
  - Temporal consistency checks
- **Offline Support**: Full offline functionality with local SQLite database
- **Automatic Sync**: Background sync with PostgreSQL when network available
- **Network Monitoring**: Automatic detection of connectivity changes

### Security Features
- Liveness detection prevents photo/video spoofing
- Confidence scoring for face matching (configurable threshold)
- Secure data sync with retry logic
- Client UUID for conflict resolution

## Architecture

### Local Storage (SQLite)
- **Employees**: Store employee profiles with face embeddings
- **Attendance**: Track check-in/out records with metadata
- Room database with type converters for complex data

### Cloud Storage (PostgreSQL on Azure)
- Centralized attendance records
- Employee management
- Device tracking
- Sync conflict resolution

### Sync Mechanism
- WorkManager for reliable background sync
- Batch processing (50 records per batch)
- Exponential backoff for retries
- Network state monitoring for immediate sync

## Setup Instructions

### 1. Azure PostgreSQL Setup
1. Create Azure Database for PostgreSQL
2. Run the schema from `postgresql_schema.sql`
3. Note connection details for API configuration

### 2. Backend API Setup
Deploy a REST API with these endpoints:
- `POST /api/attendance/sync` - Sync attendance records
- `GET /api/employees` - Fetch employee list
- `POST /api/employees` - Register new employee

### 3. Android App Configuration
1. Update `NetworkModule.kt` with your API base URL
2. Configure device ID in `AttendanceSyncWorker.kt`
3. Build and deploy the APK

### 4. Permissions Required
```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

## Usage

### Employee Registration
1. Navigate to Register screen
2. Enter employee details
3. Capture face photo for enrollment
4. System creates embedding for recognition

### Attendance Marking
1. Open main screen (camera view)
2. Position face in camera
3. Tap CHECK IN or CHECK OUT
4. System performs:
   - Face detection
   - Liveness verification
   - Employee matching
   - Local storage
   - Background sync

### Offline Mode
- All features work offline
- Data stored locally in SQLite
- Automatic sync when online
- Conflict resolution via client UUID

## Technical Details

### Face Recognition Pipeline
1. Capture image via CameraX
2. Detect faces using ML Kit
3. Extract face region
4. Generate embedding (128-dimensional vector)
5. Compare with stored embeddings (cosine similarity)
6. Apply threshold (default 0.6)

### Liveness Detection Algorithm
- **Eye Analysis**: Track blink patterns and openness
- **Head Pose**: Monitor rotation angles
- **Expression Changes**: Detect natural variations
- **Anti-Spoofing**: Check face size ratios and tracking stability
- **Scoring**: Weighted combination of factors

### Sync Strategy
- Immediate sync on network availability
- Periodic sync every 15 minutes
- Batch processing for efficiency
- Retry with exponential backoff
- Partial success handling

## Database Schema

### Local (SQLite)
- `employees`: id, code, name, embedding
- `attendance`: id, employee_id, timestamp, type, liveness, synced

### Cloud (PostgreSQL)
- Extended schema with audit trails
- Views for reporting
- Stored procedures for conflict resolution

## Error Handling
- Network failures: Automatic retry
- Face detection failures: User feedback
- Liveness failures: Request retry
- Sync conflicts: UUID-based resolution

## Performance Optimizations
- Batch sync (50 records)
- Image compression for thumbnails
- Efficient embedding comparison
- Background processing
- Caching strategies

## Security Considerations
- No credentials stored in app
- API authentication required
- Encrypted data transmission
- Liveness detection mandatory
- Audit logging

## Future Enhancements
- TensorFlow Lite for better embeddings
- Multi-face detection
- GPS location tracking
- Advanced reporting dashboard
- Push notifications
- Facial mask detection