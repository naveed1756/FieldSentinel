import DatabaseService from './DatabaseService';
import NetInfo from '@react-native-community/netinfo';

class SyncService {
    private isSyncing = false;

    async syncNow() {
        if (this.isSyncing) return { success: false, message: 'Already syncing...' };
        const netState = await NetInfo.fetch();
        const isConnected = netState.isConnected && netState.isInternetReachable;
        if (!isConnected) {
            console.log('Offline. Sync postponed.');
            return { success: false, message: 'No internet connection.' };
        }
        this.isSyncing = true;

        try {
            // 1. Fetch up to 50 pending records from SQLite
            const pending = await DatabaseService.getPendingRecords(50);

            if (pending.length === 0) {
                this.isSyncing = false;
                return { success: true, message: 'All records are already synced!' };
            }

            // 3. Mock the AWS API POST Request
            console.log(`Uploading ${pending.length} records to AWS...`);
            /* In production, this is where you run:
              const response = await fetch(AWS_ENDPOINT, { method: 'POST', body: JSON.stringify({ records: pending }) });
            */

            // Simulate a 1.5 second network delay
            await new Promise(resolve => setTimeout(resolve, 1500));

            // 4. Handle Success
            const confirmedIds = pending.map((r: any) => r.id);
            const now = Date.now();

            // Mark as synced in SQLite
            await DatabaseService.markSynced(confirmedIds, now);

            // Purge records synced > 7 days ago
            await DatabaseService.purgeSyncedOlderThan(now - 7 * 86400 * 1000);

            this.isSyncing = false;
            return { success: true, message: `Successfully synced ${pending.length} records to AWS! ☁️` };

        } catch (err) {
            console.error('Sync error:', err);
            this.isSyncing = false;
            return { success: false, message: 'Sync crashed unexpectedly.' };
        }
    }
}

export default new SyncService();