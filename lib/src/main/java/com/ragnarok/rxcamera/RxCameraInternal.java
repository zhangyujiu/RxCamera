package com.ragnarok.rxcamera;

import android.content.Context;
import android.hardware.Camera;
import android.util.Log;
import android.view.SurfaceView;
import android.view.TextureView;

import com.ragnarok.rxcamera.config.CameraUtil;
import com.ragnarok.rxcamera.config.RxCameraConfig;
import com.ragnarok.rxcamera.error.BindSurfaceFailedException;
import com.ragnarok.rxcamera.error.OpenCameraExecption;
import com.ragnarok.rxcamera.error.OpenCameraFailedReason;
import com.ragnarok.rxcamera.error.StartPreviewFailedException;
import com.ragnarok.rxcamera.SurfaceCallback;

import java.util.List;

/**
 * Created by ragnarok on 15/11/13.
 * the internal logic of camera
 */
public class RxCameraInternal implements SurfaceCallback.SurfaceListener {

    private static final String TAG = "RxCamera.CameraInternal";

    // the native camera object
    private Camera camera;

    // the camera config
    private RxCameraConfig cameraConfig;

    private Context context;

    private boolean isBindSurface = false;
    private boolean isOpenCamera = false;

    private SurfaceView bindSurfaceView;
    private TextureView bindTextureView;

    private SurfaceCallback surfaceCallback = new SurfaceCallback();

    private boolean isSurfaceAvailable = false;
    private boolean isNeedStartPreviewLater = false;

    public void setConfig(RxCameraConfig config) {
        this.cameraConfig = config;
    }

    public RxCameraConfig getConfig() {
        return this.cameraConfig;
    }

    public void setContext(Context context) {
        this.context = context;
    }

    private OpenCameraFailedReason openCameraFailedReason;
    private Throwable openCameraFailedCause;
    public boolean openCameraInternal() {
        reset();
        if (cameraConfig == null) {
            openCameraFailedReason = OpenCameraFailedReason.PARAMETER_ERROR;
            return false;
        }
        // open camera
        try {
            this.camera = Camera.open(cameraConfig.currentCameraId);
        } catch (Exception e) {
            openCameraFailedReason = OpenCameraFailedReason.OPEN_FAILED;
            openCameraFailedCause = e;
            Log.e(TAG, "open camera failed: " + e.getMessage());
            return false;
        }

        Camera.Parameters parameters = null;
        try {
            parameters = camera.getParameters();
        } catch (Exception e) {
            openCameraFailedReason = OpenCameraFailedReason.GET_PARAMETER_FAILED;
            openCameraFailedCause = e;
            Log.e(TAG, "get parameter failed: " + e.getMessage());
        }

        if (parameters == null) {
            openCameraFailedReason = OpenCameraFailedReason.GET_PARAMETER_FAILED;
            return false;
        }

        // set fps
        if (cameraConfig.minPreferPreviewFrameRate != -1 && cameraConfig.maxPreferPreviewFrameRate != -1) {
            try {
                int[] range = CameraUtil.findClosestFpsRange(camera, cameraConfig.minPreferPreviewFrameRate, cameraConfig.maxPreferPreviewFrameRate);
                parameters.setPreviewFpsRange(range[0], range[1]);
            } catch (Exception e) {
                openCameraFailedReason = OpenCameraFailedReason.SET_FPS_FAILED;
                openCameraFailedCause = e;
                Log.e(TAG, "set preview fps range failed: " + e.getMessage());
                return false;
            }
        }
        // set preview size;
        if (cameraConfig.preferPreviewSize != null) {
            try {
                Camera.Size previewSize = CameraUtil.findClosetPreviewSize(camera, cameraConfig.preferPreviewSize);
                parameters.setPreviewSize(previewSize.width, previewSize.height);
            } catch (Exception e) {
                openCameraFailedReason = OpenCameraFailedReason.SET_PREVIEW_SIZE_FAILED;
                openCameraFailedCause = e;
                Log.e(TAG, "set preview size failed: " + e.getMessage());
                return false;
            }
        }

        // set format
        if (cameraConfig.previewFormat != -1) {
            try {
                parameters.setPreviewFormat(cameraConfig.previewFormat);
            } catch (Exception e) {
                openCameraFailedReason = OpenCameraFailedReason.SET_PREVIEW_FORMAT_FAILED;
                openCameraFailedCause = e;
                Log.e(TAG, "set preview format failed: " + e.getMessage());
                return false;
            }
        }

        // set auto focus
        if (cameraConfig.isAutoFocus) {
            try {
                List<String> focusModes = parameters.getSupportedFocusModes();
                if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
                    parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
                } else if (focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                    parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
                }
            } catch (Exception e) {
                Log.e(TAG, "set auto focus failed: " + e.getMessage());
                openCameraFailedReason = OpenCameraFailedReason.SET_AUTO_FOCUS_FAILED;
                openCameraFailedCause = e;
                return false;
            }
        }

