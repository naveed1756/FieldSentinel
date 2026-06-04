import { NativeModules } from 'react-native';

const { FaceAuthModule } = NativeModules;

export interface AuthResult {
    success: boolean;
    authResult?: string;
    faceMatchScore?: number;
    antispoofScore?: number;
    livenessMethod?: string;
    abortStage?: string;
    driftUpdated?: boolean;
    errorMessage?: string;
}

export const FaceAuthBridge = {
    captureFrame: async (): Promise<string> => {
        if (!FaceAuthModule) {
            console.log('[MOCK] captureFrame — returning empty string');
            return '';
        }
        return await FaceAuthModule.captureFrame();
    },

    isEnrolled: async (employeeId: string): Promise<boolean> => {
        if (!FaceAuthModule) return true; // mock
        return await FaceAuthModule.isEnrolled(employeeId);
    },

    enroll: async (employeeId: string, base64Frames: string[]): Promise<boolean> => {
        if (!FaceAuthModule) {
            console.log('[MOCK] Enroll called — returning success');
            return true;
        }
        const result = await FaceAuthModule.enroll(employeeId, base64Frames);
        return result === 'ENROLLED';
    },

    authenticate: async (
        employeeId: string,
        base64Frame: string,
        detectionConfidence: number
    ): Promise<AuthResult> => {
        if (!FaceAuthModule) {
            console.log('[MOCK] Authenticate called — returning mock success');
            // Mock a realistic successful result for UI testing
            return {
                success: true,
                authResult: 'SUCCESS',
                faceMatchScore: 0.91,
                antispoofScore: 0.94,
                livenessMethod: 'skipped_high_conf',
                abortStage: '',
                driftUpdated: false,
            };
        }
        return await FaceAuthModule.authenticate(employeeId, base64Frame, detectionConfidence);
    },

    startBlinkChallenge: async (): Promise<string> => {
        if (!FaceAuthModule) return 'STARTED';
        return await FaceAuthModule.startBlinkChallenge();
    },

    feedEAR: async (ear: number): Promise<string> => {
        if (!FaceAuthModule) return 'CONFIRMED';
        return await FaceAuthModule.feedEAR(ear);
    },
};