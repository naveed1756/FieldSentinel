import React, { useState } from 'react';
import {
  View, Text, Button, StyleSheet,
  Alert, ActivityIndicator, PermissionsAndroid, Platform
} from 'react-native';
import { FaceAuthBridge } from '../bridges/FaceAuthBridge';
import DatabaseService from '../services/DatabaseService';
import NativeCameraView from '../components/NativeCameraView';

const EMPLOYEE_ID = 'EMP_001';

const requestCameraPermission = async (): Promise<boolean> => {
  if (Platform.OS !== 'android') return true;
  const granted = await PermissionsAndroid.request(
    PermissionsAndroid.PERMISSIONS.CAMERA,
    {
      title: 'Camera Permission',
      message: 'FieldSentinel needs camera access to verify your identity.',
      buttonPositive: 'Allow',
    }
  );
  return granted === PermissionsAndroid.RESULTS.GRANTED;
};

export const AuthScreen = () => {
  const [isAuthenticating, setIsAuthenticating] = useState(false);
  const [result, setResult]                     = useState<any>(null);
  const [status, setStatus]                     = useState('Ready');

  const handleAuth = async () => {
    setIsAuthenticating(true);
    setResult(null);

    try {
      // 1. Check camera permission
      const hasPermission = await requestCameraPermission();
      if (!hasPermission) {
        Alert.alert('Permission Denied', 'Camera access is required.');
        return;
      }

      // 2. Capture current frame from native camera view
      setStatus('Capturing face...');
      const base64Frame = await FaceAuthBridge.captureFrame();

      if (!base64Frame) {
        Alert.alert('No Frame', 'Could not capture camera frame. Try again.');
        return;
      }

      // 3. Run the confidence cascade pipeline
      setStatus('Running AI pipeline...');
      const authResponse = await FaceAuthBridge.authenticate(
        EMPLOYEE_ID,
        base64Frame,
        0.99  // detection confidence — MediaPipe will provide real value later
      );

      if (authResponse.success) {
        // 4. Save to SQLite
        const record = {
          id:                   Math.random().toString(36).substring(2) + Date.now(),
          employee_id:          EMPLOYEE_ID,
          timestamp_unix:       Date.now(),
          timestamp_iso:        new Date().toISOString(),
          latitude:             12.9716,
          longitude:            77.5946,
          gps_accuracy_m:       5.0,
          mock_location:        0,
          face_match_score:     authResponse.faceMatchScore || 0,
          antispoof_score:      authResponse.antispoofScore || 0,
          liveness_method:      authResponse.livenessMethod || 'unknown',
          auth_result:          'SUCCESS',
          cascade_abort_stage:  null,
          drift_updated:        authResponse.driftUpdated ? 1 : 0,
          device_id:            'DEVICE_001',
          app_version:          '1.0.0',
        };

        await DatabaseService.insertRecord(record);
        setStatus('Ready');
        setResult({
          status: 'Authenticated ✅',
          color:  'green',
          score:  authResponse.faceMatchScore,
          method: authResponse.livenessMethod,
        });

      } else {
        setStatus('Ready');
        setResult({
          status:  `Failed ❌: ${authResponse.abortStage}`,
          color:   'red',
          message: authResponse.authResult,
        });
      }

    } catch (error: any) {
      console.error(error);
      Alert.alert('System Error', error.message || 'Pipeline crashed.');
    } finally {
      setIsAuthenticating(false);
    }
  };

  return (
    <View style={styles.container}>
      <Text style={styles.title}>FieldSentinel</Text>
      <Text style={styles.subtitle}>NHAI Field Authentication</Text>

      {/* Live camera preview from native Camera2 */}
      <NativeCameraView style={styles.camera} />

      <Text style={styles.employeeText}>Employee: {EMPLOYEE_ID}</Text>
      <Text style={styles.statusText}>{status}</Text>

      {isAuthenticating ? (
        <ActivityIndicator size="large" color="#0066cc" style={{ marginTop: 20 }} />
      ) : (
        <View style={styles.buttonContainer}>
          <Button
            title="Verify Face & Clock In"
            onPress={handleAuth}
            color="#0066cc"
          />
        </View>
      )}

      {result && (
        <View style={[styles.resultBox, { borderColor: result.color }]}>
          <Text style={[styles.resultText, { color: result.color }]}>
            {result.status}
          </Text>
          {result.score !== undefined && (
            <Text>Match Score: {(result.score * 100).toFixed(1)}%</Text>
          )}
          {result.method && (
            <Text>Liveness: {result.method}</Text>
          )}
          {result.message && <Text>{result.message}</Text>}
        </View>
      )}
    </View>
  );
};

const styles = StyleSheet.create({
  container:     { flex:1, padding:20, backgroundColor:'#f5f5f5' },
  title:         { fontSize:24, fontWeight:'bold', textAlign:'center', color:'#333', marginBottom:4 },
  subtitle:      { fontSize:14, textAlign:'center', color:'#666', marginBottom:16 },
  camera:        { height:380, borderRadius:12, overflow:'hidden', marginBottom:16 },
  employeeText:  { textAlign:'center', fontSize:16, color:'#444', marginBottom:4 },
  statusText:    { textAlign:'center', fontSize:13, color:'#888', marginBottom:12 },
  buttonContainer: { marginTop:8 },
  resultBox:     { marginTop:24, padding:20, borderWidth:2, borderRadius:10, alignItems:'center', backgroundColor:'#fff' },
  resultText:    { fontSize:20, fontWeight:'bold', marginBottom:8 },
});