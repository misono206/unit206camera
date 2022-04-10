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

import android.annotation.TargetApi;
import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.os.Build;

import java.io.Closeable;

/**
 *	Provies a dummy SurfaceTexture for Camera preview.
 *
 *	@version	2016.10.18
 *	@author		ara
 *
 */
public final class SimpleSurface implements Closeable {
	private final SurfaceTexture st;
	private final int[] textures;

	public SimpleSurface() {
		textures = new int[1];
		GLES20.glGenTextures(1, textures, 0);
		st = new SurfaceTexture(textures[0]);
	}

	@Override
	public void close() {
		st.release();
		GLES20.glDeleteTextures(1, textures, 0);
	}

	public SurfaceTexture getSurfaceTexture() {
		return st;
	}
}
