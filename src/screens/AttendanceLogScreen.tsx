import React, { useEffect, useState } from 'react';
import { View, Text, FlatList, StyleSheet, Button, Alert, ActivityIndicator } from 'react-native';
import DatabaseService from '../services/DatabaseService';
import SyncService from '../services/SyncService';

export const AttendanceLogScreen = ({ onBack }: { onBack: () => void }) => {
    const [records, setRecords] = useState<any[]>([]);
    const [isSyncing, setIsSyncing] = useState(false);

    const fetchRecords = async () => {
        // We fetch a mix of pending and some synced records just to see them in the UI
        const pending = await DatabaseService.getPendingRecords(50);
        setRecords(pending);
    };

    useEffect(() => {
        fetchRecords();
    }, []);

    const handleSync = async () => {
        setIsSyncing(true);
        const result = await SyncService.syncNow();
        setIsSyncing(false);

        Alert.alert(result.success ? 'Success' : 'Error', result.message);

        // Refresh the UI to see the yellow "PENDING" badges turn green!
        fetchRecords();
    };

    const renderItem = ({ item }: { item: any }) => (
        <View style={styles.card}>
            <Text style={styles.empId}>Employee: {item.employee_id}</Text>
            <Text>Date: {new Date(item.timestamp_unix).toLocaleString()}</Text>
            <Text>Match Score: {(item.face_match_score * 100).toFixed(1)}%</Text>
            <View style={[styles.badge, { backgroundColor: item.sync_status === 'pending' ? '#ffcc00' : '#4caf50' }]}>
                <Text style={styles.badgeText}>{item.sync_status.toUpperCase()}</Text>
            </View>
        </View>
    );

    return (
        <View style={styles.container}>
            <Button title="← Back to Camera" onPress={onBack} color="#333" />

            <View style={styles.header}>
                <Text style={styles.title}>Local Attendance</Text>
                {isSyncing ? (
                    <ActivityIndicator size="small" color="#0066cc" />
                ) : (
                    <Button title="☁️ Sync to AWS" onPress={handleSync} color="#0066cc" />
                )}
            </View>

            {records.length === 0 ? (
                <Text style={styles.empty}>No pending records. You are all caught up!</Text>
            ) : (
                <FlatList
                    data={records}
                    keyExtractor={(item) => item.id}
                    renderItem={renderItem}
                />
            )}
        </View>
    );
};

const styles = StyleSheet.create({
    container: { flex: 1, padding: 20, backgroundColor: '#f5f5f5' },
    header: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginVertical: 15 },
    title: { fontSize: 22, fontWeight: 'bold', color: '#333' },
    card: { backgroundColor: '#fff', padding: 15, borderRadius: 8, marginBottom: 10, borderWidth: 1, borderColor: '#ddd' },
    empId: { fontSize: 16, fontWeight: 'bold', marginBottom: 5 },
    empty: { textAlign: 'center', marginTop: 50, fontSize: 16, color: '#666' },
    badge: { alignSelf: 'flex-start', paddingHorizontal: 10, paddingVertical: 4, borderRadius: 12, marginTop: 10 },
    badgeText: { fontSize: 12, fontWeight: 'bold', color: '#000' }
});