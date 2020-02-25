package com.froxynetwork.servermanager.server;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.froxynetwork.froxynetwork.network.output.Callback;
import com.froxynetwork.froxynetwork.network.output.RestException;
import com.froxynetwork.froxynetwork.network.output.data.EmptyDataOutput;
import com.froxynetwork.froxynetwork.network.output.data.EmptyDataOutput.Empty;
import com.froxynetwork.froxynetwork.network.output.data.server.ServerListDataOutput.ServerList;
import com.froxynetwork.servermanager.Main;
import com.froxynetwork.servermanager.server.config.ServerVps;

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
public class ServerManager {

	private final Logger LOG = LoggerFactory.getLogger(getClass());
	private boolean stop = false;
	private int lowPort;
	private int highPort;
	private int webSocketAuthTimeout;

	// A list of VPS
	@Getter
	private List<Vps> vps;
	// A key-value map containing the id of the server as the key and the Vps where
	// is the server as the value
	private HashMap<String, Vps> serversVps;
	// A key-value map containing the id of the BungeeCord as the key and the Vps
	// where is the BungeeCord as the value
	private HashMap<String, Vps> bungeecordsVps;

	public ServerManager(int lowPort, int highPort, int webSocketAuthTimeout) {
		this.lowPort = lowPort;
		this.highPort = highPort;
		this.webSocketAuthTimeout = webSocketAuthTimeout;
		serversVps = new HashMap<>();
		bungeecordsVps = new HashMap<>();
	}

	private boolean loaded = false;

	public void load() {
		if (loaded)
			return;
		initializeVps();
		initializeAllServers();
		initializeAllDockers();
	}

	/**
	 * Initialize a VPS
	 */
	private void initializeVps() {
		vps = new ArrayList<>();
		for (ServerVps sv : Main.get().getServerConfigManager().getVps())
			this.vps.add(new Vps(this, sv, lowPort, highPort, webSocketAuthTimeout));
	}

	/**
	 * Retrieve all servers from REST and load
	 */
	private void initializeAllServers() {
		try {
			ServerList servers = Main.get().getNetworkManager().getNetwork().getServerService().syncGetServers();
			LOG.info("Loading {} servers ...", servers.getSize());
			Date now = new Date();
			long milliNow = now.getTime();
			for (com.froxynetwork.froxynetwork.network.output.data.server.ServerDataOutput.Server srv : servers
					.getServers()) {
				// Test if server was opened more than 4 hours ago
				long diff = milliNow - srv.getCreationTime().getTime();
				if (diff >= 21600000) {
					// STOPPPPPPPPP
					LOG.info("Server {} was created more than 4 hours ago, stopping it", srv.getId());
					Main.get().getNetworkManager().getNetwork().getServerService().asyncDeleteServer(srv.getId(),
							new Callback<EmptyDataOutput.Empty>() {

								@Override
								public void onResponse(Empty response) {
									LOG.info("Server {} deleted", srv.getId());
								}

								@Override
								public void onFailure(RestException ex) {
									LOG.error("Error while deleting server {}", srv.getId());
								}

								@Override
								public void onFatalFailure(Throwable t) {
									LOG.error("Fatal Error while deleting server {}", srv.getId());
								}
							});
				} else {
					// Register it
					if (srv.getDocker() == null) {
						// WHAT ????
						LOG.error("Server {} doesn't have a registered docker !!!! Stopping it ...", srv.getId());
						// Stop the server
						Main.get().getNetworkManager().getNetwork().getServerService().asyncDeleteServer(srv.getId(),
								new Callback<EmptyDataOutput.Empty>() {

									@Override
									public void onResponse(Empty response) {
										LOG.info("Server {} deleted", srv.getId());
									}

									@Override
									public void onFailure(RestException ex) {
										LOG.error("Error while deleting server {}", srv.getId());
									}

									@Override
									public void onFatalFailure(Throwable t) {
										LOG.error("Fatal Error while deleting server {}", srv.getId());
									}
								});
					} else {
						Vps vps = getVps(srv.getDocker().getServer());
						String containerId = srv.getDocker().getId();
						Server serv = new Server(srv.getId(), srv, vps, containerId, webSocketAuthTimeout);
						vps.registerServer(serv, true);
						serversVps.put(serv.getId(), vps);
						LOG.info("Server {} registered", srv.getId());
					}
				}
			}
		} catch (RestException ex) {
			LOG.error("Error while retrieving servers", ex);
		} catch (Exception ex) {
			LOG.error("Fatal error while retrieving servers", ex);
		}
	}

	/*
	 * Retrieves all dockers from all VPS and close unregistered dockers
	 */
	private void initializeAllDockers() {
		for (Vps vps : this.vps) {
			try {
				LOG.info("Retrieving running dockers for VPS {}", vps.getId());
				vps.getDockerManager().getDockers(dockers -> {
					LOG.info("- VPS: {}, Number of dockers: {}", vps.getId(), dockers.size());
					vps.initializeDockers(dockers);
				});
			} catch (Exception ex) {
				// Wops, strange exception here
				LOG.error("Error while retrieving dockers for VPS {} :", vps.getId());
				LOG.error("", ex);
			}
		}
	}

