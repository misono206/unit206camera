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
import android.renderscript.Allocation;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicYuvToRGB;
import android.renderscript.Type;

import androidx.annotation.RequiresApi;

import java.io.Closeable;
import java.nio.ByteBuffer;

/**
 *	Provides a ScriptIntrinsicYuvToRGB class.
 *	This class is not thread-safe.
 *
 *	@version	2019.03.02
 *	@author		ara
 *
 */
@RequiresApi(17)
public final class RsYuvToRgb implements Closeable {
	private ScriptIntrinsicYuvToRGB	script;
	private RenderScript			rs;
	private Allocation				in, out;
	private ByteBuffer				buf;
	private byte[]					rgba;
	private int						saveLength, saveWidth, saveHeight, saveSize;

	public RsYuvToRgb(Context ctx) {
		create17(ctx);
	}

	@RequiresApi(17)
	private void create17(Context ctx) {
		rs = RenderScript.create(ctx);
		script = ScriptIntrinsicYuvToRGB.create(rs, android.renderscript.Element.U8_4(rs));
	}

	public byte[] yuv2rgb(byte[] data, int width, int height) {
		yuv2rgb17(data, width, height);

		return rgba;
	}

	public void yuv2bitmap(Bitmap bitmap, byte[] data, int width, int height) {
		int	size;

		yuv2rgb(data, width, height);
		size = width * height * 4;
		if (buf == null || saveSize != size) {
			saveSize = size;
			buf = ByteBuffer.allocate(size);
		}
		buf.clear();
		buf.put(rgba);
		buf.rewind();
		bitmap.copyPixelsFromBuffer(buf);
	}

	@RequiresApi(17)
	private void yuv2rgb17(byte[] data, int width, int height) {
		android.renderscript.Type	yuvType, rgbaType;

		if (saveLength != data.length) {
			saveLength = data.length;
			yuvType = new Type.Builder(rs, android.renderscript.Element.U8(rs)).setX(data.length).create();
			if (in != null) in.destroy();
			in = Allocation.createTyped(rs, yuvType, android.renderscript.Allocation.USAGE_SCRIPT);
		}
		if (saveWidth != width || saveHeight != height) {
			saveWidth = width;
			saveHeight = height;
			rgbaType = new Type.Builder(rs, android.renderscript.Element.RGBA_8888(rs)).setX(width).setY(height).create();
			if (out != null) out.destroy();
			out = Allocation.createTyped(rs, rgbaType, android.renderscript.Allocation.USAGE_SCRIPT);
			rgba = new byte[width * height * 4];
		}
		in.copyFrom(data);
		script.setInput(in);
		script.forEach(out);
		out.copyTo(rgba);
	}

	@Override
	protected void finalize() throws Throwable {
		close();
		super.finalize();
	}

	/**
	 *	thread unsafe...
	 */
	@Override
	public void close() {
		destroy17();
	}

	@RequiresApi(17)
	private void destroy17() {
		if (in != null) {
			in.destroy();
			in = null;
		}
		if (out != null) {
			out.destroy();
			out = null;
		}
		if (script != null) {
			script.destroy();
			script = null;
		}
		if (rs != null) {
			rs.destroy();
			rs = null;
		}
	}

}
