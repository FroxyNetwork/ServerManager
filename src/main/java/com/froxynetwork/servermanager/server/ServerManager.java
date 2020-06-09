package com.froxynetwork.servermanager.server;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.UUID;

import org.java_websocket.framing.CloseFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.froxynetwork.froxynetwork.network.output.Callback;
import com.froxynetwork.froxynetwork.network.output.RestException;
import com.froxynetwork.froxynetwork.network.output.data.EmptyDataOutput;
import com.froxynetwork.froxynetwork.network.output.data.EmptyDataOutput.Empty;
import com.froxynetwork.froxynetwork.network.output.data.server.ServerDataOutput;
import com.froxynetwork.froxynetwork.network.output.data.server.ServerListDataOutput.ServerList;
import com.froxynetwork.froxynetwork.network.service.ServerService.Type;
import com.froxynetwork.froxynetwork.network.websocket.WebSocketClientImpl;
import com.froxynetwork.froxynetwork.network.websocket.WebSocketFactory;
import com.froxynetwork.froxynetwork.network.websocket.WebSocketServerImpl;
import com.froxynetwork.froxynetwork.network.websocket.auth.WebSocketTokenAuthentication;
import com.froxynetwork.froxynetwork.network.websocket.modules.WebSocketAutoReconnectModule;
import com.froxynetwork.servermanager.Main;
import com.froxynetwork.servermanager.scheduler.Scheduler;
import com.froxynetwork.servermanager.server.config.ServerVps;
import com.froxynetwork.servermanager.websocket.commands.core.ServerStartCommand;
import com.froxynetwork.servermanager.websocket.commands.core.ServerStopCommand;

import lombok.Getter;
import lombok.Setter;

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
public class ServerManager {
	private final Logger LOG = LoggerFactory.getLogger(getClass());

	@Getter
	private String id;
	private boolean stop = false;
	private int lowPort;
	private int highPort;
	@Getter
	private ServerVps serverVps;
	private URI coreURI;
	private LinkedList<Integer> availablePort;
	@Getter
	@Setter
	private Server bungee;
	private HashMap<String, Server> servers;
	private HashMap<String, Server> creatingServers;
	private WebSocketClientImpl client;
	private String[] scriptStart;
	private String[] scriptStop;

	public ServerManager(String id, int lowPort, int highPort, ServerVps serverVps, String[] scriptStart,
			String[] scriptStop, URI coreURI) {
		this.id = id;
		this.lowPort = lowPort;
		this.highPort = highPort;
		this.serverVps = serverVps;
		this.scriptStart = scriptStart;
		this.scriptStop = scriptStop;
		this.coreURI = coreURI;
		this.servers = new HashMap<>();
		this.creatingServers = new HashMap<>();
		this.availablePort = new LinkedList<>();
		// TODO Detect available port
		for (int i = lowPort; i <= highPort; i++)
			availablePort.add(i);
	}

	private void loadAllServers() {
		// Load servers that are running on this VPS
		// Here, we load these servers in sync mode
		LOG.debug("loadAllServers()");
		LOG.info("Loading Servers ...");
		try {
			// Bungee
			LOG.debug("Bungee ...");
			ServerList list = Main.get().getNetworkManager().getNetwork().getServerService()
					.syncGetServers(Type.BUNGEE);
			LOG.debug("Got {} bungee !", list.getServers().size());
			for (ServerDataOutput.Server srvList : list.getServers()) {
				if (srvList.getVps() != null && srvList.getVps().equalsIgnoreCase(id)) {
					LOG.debug("Found bungee {} being bungee on this VPS !", srvList.getId());
					// A Bungee is already running on this VPS
					bungee = new Server(null, srvList.getId(), srvList, true);
					// TODO Check if directory exist
				}
			}
			if (bungee != null)
				LOG.info("Bungee {} is bungee of this VPS !", bungee.getId());
			else
				LOG.info("No bungee found for this VPS ! Let's create one later ...");

			// Servers
			LOG.debug("Servers ...");
			list = Main.get().getNetworkManager().getNetwork().getServerService().syncGetServers(Type.SERVER);
			LOG.debug("Got {} servers !", list.getServers().size());
			for (ServerDataOutput.Server srvList : list.getServers()) {
				if (srvList.getVps() != null && srvList.getVps().equalsIgnoreCase(id)) {
					LOG.debug("Found server {} being one server of this VPS !", srvList.getId());
					// A Server is already running on this VPS
					servers.put(srvList.getId(), new Server(null, srvList.getId(), srvList, false));
					availablePort.remove((Integer) srvList.getPort());
					// TODO Check if directory exist
				}
			}
			LOG.info("{} server loaded !", servers.size());
		} catch (RestException ex) {
			LOG.error("Error while retrieving servers: ", ex);
		} catch (Exception ex) {
			LOG.error("Fatal Error while retrieving servers: ", ex);
			ex.printStackTrace();
		}
	}

