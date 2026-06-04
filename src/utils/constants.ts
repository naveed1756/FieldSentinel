export const THRESHOLDS = {
  // Face detection
  FACE_DETECT_MIN: 0.60,

  // Passive anti-spoofing
  ANTISPOOF_REJECT: 0.40,
  ANTISPOOF_SKIP_BLINK: 0.80,

  // Blink detection
  EAR_BLINK_THRESHOLD: 0.20,
  EAR_CONSECUTIVE_FRAMES: 2,
  BLINK_TIMEOUT_MS: 10000,

  // Face recognition
  RECOGNITION_MATCH: 0.70,

  // Drift detection
  DRIFT_UPDATE_MIN_SCORE: 0.85,
  DRIFT_ALPHA: 0.10,
};

export const AWS_ENDPOINT = 'https://your-api-gateway-url.amazonaws.com/prod/attendance';

export const DB_NAME = 'fieldsentinel.db';

export const EMBEDDING_SIZE = 128;