        // set all parameters
        try {
            camera.setParameters(parameters);
        } catch (Exception e) {
            openCameraFailedReason = OpenCameraFailedReason.SET_PARAMETER_FAILED;
            openCameraFailedCause = e;
            Log.e(TAG, "set final parameter failed: " + e.getMessage());
            return false;
        }

        // set display orientation
        if (cameraConfig.displayOrientation == -1) {
            cameraConfig.displayOrientation = CameraUtil.getPortraitCamearaDisplayOrientation(context, cameraConfig.currentCameraId, cameraConfig.isFaceCamera);
        }
        try {
            camera.setDisplayOrientation(cameraConfig.displayOrientation);
        } catch (Exception e) {
            openCameraFailedReason = OpenCameraFailedReason.SET_DISPLAY_ORIENTATION_FAILED;
            openCameraFailedCause = e;
            Log.e(TAG, "open camera failed: " + e.getMessage());
            return false;
        }
        isOpenCamera = true;
        return true;
    }

    public OpenCameraExecption openCameraExecption() {
        return new OpenCameraExecption(openCameraFailedReason, openCameraFailedCause);
    }

    public boolean isBindSurface() {
        return isBindSurface;
    }

    public boolean isOpenCamera() {
        return isOpenCamera;
    }

    private boolean installPreviewCallback() {
        return false;
    }

    private String bindSurfaceFailedMessage;
    private Throwable bindSurfaceFailedCause;
    public boolean bindSurfaceInternal(SurfaceView surfaceView) {
        if (camera == null || isBindSurface || surfaceView == null) {
            return false;
        }
        try {
            bindSurfaceView = surfaceView;
            if (cameraConfig.isHandleSurfaceEvent) {
                bindSurfaceView.getHolder().addCallback(surfaceCallback);
            }
            if (surfaceView.getHolder() != null) {
                camera.setPreviewDisplay(surfaceView.getHolder());
            }
            isBindSurface = true;
        } catch (Exception e) {
            bindSurfaceFailedMessage = e.getMessage();
            bindSurfaceFailedCause = e;
            Log.e(TAG, "bindSurface failed: " + e.getMessage());
            return false;
        }
        return true;
    }

    public BindSurfaceFailedException bindSurfaceFailedException() {
        return new BindSurfaceFailedException(bindSurfaceFailedMessage, bindSurfaceFailedCause);
    }

    public boolean bindTextureInternal(TextureView textureView) {
        if (camera == null || isBindSurface || textureView == null) {
            return false;
        }
        try {
            bindTextureView = textureView;
            if (cameraConfig.isHandleSurfaceEvent) {
                bindTextureView.setSurfaceTextureListener(surfaceCallback);
            }
            if (bindTextureView.getSurfaceTexture() != null) {
                camera.setPreviewTexture(bindTextureView.getSurfaceTexture());
            }
            isBindSurface = true;
        } catch (Exception e) {
            bindSurfaceFailedMessage = e.getMessage();
            bindSurfaceFailedCause = e;
            Log.e(TAG, "bindSurfaceTexture failed: " + e.getMessage());
            return false;
        }
        return true;
    }

    private String previewFailedMessage;
    private Throwable previewFailedCause;
    public boolean startPreviewInternal() {
        if (camera == null || !isBindSurface) {
            return false;
        }
        try {
            if (bindTextureView != null && bindTextureView.isAvailable()) {
                isSurfaceAvailable = true;
            }
            if (!isSurfaceAvailable && cameraConfig.isHandleSurfaceEvent) {
                isNeedStartPreviewLater = true;
                return true;
            }
            camera.startPreview();
        } catch (Exception e) {
            Log.e(TAG, "start preview failed: " + e.getMessage());
            previewFailedMessage = e.getMessage();
            previewFailedCause = e;
            return false;
        }
        return true;
    }

    public StartPreviewFailedException startPreviewFailedException() {
        return new StartPreviewFailedException(previewFailedMessage, previewFailedCause);
    }

    public boolean closeCameraInternal() {
        if (camera == null) {
            return false;
        }
        try {
            camera.setPreviewCallback(null);
            camera.release();
            reset();
        } catch (Exception e) {
            Log.e(TAG, "close camera failed: " + e.getMessage());
            return false;
        }
        return true;
    }

    @Override
    public void onAvailable() {
        if (isNeedStartPreviewLater) {
            try {
                camera.startPreview();
            } catch (Exception e) {
                Log.e(TAG, "onAvailable, start preview failed");
            }
        }
    }

    @Override
    public void onDestroy() {
        isSurfaceAvailable = false;
    }

    private void reset() {
        isBindSurface = false;
        isNeedStartPreviewLater = false;
        isSurfaceAvailable = false;
        openCameraFailedCause = null;
        openCameraFailedReason = null;
        previewFailedCause = null;
        previewFailedMessage = null;
        bindSurfaceFailedCause = null;
        bindSurfaceFailedMessage = null;
    }
}