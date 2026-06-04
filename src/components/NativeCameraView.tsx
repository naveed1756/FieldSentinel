import { requireNativeComponent, ViewStyle } from 'react-native';

interface NativeCameraViewProps {
  style?: ViewStyle;
}

const NativeCameraView = requireNativeComponent<NativeCameraViewProps>('CameraPreviewView');

export default NativeCameraView;