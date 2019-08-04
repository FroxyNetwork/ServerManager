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

public class ZipUtil {
	private static final Logger LOG = LoggerFactory.getLogger(ZipUtil.class);
	private static int BUFFER = 2048;

	private ZipUtil() {
	}

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
