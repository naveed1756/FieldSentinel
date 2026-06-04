import React, { useState, useEffect } from 'react';
import {
  View, Text, TextInput, Button, StyleSheet,
  Alert, ActivityIndicator, PermissionsAndroid, Platform
} from 'react-native';
import { FaceAuthBridge } from '../bridges/FaceAuthBridge';
import NativeCameraView from '../components/NativeCameraView';

export const EnrollmentScreen = () => {
  const [employeeId, setEmployeeId]     = useState('EMP_001');
  const [isEnrolling, setIsEnrolling]   = useState(false);
  const [status, setStatus]             = useState('Position face in frame');
  const [hasPermission, setHasPermission] = useState(false);

  useEffect(() => {
    requestPermission();
  }, []);

  const requestPermission = async () => {
    if (Platform.OS !== 'android') { setHasPermission(true); return; }
    const granted = await PermissionsAndroid.request(
      PermissionsAndroid.PERMISSIONS.CAMERA
    );
    setHasPermission(granted === PermissionsAndroid.RESULTS.GRANTED);
  };

  const handleEnroll = async () => {
    if (!employeeId.trim()) {
      Alert.alert('Error', 'Enter an Employee ID first');
      return;
    }

    setIsEnrolling(true);
    setStatus('Capturing frames...');

    try {
      // Capture 5 frames in quick succession
      const frames: string[] = [];
      for (let i = 0; i < 5; i++) {
        setStatus(`Capturing frame ${i + 1} of 5...`);
        const frame = await FaceAuthBridge.captureFrame();
        if (frame) frames.push(frame);
        await new Promise(r => setTimeout(r, 300)); // 300ms between captures
      }

      if (frames.length < 3) {
        Alert.alert('Error', 'Could not capture enough frames. Try again.');
        setStatus('Position face in frame');
        setIsEnrolling(false);
        return;
      }

      setStatus('Processing enrollment...');
      const success = await FaceAuthBridge.enroll(employeeId.trim(), frames);

      if (success) {
        setStatus('Enrolled successfully ✅');
        Alert.alert('Success', `${employeeId} enrolled. You can now authenticate.`);
      } else {
        setStatus('Enrollment failed ❌');
        Alert.alert('Failed', 'Enrollment failed. Try again.');
      }
    } catch (error: any) {
      console.error(error);
      Alert.alert('Error', error.message || 'Enrollment crashed.');
      setStatus('Error — try again');
    } finally {
      setIsEnrolling(false);
    }
  };

  return (
    <View style={styles.container}>
      <Text style={styles.title}>Face Enrollment</Text>

      {hasPermission ? (
        <NativeCameraView style={styles.camera} />
      ) : (
        <View style={styles.permBox}>
          <Text style={styles.permText}>Camera permission needed</Text>
          <Button title="Grant Permission" onPress={requestPermission} />
        </View>
      )}

      <TextInput
        style={styles.input}
        placeholder="Employee ID (e.g. EMP_001)"
        value={employeeId}
        onChangeText={setEmployeeId}
        editable={!isEnrolling}
      />

      <Text style={styles.status}>{status}</Text>

      {isEnrolling ? (
        <ActivityIndicator size="large" color="#0066cc" />
      ) : (
        <Button
          title="Capture & Enroll"
          onPress={handleEnroll}
          color="#0066cc"
          disabled={!hasPermission}
        />
      )}
    </View>
  );
};

const styles = StyleSheet.create({
  container: { flex:1, padding:20, backgroundColor:'#f5f5f5' },
  title:     { fontSize:22, fontWeight:'bold', textAlign:'center', marginBottom:16, color:'#333' },
  camera:    { height:340, borderRadius:12, overflow:'hidden', marginBottom:16 },
  permBox:   { height:340, justifyContent:'center', alignItems:'center', backgroundColor:'#ddd', borderRadius:12, marginBottom:16 },
  permText:  { fontSize:15, color:'#666', marginBottom:12 },
  input:     { borderWidth:1, borderColor:'#ccc', padding:14, marginBottom:12, borderRadius:8, backgroundColor:'#fff', fontSize:16 },
  status:    { textAlign:'center', fontSize:16, fontWeight:'500', color:'#444', marginBottom:16 },
});