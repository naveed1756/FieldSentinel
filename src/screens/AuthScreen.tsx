import React, { useState } from 'react';
import { View, Text, Button, StyleSheet, Alert, ActivityIndicator } from 'react-native';
import { FaceAuthBridge } from '../bridges/FaceAuthBridge';
import DatabaseService from '../services/DatabaseService';
import LocationService from '../services/LocationService';

export const AuthScreen = () => {
    const [employeeId, setEmployeeId] = useState('EMP_001'); // Hardcoded for testing
    const [isAuthenticating, setIsAuthenticating] = useState(false);
    const [result, setResult] = useState<any>(null);

    const handleAuth = async () => {
        setIsAuthenticating(true);
        setResult(null);

        try {
            // 1. Trigger the Confidence Cascade Native Pipeline
            const authResponse = await FaceAuthBridge.authenticate(employeeId);

            if (authResponse.success) {
                // 2. Grab the real GPS coordinates!
                const location = await LocationService.getCurrentPosition();
                console.log("📍 Captured GPS: ", location.latitude, location.longitude);

                // 3. Build the record with REAL location data
                const record = {
                    id: Math.random().toString(36).substring(7), // Dummy UUID
                    employee_id: employeeId,
                    timestamp_unix: Date.now(),
                    timestamp_iso: new Date().toISOString(),
                    latitude: location.latitude,           // <-- Replaced Dummy Data
                    longitude: location.longitude,         // <-- Replaced Dummy Data
                    gps_accuracy_m: location.gps_accuracy_m, // <-- Replaced Dummy Data
                    mock_location: location.mock_location,   // <-- Replaced Dummy Data
                    face_match_score: authResponse.score || 0.95,
                    antispoof_score: 0.99,
                    liveness_method: 'skipped_high_conf',
                    auth_result: 'SUCCESS',
                    cascade_abort_stage: null,
                    drift_updated: 0,
                    device_id: 'TEST_DEVICE_1',
                    app_version: '1.0.0'
                };

                // 4. Save to SQLite
                await DatabaseService.insertRecord(record);
                setResult({ status: 'Success ✅', color: 'green', score: record.face_match_score });

            } else {
                setResult({
                    status: `Failed ❌: ${authResponse.errorStage}`,
                    color: 'red',
                    message: authResponse.errorMessage
                });
            }
        } catch (error) {
            console.error(error);
            Alert.alert('System Error', 'Authentication pipeline crashed.');
        } finally {
            setIsAuthenticating(false);
        }
    };

    return (
        <View style={styles.container}>
            <Text style={styles.title}>NHAI Datalake Auth</Text>

            {/* Placeholder for react-native-vision-camera */}
            <View style={styles.cameraContainer}>
                <Text style={styles.cameraText}>Live Camera Feed</Text>
                <Text style={styles.targetBox}>[ Align Face Here ]</Text>
            </View>

            <Text style={styles.employeeText}>Clocking in as: {employeeId}</Text>

            {isAuthenticating ? (
                <ActivityIndicator size="large" color="#0066cc" style={{ marginTop: 20 }} />
            ) : (
                <View style={styles.buttonContainer}>
                    <Button title="Verify Face & Clock In" onPress={handleAuth} color="#0066cc" />
                </View>
            )}

            {result && (
                <View style={[styles.resultBox, { borderColor: result.color }]}>
                    <Text style={[styles.resultText, { color: result.color }]}>{result.status}</Text>
                    {result.score && <Text>Match Score: {(result.score * 100).toFixed(1)}%</Text>}
                    {result.message && <Text>{result.message}</Text>}
                </View>
            )}
        </View>
    );
};

const styles = StyleSheet.create({
    container: { flex: 1, padding: 20, justifyContent: 'center', backgroundColor: '#f5f5f5' },
    title: { fontSize: 24, fontWeight: 'bold', textAlign: 'center', marginBottom: 20, color: '#333' },
    cameraContainer: { height: 400, backgroundColor: '#000', justifyContent: 'center', alignItems: 'center', marginBottom: 20, borderRadius: 15 },
    cameraText: { color: '#fff', position: 'absolute', top: 20 },
    targetBox: { color: '#00ff00', fontSize: 20, fontWeight: 'bold', borderWidth: 2, borderColor: '#00ff00', padding: 40, borderStyle: 'dashed' },
    employeeText: { textAlign: 'center', fontSize: 16, marginBottom: 20, color: '#666' },
    buttonContainer: { marginTop: 10 },
    resultBox: { marginTop: 30, padding: 20, borderWidth: 2, borderRadius: 10, alignItems: 'center', backgroundColor: '#fff' },
    resultText: { fontSize: 20, fontWeight: 'bold', marginBottom: 10 }
});