	public void login() throws URISyntaxException {
		LOG.debug("login()");
		if (client != null && client.isConnected()) {
			LOG.debug("login(): client already connected");
			return;
		}
		client = WebSocketFactory.client(coreURI, new WebSocketTokenAuthentication(Main.get().getNetworkManager()));
		client.registerWebSocketAuthentication(() -> {
			// TODO
		});

		WebSocketAutoReconnectModule wsarm = new WebSocketAutoReconnectModule(5000);
		client.registerWebSocketDisconnection(remote -> {
			if (!stop)
				return;
			wsarm.unload();
		});
		client.addModule(wsarm);

		// Commands
		client.registerCommand(new ServerStartCommand(client));
		client.registerCommand(new ServerStopCommand(client));

		LOG.debug("login() ok");
	}

	private boolean loaded = false;

	public void load() throws URISyntaxException {
		if (loaded)
			return;
		loadAllServers();
		login();
		loaded = true;
	}

	public Server getServer(String id) {
		return servers.get(id);
	}

	public Server getCreatingServer(String id) {
		return creatingServers.get(id);
	}

	/**
	 * Load this server and notify CoreManager that this server is now loaded
	 * 
	 * @param server The server
	 */
	public void loadServer(Server server, WebSocketServerImpl wssi) {
		creatingServers.remove(server.getId());
		servers.put(server.getId(), server);
		server.resumeWebSocket(wssi);
		// Notify
		Scheduler.add(() -> {
			client.sendCommand("register", server.getUuid().toString() + " " + server.getId());
			return true;
		}, () -> {
			// Error
			LOG.error("Error while sending \"register {}\" command", server.getUuid());
		});
	}

	public void openServer(String type, UUID uuid, Runnable error) {
		if (stop) {
			error.run();
			return;
		}
		LOG.info("Opening server type {} uuid {}", type, uuid.toString());
		Scheduler.add(() -> _openServer(type, uuid, error), error);
	}

