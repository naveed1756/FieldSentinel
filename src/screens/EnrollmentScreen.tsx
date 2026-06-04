import React, { useState } from 'react';
import { View, Text, TextInput, Button, StyleSheet, Alert } from 'react-native';
import { FaceAuthBridge } from '../bridges/FaceAuthBridge';

export const EnrollmentScreen = () => {
    const [employeeId, setEmployeeId] = useState('');
    const [isEnrolling, setIsEnrolling] = useState(false);
    const [status, setStatus] = useState('Not Enrolled');

    const handleEnroll = async () => {
        if (!employeeId) {
            Alert.alert('Error', 'Please enter an Employee ID (e.g., EMP_001)');
            return;
        }

        setIsEnrolling(true);
        // The native module handles capturing the 5 frames in quick succession [cite: 520]
        setStatus('Capturing 5 frames...');

        try {
            const success = await FaceAuthBridge.enroll(employeeId);
            if (success) {
                setStatus('Enrollment Successful! ✅');
                Alert.alert('Success', `Employee ${employeeId} enrolled successfully.`);
            } else {
                setStatus('Enrollment Failed ❌');
            }
        } catch (error) {
            setStatus('Error during enrollment');
            console.error(error);
        } finally {
            setIsEnrolling(false);
        }
    };

    return (
        <View style={styles.container}>
            <Text style={styles.title}>NHAI Face Enrollment</Text>

            {/* Placeholder for react-native-vision-camera */}
            <View style={styles.cameraPlaceholder}>
                <Text>Native Camera View Goes Here</Text>
            </View>

            <TextInput
                style={styles.input}
                placeholder="Enter Employee ID (e.g., EMP_001)"
                value={employeeId}
                onChangeText={setEmployeeId}
            />

            <Button
                title={isEnrolling ? "Processing AI..." : "Capture & Enroll"}
                onPress={handleEnroll}
                disabled={isEnrolling}
                color="#0066cc"
            />

            <Text style={styles.status}>{status}</Text>
        </View>
    );
};

const styles = StyleSheet.create({
    container: { flex: 1, padding: 20, justifyContent: 'center', backgroundColor: '#f5f5f5' },
    title: { fontSize: 24, fontWeight: 'bold', textAlign: 'center', marginBottom: 20, color: '#333' },
    cameraPlaceholder: { height: 350, backgroundColor: '#d3d3d3', justifyContent: 'center', alignItems: 'center', marginBottom: 20, borderRadius: 10 },
    input: { borderWidth: 1, borderColor: '#ccc', padding: 15, marginBottom: 20, borderRadius: 8, backgroundColor: '#fff', fontSize: 16 },
    status: { marginTop: 20, textAlign: 'center', fontSize: 18, fontWeight: 'bold', color: '#444' }
});