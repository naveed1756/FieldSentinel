import SQLite from 'react-native-sqlite-storage';

SQLite.enablePromise(true);

class DatabaseService {
    private db: SQLite.SQLiteDatabase | null = null;

    async initDB() {
        this.db = await SQLite.openDatabase({ name: 'DatalakeFaceAuth.db', location: 'default' });

        // Table: attendance_records
        await this.db.executeSql(`
      CREATE TABLE IF NOT EXISTS attendance_records (
        id TEXT PRIMARY KEY,
        employee_id TEXT NOT NULL,
        timestamp_unix INTEGER NOT NULL,
        timestamp_iso TEXT NOT NULL,
        latitude REAL NOT NULL,
        longitude REAL NOT NULL,
        gps_accuracy_m REAL NOT NULL,
        mock_location INTEGER NOT NULL DEFAULT 0,
        face_match_score REAL NOT NULL,
        antispoof_score REAL NOT NULL,
        liveness_method TEXT NOT NULL,
        auth_result TEXT NOT NULL,
        cascade_abort_stage TEXT,
        drift_updated INTEGER NOT NULL DEFAULT 0,
        device_id TEXT NOT NULL,
        app_version TEXT NOT NULL,
        sync_status TEXT NOT NULL DEFAULT 'pending',
        synced_at_unix INTEGER,
        created_at_unix INTEGER NOT NULL
      );
    `);

        // Table: enrollment_meta
        await this.db.executeSql(`
      CREATE TABLE IF NOT EXISTS enrollment_meta (
        employee_id TEXT PRIMARY KEY,
        enrolled_at_unix INTEGER NOT NULL,
        enrolled_at_iso TEXT NOT NULL,
        embedding_version INTEGER NOT NULL DEFAULT 1,
        drift_update_count INTEGER NOT NULL DEFAULT 0,
        last_drift_at_unix INTEGER,
        device_id TEXT NOT NULL
      );
    `);

        // Indexes
        await this.db.executeSql(`CREATE INDEX IF NOT EXISTS idx_sync_status ON attendance_records (sync_status, created_at_unix);`);
        await this.db.executeSql(`CREATE INDEX IF NOT EXISTS idx_employee ON attendance_records (employee_id, created_at_unix);`);
    }

    async insertRecord(record: any) {
        if (!this.db) return;
        await this.db.executeSql(
            `INSERT INTO attendance_records (
        id, employee_id, timestamp_unix, timestamp_iso, 
        latitude, longitude, gps_accuracy_m, mock_location, 
        face_match_score, antispoof_score, liveness_method, auth_result, 
        cascade_abort_stage, drift_updated, device_id, app_version, 
        sync_status, created_at_unix
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
            [
                record.id, record.employee_id, record.timestamp_unix, record.timestamp_iso,
                record.latitude, record.longitude, record.gps_accuracy_m, record.mock_location,
                record.face_match_score, record.antispoof_score, record.liveness_method, record.auth_result,
                record.cascade_abort_stage, record.drift_updated, record.device_id, record.app_version,
                'pending', record.timestamp_unix
            ]
        );
    }

    async getPendingRecords(limit: number = 50) {
        if (!this.db) return [];
        const [results] = await this.db.executeSql(
            `SELECT * FROM attendance_records WHERE sync_status = 'pending' ORDER BY created_at_unix ASC LIMIT ?;`,
            [limit]
        );
        let records = [];
        for (let i = 0; i < results.rows.length; i++) {
            records.push(results.rows.item(i));
        }
        return records;
    }

    async markSynced(ids: string[], syncedAtUnix: number) {
        if (!this.db || ids.length === 0) return;
        const placeholders = ids.map(() => '?').join(',');
        await this.db.executeSql(
            `UPDATE attendance_records SET sync_status = 'synced', synced_at_unix = ? WHERE id IN (${placeholders})`,
            [syncedAtUnix, ...ids]
        );
    }

    async purgeSyncedOlderThan(cutoffUnix: number) {
        if (!this.db) return;
        await this.db.executeSql(
            `DELETE FROM attendance_records WHERE sync_status = 'synced' AND synced_at_unix < ?;`,
            [cutoffUnix]
        );
    }
}

export default new DatabaseService();