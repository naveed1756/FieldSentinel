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
        return await FaceAuthModule.captureFrame();
    },

    isEnrolled: async (employeeId: string): Promise<boolean> => {
        return await FaceAuthModule.isEnrolled(employeeId);
    },

    enroll: async (employeeId: string, base64Frames: string[]): Promise<boolean> => {
        const result = await FaceAuthModule.enroll(employeeId, base64Frames);
        return result === 'ENROLLED';
    },

    authenticate: async (
        employeeId: string,
        base64Frame: string,
        detectionConfidence: number
    ): Promise<AuthResult> => {
        return await FaceAuthModule.authenticate(employeeId, base64Frame, detectionConfidence);
    },

    startBlinkChallenge: async (): Promise<string> => {
        return await FaceAuthModule.startBlinkChallenge();
    },

    feedEAR: async (ear: number): Promise<string> => {
        return await FaceAuthModule.feedEAR(ear);
    },
};