	/**
	 * Call {@link Vps#openServer(String, Consumer, Runnable)} on Vps that has fewer
	 * servers running, that is not full and that is not closed
	 * 
	 * @param type  The type of server (HUB, KOTH, ...)
	 * @param then  The action to execute once the server is created and launched
	 * @param error The action to execute if error occures
	 */
	public synchronized void openServer(String type, Consumer<Server> then, Runnable error) {
		if (stop)
			throw new IllegalStateException("Cannot open a server if the app is stopped !");
		// Check type
		if (!Main.get().getServerConfigManager().exist(type))
			throw new IllegalStateException("Type " + type + " doesn't exist !");
		Vps vps = findOptimalVps();
		if (vps == null) {
			LOG.error("Cannot find an optimal Vps !");
			error.run();
			return;
		}
		vps.openServer(type, then, error);
	}

	/**
	 * Add server to the list
	 * 
	 * @param srv The server
	 */
	protected void _openServer(Server srv) {
		serversVps.put(srv.getId(), srv.getVps());
		onServerOpen(srv);
	}

	/**
	 * Called when a server is opened
	 * 
	 * @param srv The server
	 */
	public void onServerOpen(Server srv) {
		String msg = srv.getId() + " " + srv.getVps().getServerVps().getHost() + " " + "Random MOTD";
		for (Vps vps : this.vps) {
			Server bungee = vps.getBungee();
			if (bungee != null) {
				// WebSocketRegister <serverId> <host> <port> <motd>
				// TODO MOTD
				bungee.getWebSocketServerImpl().sendMessage("MAIN", "WebSocketRegister", msg);
			}
		}
	}

	/**
	 * Same as <code>closeServer(srv, then, false)</code>
	 * 
	 * @param srv  The server
	 * @param then The action to execute once the server is deleted
	 * 
	 * @see #closeServer(Server, Runnable, boolean)
	 */
	public void closeServer(Server srv, Runnable then) {
		closeServer(srv, then, false);
	}

	/**
	 * Call {@link Vps#closeServer(Server, Runnable, boolean)}
	 * 
	 * @param srv  The ServerProcess
	 * @param then The action to execute once the server is deleted
	 * @param sync If true, execute all actions in sync mode
	 * 
	 * @see Vps#closeServer(Server, Runnable, boolean)
	 */
	public void closeServer(Server srv, Runnable then, boolean sync) {
		if (srv == null) {
			then.run();
			return;
		}
		srv.getVps().closeServer(srv, then, sync);
	}

	/**
	 * Remove the server from the list
	 */
	protected void _closeServer(Server srv) {
		serversVps.remove(srv.getId());
		onServerClose(srv);
	}

	/**
	 * Called when a server is closed
	 * 
	 * @param srv The server
	 */
	public void onServerClose(Server srv) {
		for (Vps vps : this.vps) {
			Server bungee = vps.getBungee();
			if (bungee != null) {
				// WebSocketUnregister <serverId>
				bungee.getWebSocketServerImpl().sendMessage("MAIN", "WebSocketUnregister", srv.getId());
			}
		}
	}
	
	/**
	 * Find the optimal Vps that has fewer servers running, that is not full and
	 * that is not closed
	 */
	private Vps findOptimalVps() {
		// No vps available
		if (this.vps.size() == 0)
			return null;
		Vps vps = null;
		for (Vps v : this.vps)
			if (!v.isStop() && (vps == null || v.getRunningServers() < vps.getRunningServers()))
				vps = v;
		return vps;
	}

	/**
	 * Return the VPS that has specific id
	 * 
	 * @param id The id of the VPS
	 * @return The VPS or null if not found
	 */
	public Vps getVps(String id) {
		for (Vps vps : this.vps)
			if (vps.getId().equalsIgnoreCase(id))
				return vps;
		return null;
	}

	public Server getServer(String id) {
		Vps vps = serversVps.get(id);
		if (vps == null)
			return null;
		return vps.getServer(id);
	}

	/**
	 * Set the ServerManager in "stopped" mode so no new servers will be
	 * created<br />
	 * THIS METHOD DOESN'T STOP RUNNING SERVERS<br />
	 * To stop running servers, call {@link #stopAll()}
	 */
	public void stop() {
		this.stop = true;
	}

	/**
	 * <ul>
	 * <li>Call {@link #stop}</li>
	 * <li>Call {@link Vps#stopAll(boolean)} for each VPS</li>
	 * </ul>
	 */
	public void stopAll(boolean closeAll) {
		stop();
		for (Vps vps : vps)
			vps.stopAll(closeAll);
	}
}
