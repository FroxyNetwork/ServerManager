package com.froxynetwork.servermanager;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.froxynetwork.froxynetwork.network.NetworkManager;
import com.froxynetwork.servermanager.command.CommandManager;
import com.froxynetwork.servermanager.server.ServerManager;
import com.froxynetwork.servermanager.server.config.ServerConfigManager;

import lombok.Getter;

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
public class Main {

	private final Logger LOG = LoggerFactory.getLogger(getClass());

	private Properties p;

	@Getter
	private NetworkManager networkManager;
	@Getter
	private ServerManager serverManager;
	@Getter
	private CommandManager commandManager;
	@Getter
	private ServerConfigManager serverConfigManager;

	public Main(String[] args) {
		LOG.info("ServerManager initialization");
		if (args == null || args.length != 1) {
			LOG.error("Invalid argument number, please enter correct arguments ! (<propertiesFile>)");
			System.exit(1);
		}
		String properties = args[0];
		File fProperties = new File(properties);
		if (fProperties == null || !fProperties.exists()) {
			LOG.error("Properties file not found ({})", properties);
			System.exit(1);
		}
		if (!fProperties.isFile() || !fProperties.canRead()) {
			LOG.error("Properties file is not a file or we don't have permission to read the properties file ({})",
					properties);
			System.exit(1);
		}
		p = new Properties();
		try {
			p.load(new FileInputStream(fProperties));
		} catch (IOException ex) {
			LOG.error("Error while reading properties file ({})", properties);
			LOG.error("", ex);
			System.exit(1);
		}

		initializeNetwork();
		initializeServer();
		initializeServerConfig();
		initializeCommands();
		LOG.info("All initialized");
	}

	private void initializeNetwork() {
		LOG.info("Initializing NetworkManager");
		String url = p.getProperty("url");
		String clientId = p.getProperty("client_id");
		String clientSecret = p.getProperty("client_secret");
		LOG.info("url = {}, client_id = {}, client_secret = {}", url, clientId,
				clientSecret == null ? "null" : "<hidden>");
		try {
			networkManager = new NetworkManager(url, clientId, clientSecret);
		} catch (Exception ex) {
			LOG.error("An error has occured while initializing NetworkManager: ", ex);
			System.exit(1);
		}
		LOG.info("NetworkManager initialized");
	}

	private void initializeServer() {
		LOG.info("Initializing ServerManager");
		String from = p.getProperty("srvDir");
		String to = p.getProperty("toDir");
		String lPort = p.getProperty("lowPort");
		String hPort = p.getProperty("highPort");
		String dDirectories = p.getProperty("deleteDirectories");
		String dFiles = p.getProperty("deleteFiles");
		if (from == null || "".equalsIgnoreCase(from.trim())) {
			LOG.error("Incorrect config ! (srvDir is empty)");
			System.exit(1);
		}
		if (to == null || "".equalsIgnoreCase(to.trim())) {
			LOG.error("Incorrect config ! (toDir is empty)");
			System.exit(1);
		}
		if (lPort == null || "".equalsIgnoreCase(lPort.trim())) {
			LOG.error("Incorrect config ! (lowPort is empty)");
			System.exit(1);
		}
		if (hPort == null || "".equalsIgnoreCase(hPort.trim())) {
			LOG.error("Incorrect config ! (highPort is empty)");
			System.exit(1);
		}
		LOG.info("srvDir = {}, toDir = {}, lowPort = {}, highPort = {}, deleteDirectories = {}, deleteFiles = {}", from,
				to, lPort, hPort, dDirectories, dFiles);
		File srvDir = new File(from);
		File toDir = new File(to);
		if (from == null || !srvDir.isDirectory()) {
			LOG.error("srvDir is not a directory ({})", from);
			System.exit(1);
		}
		if (to == null || !toDir.isDirectory()) {
			LOG.error("toDir is not a directory ({})", from);
			System.exit(1);
		}
		int lowPort = 25566;
		try {
			lowPort = Integer.parseInt(lPort);
		} catch (NumberFormatException ex) {
			LOG.error("lowPort is not a number: {}", lPort);
			LOG.info("Using default lowPort ({})", lowPort);
		}
		int highPort = lowPort + 100;
		try {
			highPort = Integer.parseInt(hPort);
		} catch (NumberFormatException ex) {
			LOG.error("highPort is not a number: {}", hPort);
			LOG.info("Using default highPort ({})", highPort);
		}
		boolean deleteDirectories = false;
		if ("true".equalsIgnoreCase(dDirectories))
			deleteDirectories = true;
		boolean deleteFiles = false;
		if ("true".equalsIgnoreCase(dFiles))
			deleteFiles = true;
		serverManager = new ServerManager(this, srvDir, toDir, lowPort, highPort, deleteDirectories, deleteFiles);
		LOG.info("ServerManager initialized");
	}

	private void initializeCommands() {
		LOG.info("Initializing CommandManager");
		commandManager = new CommandManager(this);
		LOG.info("CommandManager initialized");
	}

	private void initializeServerConfig() {
		LOG.info("Initializing ServerConfigManager");
		String strDownloadThread = p.getProperty("downloadThread");
		if (strDownloadThread == null || "".equalsIgnoreCase(strDownloadThread.trim())) {
			LOG.error("Incorrect config ! (downloadThread is empty)");
			System.exit(1);
		}
		int downloadThread = 5;
		try {
			downloadThread = Integer.parseInt(strDownloadThread);
		} catch (NumberFormatException ex) {
			LOG.error("downloadThread is not a number: {}", strDownloadThread);
			LOG.info("Using default downloadThread ({})", downloadThread);
		}
		serverConfigManager = new ServerConfigManager(this, downloadThread);
		try {
			serverConfigManager.reload(() -> {
				LOG.info("Reloaded !");
			});
		} catch (Exception ex) {
			LOG.error("An error has occured while initializing ServerConfigManager: ", ex);
			System.exit(1);
		}
		LOG.info("ServerConfigManager initialized");
	}

	public static void main(String[] args) {
		Main main = new Main(args);
		// main.getServerManager().openServer("Koth", srv -> {
		// System.out.println("Done: " + srv);
		// }, () -> {
		// System.out.println("ERROR");
		// });
		// main.getServerManager().openServer("Koth", srv -> {
		// System.out.println("Done: " + srv);
		// }, () -> {
		// System.out.println("ERROR");
		// });
	}
}
