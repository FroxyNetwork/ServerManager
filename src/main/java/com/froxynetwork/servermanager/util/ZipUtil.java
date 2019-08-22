package com.froxynetwork.servermanager.util;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MIT License
 *
 * Copyright (c) 2019 FroxyNetwork
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 * 
 * @author 0ddlyoko
 */
public class ZipUtil {
	private static final Logger LOG = LoggerFactory.getLogger(ZipUtil.class);
	private static int BUFFER = 2048;

	private ZipUtil() {
	}

	/**
	 * Extract a zip file to specific directory
	 * 
	 * @param srcFile
	 *            The zip file to extract
	 * @param destFile
	 *            The destination folder
	 * @throws IOException
	 *             If error occurs
	 * 
	 * @see <a href="https://stackoverflow.com/q/11647362/8008251">Original code</a>
	 */
	public static void unzipAndMove(File srcFile, File destFile) throws IOException {
		LOG.info("Extracting {} to {}", srcFile.getAbsolutePath(), destFile.getAbsolutePath());
		destFile.mkdirs();
		BufferedOutputStream dest = null;
		ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(srcFile)));

		ZipEntry entry;
		while ((entry = zis.getNextEntry()) != null) {
			int count;
			byte data[] = new byte[BUFFER];
			if (entry.isDirectory()) {
				new File(destFile, entry.getName()).mkdirs();
				continue;
			} else {
				int di = entry.getName().lastIndexOf('/');
				if (di != -1) {
					new File(destFile, entry.getName().substring(0, di)).mkdirs();
				}
			}
			FileOutputStream fos = new FileOutputStream(new File(destFile, entry.getName()));

			dest = new BufferedOutputStream(fos);
			while ((count = zis.read(data)) != -1)
				dest.write(data, 0, count);
			dest.flush();
			dest.close();
		}
		zis.close();
	}
}