	private boolean _openServer(String type, UUID uuid, Runnable error) {
		LOG.debug("_openServer type {} uuid {}", type, uuid.toString());
		if (availablePort.size() == 0) {
			LOG.warn("No available port found !");
			return false;
		}
		int port = availablePort.poll();
		String name = type + "_" + port;
		Main.get().getNetworkManager().getNetwork().getServerService().asyncAddServer(name, type, port,
				new Callback<ServerDataOutput.Server>() {

					@Override
					public void onResponse(
							com.froxynetwork.froxynetwork.network.output.data.server.ServerDataOutput.Server response) {
						LOG.debug("Got id {} for uuid {}", response.getId(), uuid.toString());
						// Server has been created on REST
						Server srv = new Server(uuid, response.getId(), response, false);
						creatingServers.put(response.getId(), srv);
						new Thread(() -> {
							// Call script that will launch the server
							String[] copy = new String[scriptStart.length];
							for (int i = 0; i < scriptStart.length; i++) {
								copy[i] = scriptStart[i].replaceAll("\\{type\\}", type)
										.replaceAll("\\{id\\}", srv.getId())
										.replaceAll("\\{secret\\}", response.getAuth().getClientSecret())
										.replaceAll("\\{port\\}", Integer.toString(port));
							}
							ProcessBuilder pb = new ProcessBuilder(copy);
							try {
								LOG.debug("Starting creation script for server {}", srv.getId());
								Process p = pb.start();
								// For test only
								BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
								int exitValue = p.waitFor();
								// Debug
								String line = null;
								while ((line = reader.readLine()) != null)
									LOG.debug("input: {}", line);
								if (exitValue != 0) {
									// Error
									throw new IllegalStateException(
											"Starting server " + srv.getId() + " returns exitValue " + exitValue);
								}
							} catch (Exception ex) {
								LOG.error("Error while starting start script for server {} (type = {})", srv.getId(),
										type);
								LOG.error("", ex);
								// Closing it
								Main.get().getNetworkManager().getNetwork().getServerService()
										.asyncDeleteServer(srv.getId(), new Callback<EmptyDataOutput.Empty>() {

											@Override
											public void onResponse(Empty response) {
												// Okay
											}

											@Override
											public void onFailure(RestException ex) {
												LOG.error("Error while closing server {}", srv.getId());
												LOG.error("", ex);
											}

											@Override
											public void onFatalFailure(Throwable t) {
												LOG.error("Fatal Error while closing server {}", srv.getId());
												LOG.error("", t);
											}
										});
								error.run();
							}
						}, "ServerManager-Copy-" + response.getId()).start();
					}

					@Override
					public void onFailure(RestException ex) {
						LOG.error("Failure while creating server (type = {}, port = {}, uuid = {})", type, port, uuid);
						LOG.error("", ex);
						error.run();
					}

					@Override
					public void onFatalFailure(Throwable t) {
						LOG.error("Fatal Failure while creating server (type = {}, port = {}, uuid = {})", type, port,
								uuid);
						LOG.error("", t);
						error.run();
					}
				});
		return true;
	}

	public void closeServer(String id, Runnable error) {
		Scheduler.add(() -> _closeServer(id, error), error);
	}

	private boolean _closeServer(String id, Runnable error) {
		Server srv = servers.remove(id);
		if (srv == null)
			return true;
		if (srv.getWebSocket() != null && srv.getWebSocket().isConnected())
			srv.getWebSocket().sendCommand("stop", null);
		srv.getWebSocket().closeAll();

		new Thread(() -> {
			// Wait 10 seconds for the stop request sent previously
			// TODO Do not delete the directory if server is SkyBlock
			try {
				Thread.sleep(10000);
			} catch (InterruptedException ex) {
				ex.printStackTrace();
			}
			// Call script that will launch the server
			String[] copy = new String[scriptStop.length];
			for (int i = 0; i < scriptStop.length; i++)
				copy[i] = scriptStop[i].replaceAll("\\{id\\}", id);
			ProcessBuilder pb = new ProcessBuilder(copy);
			try {
				LOG.debug("Starting stop script for server {}", id);
				Process p = pb.start();
				// For test only
				BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
				int exitValue = p.waitFor();
				// Debug
				String line = null;
				while ((line = reader.readLine()) != null)
					LOG.debug("input: {}", line);
				if (exitValue != 0) {
					// Error
					throw new IllegalStateException("Stopping server " + id + " returns exitValue " + exitValue);
				}
			} catch (Exception ex) {
				LOG.error("Error while starting stop script for server {}", id);
				LOG.error("", ex);
				error.run();
			}
			// Closing it
			Main.get().getNetworkManager().getNetwork().getServerService().asyncDeleteServer(id,
					new Callback<EmptyDataOutput.Empty>() {

						@Override
						public void onResponse(Empty response) {
							// Okay
						}

						@Override
						public void onFailure(RestException ex) {
							LOG.error("Error while closing server {}", id);
							LOG.error("", ex);
						}

						@Override
						public void onFatalFailure(Throwable t) {
							LOG.error("Fatal Error while closing server {}", id);
							LOG.error("", t);
						}
					});
		}, "ServerManager-Stop-" + id).start();
		return true;
	}

	/**
	 * Set the ServerManager in "stopped" mode so no new servers will be created and
	 * disconnect WebSocket<br />
	 * THIS METHOD DOESN'T STOP RUNNING SERVERS<br />
	 * To stop running servers, call {@link #stopAll()}
	 */
	public void stop() {
		this.stop = true;
		client.disconnect(CloseFrame.NORMAL, "");
		client.closeAll();
	}
}
