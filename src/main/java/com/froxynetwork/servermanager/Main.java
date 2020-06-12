package com.froxynetwork.servermanager;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.froxynetwork.froxynetwork.network.NetworkManager;
import com.froxynetwork.servermanager.command.CommandManager;
import com.froxynetwork.servermanager.scheduler.Scheduler;
import com.froxynetwork.servermanager.server.ServerManager;
import com.froxynetwork.servermanager.server.config.ServerConfigManager;
import com.froxynetwork.servermanager.server.config.ServerVps;
import com.froxynetwork.servermanager.websocket.WebSocketManager;

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

	private static Main INSTANCE;

	private Properties p;

	private String id;
	private String ip;
	private ServerVps serverVps;

	@Getter
	private NetworkManager networkManager;
	@Getter
	private ServerManager serverManager;
	@Getter
	private CommandManager commandManager;
	@Getter
	private ServerConfigManager serverConfigManager;
	@Getter
	private WebSocketManager webSocketManager;

	public Main(String[] args) {
		INSTANCE = this;
		try {
			LOG.info("ServerManager initialization");
			if (args == null || args.length != 1) {
				LOG.error("Invalid argument number, please enter correct arguments ! (<propertiesFile>)");
				System.exit(1);
				return;
			}
			String properties = args[0];
			File fProperties = new File(properties);
			if (fProperties == null || !fProperties.exists()) {
				LOG.error("Properties file not found ({})", properties);
				System.exit(1);
				return;
			}
			if (!fProperties.isFile() || !fProperties.canRead()) {
				LOG.error("Properties file is not a file or we don't have permission to read the properties file ({})",
						properties);
				System.exit(1);
				return;
			}
			p = new Properties();
			try {
				p.load(new FileInputStream(fProperties));
			} catch (IOException ex) {
				LOG.error("Error while reading properties file ({})", properties);
				LOG.error("", ex);
				System.exit(1);
				return;
			}

			id = p.getProperty("id");
			if (id == null || "".equalsIgnoreCase(id.trim())) {
				LOG.error("Id not found !");
				System.exit(1);
				return;
			}

			ip = p.getProperty("ip");
			if (ip == null || "".equalsIgnoreCase(ip.trim())) {
				LOG.error("Ip not found !");
				System.exit(1);
				return;
			}

			initializeNetwork();
			initializeServerConfig(() -> {
				// Retrieve VPS information
				serverVps = serverConfigManager.getVps(id);
				if (serverVps == null) {
					LOG.error("Cannot find vps informations for vps {}", id);
					System.exit(1);
					return;
				}
				// Initialize Servers once ServerConfig is initialized
				initializeServer();
				initializeWebSocket();
				initializeCommands();
				LOG.info("All initialized");
			});
		} catch (Exception ex) {
			LOG.error("ERROR: ", ex);
			System.exit(1);
			return;
		}
	}

	private void initializeNetwork() {
		LOG.info("Initializing NetworkManager");
		String url = p.getProperty("url");
		String clientSecret = p.getProperty("client_secret");
		LOG.info("url = {}, client_id = {}, client_secret = {}", url, id, clientSecret == null ? "null" : "<hidden>");
		try {
			networkManager = new NetworkManager(url, id, clientSecret);
		} catch (Exception ex) {
			LOG.error("An error has occured while initializing NetworkManager: ", ex);
			System.exit(1);
			return;
		}
		LOG.info("NetworkManager initialized");
	}

	private void initializeServerConfig(Runnable then) {
		LOG.info("Initializing ServerConfigManager");
		serverConfigManager = new ServerConfigManager();
		try {
			serverConfigManager.reload(() -> {
				LOG.info("ServerConfigManager initialized");
				then.run();
			});
		} catch (Exception ex) {
			LOG.error("An error has occured while initializing ServerConfigManager: ", ex);
			System.exit(1);
			return;
		}
	}

	private void initializeServer() {
		LOG.info("Initializing ServerManager");
		String lPort = p.getProperty("low_port");
		String hPort = p.getProperty("high_port");
		String websocketCore = p.getProperty("websocket_core");
		String scriptStart = p.getProperty("script_start");
		String scriptStop = p.getProperty("script_stop");
		if (lPort == null || "".equalsIgnoreCase(lPort.trim())) {
			LOG.error("Incorrect config ! (lowPort is empty)");
			System.exit(1);
			return;
		}
		if (hPort == null || "".equalsIgnoreCase(hPort.trim())) {
			LOG.error("Incorrect config ! (highPort is empty)");
			System.exit(1);
			return;
		}
		if (websocketCore == null || "".equalsIgnoreCase(websocketCore.trim())) {
			LOG.error("Incorrect config ! (websocketCore is empty)");
			System.exit(1);
			return;
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
		if (scriptStart == null || "".equalsIgnoreCase(scriptStart.trim())) {
			LOG.error("Incorrect config ! (scriptStart is empty)");
			System.exit(1);
			return;
		}
		if (scriptStop == null || "".equalsIgnoreCase(scriptStop.trim())) {
			LOG.error("Incorrect config ! (scriptStop is empty)");
			System.exit(1);
			return;
		}
		try {
			serverManager = new ServerManager(id, ip, lowPort, highPort, serverVps, scriptStart.split(" "),
					scriptStop.split(" "), new URI(websocketCore));
			serverManager.load();
		} catch (URISyntaxException ex) {
			ex.printStackTrace();
		}
		LOG.info("ServerManager initialized");
	}

	private void initializeWebSocket() {
		LOG.info("Initializing WebSocket");
		String strWebsocketPort = p.getProperty("websocket_port");
		int websocketPort = 35565;
		try {
			websocketPort = Integer.parseInt(strWebsocketPort);
		} catch (NumberFormatException ex) {
			LOG.error("websocketPort is not a number: {}", strWebsocketPort);
			LOG.info("Using default websocketPort ({})", websocketPort);
		}
		webSocketManager = new WebSocketManager(ip, websocketPort);
		LOG.info("WebSocket initialized");
	}

	private void initializeCommands() {
		LOG.info("Initializing CommandManager");
		commandManager = new CommandManager();
		LOG.info("CommandManager initialized");
	}

	public void stop() {
		LOG.info("Shutdowning ServerManager");
//		Main.get().getServerManager().stopAll(false);
		serverManager.stop();

		LOG.info("Shutdowning WebSocket");
		webSocketManager.stop();

		LOG.info("Shutdowning NetworkManager");
		networkManager.shutdown();

		LOG.info("Shutdowning Scheduler");
		Scheduler.stop();

		// Exit
		System.exit(0);
	}

	public static Main get() {
		return INSTANCE;
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
