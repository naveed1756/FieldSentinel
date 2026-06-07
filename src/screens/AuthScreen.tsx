import React, { useState, useEffect } from 'react';
import {
  View, Text, Button, StyleSheet,
  Alert, ActivityIndicator, PermissionsAndroid, Platform, TextInput
} from 'react-native';
import { FaceAuthBridge } from '../bridges/FaceAuthBridge';
import DatabaseService from '../services/DatabaseService';
import LocationService from '../services/LocationService';
import NativeCameraView from '../components/NativeCameraView';

export const AuthScreen = () => {
  const [employeeId, setEmployeeId] = useState('');
  const [isAuthenticating, setIsAuthenticating] = useState(false);
  const [result, setResult] = useState<any>(null);
  const [status, setStatus] = useState('Ready');
  const [hasPermission, setHasPermission] = useState(false);

  useEffect(() => {
    requestCameraPermission();
  }, []);

  const requestCameraPermission = async () => {
    if (Platform.OS !== 'android') { setHasPermission(true); return; }
    try {
      const granted = await PermissionsAndroid.request(
        PermissionsAndroid.PERMISSIONS.CAMERA,
        {
          title: 'Camera Permission',
          message: 'FieldSentinel needs camera access to verify your identity.',
          buttonPositive: 'Allow',
          buttonNegative: 'Deny',
        }
      );
      if (granted === PermissionsAndroid.RESULTS.GRANTED) {
        const locationGranted = await LocationService.requestPermission();
        if (!locationGranted) {
          Alert.alert('Location Permission Denied', 'Location access is recommended for accurate attendance logging, but you can still authenticate without it.');
        }
        setHasPermission(true);
      } else {
        Alert.alert('Permission Denied', 'Camera is required for face authentication.');
      }
    } catch (err) {
      console.warn(err);
    }
  };

  const handleAuth = async () => {
    if (!employeeId.trim()) {
      Alert.alert('Missing ID', 'Please enter your Employee ID first.');
      return;
    }

    setIsAuthenticating(true);
    setResult(null);

    try {
      setStatus('Capturing face...');
      const base64Frame = await FaceAuthBridge.captureFrame();

      if (!base64Frame) {
        Alert.alert('No Frame', 'Could not capture camera frame. Try again.');
        setIsAuthenticating(false);
        return;
      }

      setStatus('Running AI pipeline...');
      const authResponse = await FaceAuthBridge.authenticate(
        employeeId.trim(),
        base64Frame,
        0.99
      );

      if (authResponse.success) {
        let location;
        try {
            location = await LocationService.getCurrentLocation();
            console.log('GPS Location:', location);
        } catch {
            Alert.alert('Location Error', 'Could not fetch location. Recording auth without location data.');
            location = { latitude: 0, longitude: 0, accuracy: 0 };
        }
        const record = {
          id:                  Math.random().toString(36).substring(2) + Date.now(),
          employee_id:         employeeId.trim(),
          timestamp_unix:      Date.now(),
          timestamp_iso:       new Date().toISOString(),
          latitude:            location.latitude,
          longitude:           location.longitude,
          gps_accuracy_m:      location.accuracy,
          mock_location:       0,
          face_match_score:    authResponse.faceMatchScore || 0,
          antispoof_score:     authResponse.antispoofScore || 0,
          liveness_method:     authResponse.livenessMethod || 'unknown',
          auth_result:         'SUCCESS',
          cascade_abort_stage: null,
          drift_updated:       authResponse.driftUpdated ? 1 : 0,
          device_id:           'DEVICE_001',
          app_version:         '1.0.0',
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
    message: authResponse.abortStage === 'NO_FACE'
      ? 'No face detected. Face the camera directly.'
      : authResponse.abortStage === 'FACE_TILTED'
      ? 'Face tilted. Look straight at the camera.'
      : authResponse.abortStage === 'ANTISPOOF_FAIL'
      ? 'Liveness check failed.'
      : authResponse.abortStage === 'RECOGNITION_FAIL'
      ? 'Face not recognised. Try again.'
      : authResponse.authResult,
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

      {hasPermission ? (
        <NativeCameraView style={styles.camera} />
      ) : (
        <View style={styles.permissionBox}>
          <Text style={styles.permissionText}>Waiting for camera permission...</Text>
          <Button title="Grant Permission" onPress={requestCameraPermission} />
        </View>
      )}

      <TextInput
        style={styles.input}
        placeholder="Enter Employee ID (e.g. EMP_001)"
        value={employeeId}
        onChangeText={setEmployeeId}
        editable={!isAuthenticating}
        autoCapitalize="none"
      />

      <Text style={styles.statusText}>{status}</Text>

      {isAuthenticating ? (
        <ActivityIndicator size="large" color="#0066cc" style={{ marginTop: 20 }} />
      ) : (
        <View style={styles.buttonContainer}>
          <Button
            title="Verify Face & Clock In"
            onPress={handleAuth}
            color="#0066cc"
            disabled={!hasPermission}
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
          {result.method && <Text>Liveness: {result.method}</Text>}
          {result.message && <Text>{result.message}</Text>}
        </View>
      )}
    </View>
  );
};

const styles = StyleSheet.create({
  container:      { flex:1, padding:20, backgroundColor:'#f5f5f5' },
  title:          { fontSize:24, fontWeight:'bold', textAlign:'center', color:'#333', marginBottom:4 },
  subtitle:       { fontSize:14, textAlign:'center', color:'#666', marginBottom:16 },
  camera:         { height:340, borderRadius:12, overflow:'hidden', marginBottom:12 },
  permissionBox:  { height:340, justifyContent:'center', alignItems:'center', backgroundColor:'#ddd', borderRadius:12, marginBottom:12 },
  permissionText: { fontSize:16, color:'#666', marginBottom:16 },
  input:          { borderWidth:1, borderColor:'#ccc', padding:12, marginBottom:8, borderRadius:8, backgroundColor:'#fff', fontSize:16 },
  statusText:     { textAlign:'center', fontSize:13, color:'#888', marginBottom:12 },
  buttonContainer:{ marginTop:8 },
  resultBox:      { marginTop:20, padding:20, borderWidth:2, borderRadius:10, alignItems:'center', backgroundColor:'#fff' },
  resultText:     { fontSize:20, fontWeight:'bold', marginBottom:8 },
});