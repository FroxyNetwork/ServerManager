package com.froxynetwork.servermanager;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.froxynetwork.froxynetwork.network.NetworkManager;
import com.froxynetwork.servermanager.command.CommandManager;
import com.froxynetwork.servermanager.docker.DockerManager;
import com.froxynetwork.servermanager.server.ServerManager;
import com.froxynetwork.servermanager.server.config.ServerConfigManager;
import com.froxynetwork.servermanager.websocket.ServerWebSocketManager;

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
	@Getter
	private ServerWebSocketManager serverWebSocketManager;
	@Getter
	private DockerManager dockerManager;

	public Main(String[] args) {
		try {
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
			initializeServerWebSocket();
			initializeDockerManager();
			initializeCommands();
			LOG.info("All initialized");
		} catch (Exception ex) {
			LOG.error("ERROR: ", ex);
			System.exit(1);
		}
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
		String lPort = p.getProperty("lowPort");
		String hPort = p.getProperty("highPort");
		if (lPort == null || "".equalsIgnoreCase(lPort.trim())) {
			LOG.error("Incorrect config ! (lowPort is empty)");
			System.exit(1);
		}
		if (hPort == null || "".equalsIgnoreCase(hPort.trim())) {
			LOG.error("Incorrect config ! (highPort is empty)");
			System.exit(1);
		}
		LOG.info("lowPort = {}, highPort = {}", lPort, hPort);
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
		serverManager = new ServerManager(this, lowPort, highPort);
		LOG.info("ServerManager initialized");
	}

	private void initializeCommands() {
		LOG.info("Initializing CommandManager");
		commandManager = new CommandManager(this);
		LOG.info("CommandManager initialized");
	}

	private void initializeServerConfig() {
		LOG.info("Initializing ServerConfigManager");
		serverConfigManager = new ServerConfigManager(this);
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

	private void initializeServerWebSocket() {
		LOG.info("Initializing ServerWebSocketManager");
		String websocketUrl = p.getProperty("websocket_url");
		String strWebsocketPort = p.getProperty("websocket_port");
		if (websocketUrl == null || "".equalsIgnoreCase(websocketUrl.trim())) {
			LOG.error("websocketUrl is empty");
			LOG.info("Using default websocketUrl (localhost)");
			websocketUrl = "localhost";
		}
		int websocketPort = 35565;
		try {
			websocketPort = Integer.parseInt(strWebsocketPort);
		} catch (NumberFormatException ex) {
			LOG.error("websocketPort is not a number: {}", strWebsocketPort);
			LOG.info("Using default websocketPort ({})", websocketPort);
		}
		serverWebSocketManager = new ServerWebSocketManager(this, websocketUrl, websocketPort, networkManager);
		serverWebSocketManager.start();
		LOG.info("ServerWebSocketManager initialized");
	}

	private void initializeDockerManager() {
		LOG.info("Initializing DockerManager");
		String host = p.getProperty("docker_daemon_url");
		if (host == null || "".equalsIgnoreCase(host))
			host = "tcp://localhost:2376";
		String certPath = p.getProperty("docker_daemon_path");
		if (certPath == null || "".equalsIgnoreCase(certPath)) {
			LOG.error("Certificat path not found !");
			System.exit(1);
		}
		try {
			dockerManager = new DockerManager();
			dockerManager.initializeConnection(host, certPath);
		} catch (Exception ex) {
			LOG.error("An error has occured: ", ex);
			System.exit(1);
		}
		LOG.info("DockerManager initialized");
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
