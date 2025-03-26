package com.termux.api.apis;  
  
import android.content.Context;  
import android.content.Intent;  
import android.hardware.Camera;  
import android.hardware.camera2.CameraAccessException;  
import android.hardware.camera2.CameraCharacteristics;  
import android.hardware.camera2.CameraManager;  
import android.util.JsonWriter;  
  
import com.termux.api.TermuxApiReceiver;  
import com.termux.api.util.ResultReturner;  
import com.termux.shared.logger.Logger;  
  
public class TorchAPI {  
    private static Camera legacyCamera;  
    private static final String LOG_TAG = "TorchAPI";  
  
    public static void onReceive(TermuxApiReceiver apiReceiver, final Context context, final Intent intent) {  
        Logger.logDebug(LOG_TAG, "onReceive");  
        boolean enabled = intent.getBooleanExtra("enabled", false);  
        String torchIdArg = intent.getStringExtra("torch_id");  
        if (torchIdArg == null || torchIdArg.trim().isEmpty()) torchIdArg = "0";  
        boolean success = toggleTorch(context, enabled, torchIdArg, apiReceiver, intent);  
        if (success) ResultReturner.noteDone(apiReceiver, intent);  
    }  
  
    private static boolean toggleTorch(final Context context, boolean enabled, String torchIdArg,  
                                       final TermuxApiReceiver apiReceiver, final Intent intent) {  
        try {  
            final CameraManager cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);  
            if (cameraManager == null) {  
                if ("0".equals(torchIdArg.trim())) {  
                    legacyToggleTorch(enabled);  
                    return true;  
                } else {  
                    ResultReturner.returnData(apiReceiver, intent, new ResultReturner.ResultJsonWriter() {  
                        @Override  
                        public void writeJson(JsonWriter out) throws Exception {  
                            out.beginObject();  
                            out.name("error").value("Invalid flash id: device does not support multiple torches");  
                            out.endObject();  
                            out.flush();  
                            out.close();  
                        }  
                    });  
                    return false;  
                }  
            }  
  
            String[] requestedTorchIds = torchIdArg.split(",");  
            for (int i = 0; i < requestedTorchIds.length; i++) {  
                requestedTorchIds[i] = requestedTorchIds[i].trim();  
            }  
  
            String[] availableCameraIds = cameraManager.getCameraIdList();  
            for (final String reqId : requestedTorchIds) {  
                boolean found = false;  
                for (String availableId : availableCameraIds) {  
                    if (availableId.equals(reqId)) {  
                        Boolean flashAvailable = cameraManager.getCameraCharacteristics(availableId)  
                                .get(CameraCharacteristics.FLASH_INFO_AVAILABLE);  
                        if (flashAvailable != null && flashAvailable) {  
                            found = true;  
                            break;  
                        }  
                    }  
                }  
                if (!found) {  
                    ResultReturner.returnData(apiReceiver, intent, new ResultReturner.ResultJsonWriter() {  
                        @Override  
                        public void writeJson(JsonWriter out) throws Exception {  
                            out.beginObject();  
                            out.name("error").value("ID " + reqId + " do not support torch");  
                            out.endObject();  
                            out.flush();  
                            out.close();  
                        }  
                    });  
                    return false;  
                }  
            }  
  
            for (String id : requestedTorchIds) {  
                cameraManager.setTorchMode(id, enabled);  
            }  
  
        } catch (final CameraAccessException e) {  
            Logger.logStackTraceWithMessage(LOG_TAG, "Error toggling torch", e);  
            ResultReturner.returnData(apiReceiver, intent, new ResultReturner.ResultJsonWriter() {  
                @Override  
                public void writeJson(JsonWriter out) throws Exception {  
                    out.beginObject();  
                    out.name("error").value("Error toggling torch: " + e.getMessage());  
                    out.endObject();  
                    out.flush();  
                    out.close();  
                }  
            });  
            return false;  
        }  
        return true;  
    }  
  
    private static void legacyToggleTorch(boolean enabled) {  
        Logger.logInfo(LOG_TAG, "Using legacy camera API to toggle torch");  
        if (legacyCamera == null) {  
            legacyCamera = Camera.open();  
        }  
        Camera.Parameters params = legacyCamera.getParameters();  
        if (enabled) {  
            params.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);  
            legacyCamera.setParameters(params);  
            legacyCamera.startPreview();  
        } else {  
            legacyCamera.stopPreview();  
            legacyCamera.release();  
            legacyCamera = null;  
        }  
    }  
}
