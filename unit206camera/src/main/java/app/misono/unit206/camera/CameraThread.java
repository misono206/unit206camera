/*
 * Copyright 2020-2022 Atelier Misono, Inc. @ https://misono.app/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.misono.unit206.camera;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Point;
import android.hardware.Camera;
import android.os.Message;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.WorkerThread;

import app.misono.unit206.debug.Log2;
import app.misono.unit206.media.MessageThread;
import app.misono.unit206.task.Taskz;

import java.io.IOException;
import java.util.List;

@RequiresApi(17)
public class CameraThread extends MessageThread {
	private static final String TAG = "CameraThread";

	private static final int MSG_QUIT = 0;
	private static final int MSG_OPEN = 1;
	private static final int MSG_CLOSE = 2;

	private final Callback callback;
	private final int rotate;
	private final int idCamera;

	private RuntimeException exception;
	private SimpleSurface surface;
	private SyncParams syncParams;
	private RsYuvToRgb yuv2rgb;
	private Camera camera;

	public interface Callback {
		@WorkerThread
		void callback(@NonNull Bitmap bitmap);
	}

	@RequiresApi(17)
	public CameraThread(
		@NonNull Context context,
		int idCamera,
		int width,
		int height,
		@Nullable String focus,
		@Nullable String zoom,
		@Nullable String effect,
		@Nullable String scene,
		int rotate,
		@Nullable Runnable onError,
		@NonNull Callback callback
	) {
		super();
		this.idCamera = idCamera;
		this.callback = callback;
		this.rotate = rotate;
		sendMessageSync(MSG_OPEN);
		yuv2rgb	= new RsYuvToRgb(context);
		if (camera == null) {
			log("camera open fail...");
			Taskz.printStackTrace2(exception);
			throw new RuntimeException("Camera open failed...");
		}
		camera.setErrorCallback((error, camera) -> {
			log("Camera.onError:" + error);
			if (onError != null) {
				onError.run();
			}
		});

		Camera.CameraInfo info;
		Camera.Parameters cp;
		Camera.Size csize;
		Bitmap bitmap;
		Matrix jpgMatrix;
		Point cameraSize;

		camera.stopPreview();
		camera.setPreviewCallback(null);

		cp = camera.getParameters();
		setPreviewSizeSafe(cp, width, height);
		setFocusSafe(cp, focus);
		setZoomSafe(cp, zoom);
		setEffectSafe(cp, effect);
		setSceneSafe(cp, scene);
		camera.setParameters(cp);

		Camera.Parameters cparams = camera.getParameters();
		csize = cparams.getPreviewSize();
		cameraSize = new Point(csize.width, csize.height);
		Log.e(TAG, "setCameraParams:" + csize.width + "x" + csize.height);

		bitmap = Bitmap.createBitmap(cameraSize.x, cameraSize.y, Bitmap.Config.ARGB_8888);
		syncParams = new SyncParams(bitmap, cameraSize);

		camera.setPreviewCallback(cbPreview);
		camera.startPreview();
	}

	private void setPreviewSizeSafe(@NonNull Camera.Parameters cp, int width, int height) {
		List<Camera.Size> sizes = cp.getSupportedPreviewSizes();
		int n = sizes.size();
		int i;
		for (i = 0; i < n; i++) {
			Camera.Size size = sizes.get(i);
			if (size.width == width && size.height == height) break;
		}
		if (i == n) {
			Camera.Size size = sizes.get(0);
			width = size.width;
			height = size.height;
		}
		cp.setPreviewSize(width, height);
	}

	private void setZoomSafe(@NonNull Camera.Parameters cp, @Nullable String zoom) {
		if (zoom != null && cp.isZoomSupported()) {
			try {
				int iZoom = Integer.parseInt(zoom);
				List<Integer> zooms = cp.getZoomRatios();
				if (zooms != null) {
					int n = zooms.size();
					for (int i = 0; i < n; i++) {
						int iz = zooms.get(i);
						if (iz == iZoom) {
							cp.setZoom(i);
							break;
						}
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	private void setFocusSafe(@NonNull Camera.Parameters cp, @Nullable String focus) {
		log("setFocusSafe:" + focus);
		if (focus != null) {
			List<String> focuses = cp.getSupportedFocusModes();
			if (focuses != null) {
				for (String f : focuses) {
					if (f.contentEquals(focus)) {
						cp.setFocusMode(f);
						log("cp.setFocusMode:" + focus);
						break;
					}
				}
			}
		}
	}

	private void setEffectSafe(@NonNull Camera.Parameters cp, @Nullable String effect) {
		if (effect != null) {
			List<String> effects = cp.getSupportedColorEffects();
			if (effects != null) {
				for (String e : effects) {
					if (e.contentEquals(effect)) {
						cp.setColorEffect(e);
						break;
					}
				}
			}
		}
	}

	private void setSceneSafe(@NonNull Camera.Parameters cp, @Nullable String scene) {
		if (scene != null) {
			List<String> scenes = cp.getSupportedSceneModes();
			if (scenes != null) {
				for (String s : scenes) {
					if (s.contentEquals(scene)) {
						cp.setSceneMode(s);
						break;
					}
				}
			}
		}
	}

	private final Camera.PreviewCallback cbPreview = new Camera.PreviewCallback() {
		@Override
		public void onPreviewFrame(byte[] data, Camera cam) {
			SyncParams sp;
			Bitmap b;
			Point cSize;

			if (syncParams == null || data == null) {
				return;        //	for SO-02E. Sometimes data is null...
			}
			//					if (1 <= count()) return;			//	why??

			sp = syncParams;
			b = sp.bitmap;
			cSize = sp.cameraSize;
			if (cSize.x * cSize.y * 3 / 2 != data.length) {
				Log.e(TAG, "preview size is not match (" + cSize.x + "x" + cSize.y + "x1.5 != " + data.length + ") !!!");
				return;
			}

			try {
				yuv2rgb.yuv2bitmap(b, data, cSize.x, cSize.y);
				Matrix matrix = new Matrix();
				matrix.setRotate(rotate);
				Bitmap bitmap = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), matrix, false);
				callback.callback(bitmap);
			} catch (OutOfMemoryError e) {
				e.printStackTrace();
			}
		}
	};

	public void closeCamera() {
		sendMessageSync(MSG_CLOSE);
		if (yuv2rgb != null) {
			yuv2rgb.close();
			yuv2rgb = null;
		}
		quitSync(MSG_QUIT, 1000L);
	}

	public void forceClose() {
		if (camera != null) {
			camera.stopPreview();
			camera.setPreviewCallback(null);
			resetPreviewTexture();
			camera.release();
			camera = null;
		}
	}

	@Override
	public void handleMessage(Message msg) {
		switch (msg.what) {
		case MSG_OPEN:
			openCameraInternal();
			break;
		case MSG_CLOSE:
			if (camera != null) {
				camera.stopPreview();
				camera.setPreviewCallback(null);
				resetPreviewTexture();
				camera.release();
				camera = null;
			}
			break;
		case MSG_QUIT:
			quit();
			break;
		}
	}

	@Override
	public void done() {
	}

	private void openCameraInternal() {
		exception = null;
		try {
			camera = openCamera9();
			setPreviewTexture();
		} catch (RuntimeException e) {
			exception = e;
		}
	}

	private Camera openCamera9() {
		return Camera.open(idCamera);
	}

	private void setPreviewTexture() {
		surface	= new SimpleSurface();
		try {
			camera.setPreviewTexture(surface.getSurfaceTexture());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void resetPreviewTexture() {
		try {
			camera.setPreviewTexture(null);
		} catch (IOException e) {
			//				e.printStackTrace();
		}
	}

	private RuntimeException getException() {
		return exception;
	}

	private void log(@NonNull String msg) {
		Log2.e(TAG, msg);
	}

	private static class SyncParams {
		private final Bitmap bitmap;
		private final Point cameraSize;

		private SyncParams(Bitmap bitmap, Point cameraSize) {
			this.bitmap = bitmap;
			this.cameraSize = cameraSize;
		}
	}

}
