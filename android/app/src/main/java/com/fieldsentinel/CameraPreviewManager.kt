package com.fieldsentinel

import com.facebook.react.uimanager.SimpleViewManager
import com.facebook.react.uimanager.ThemedReactContext

class CameraPreviewManager : SimpleViewManager<CameraPreviewView>() {

    override fun getName(): String = "CameraPreviewView"

    override fun createViewInstance(context: ThemedReactContext): CameraPreviewView {
        val view = CameraPreviewView(context)
        // Register this view so FaceAuthModule can capture frames from it
        FaceAuthModule.activeCameraView = view
        return view
    }
}