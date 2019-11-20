package com.froxynetwork.servermanager.server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.froxynetwork.froxynetwork.network.output.Callback;
import com.froxynetwork.froxynetwork.network.output.RestException;
import com.froxynetwork.froxynetwork.network.output.data.EmptyDataOutput;
import com.froxynetwork.froxynetwork.network.output.data.EmptyDataOutput.Empty;
import com.froxynetwork.froxynetwork.network.output.data.server.ServerDataOutput;
import com.froxynetwork.froxynetwork.network.output.data.server.ServerDataOutput.ServerDocker;
import com.froxynetwork.servermanager.Main;
import com.froxynetwork.servermanager.docker.DockerManager;
import com.froxynetwork.servermanager.server.config.ServerVps;

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
/**
 * This class represent an instance of a Vps.
 */
public class Vps {

	private final Logger LOG = LoggerFactory.getLogger(getClass());

	private Main main;
	private ServerVps sVps;
	// The lowest port to use
	private int lowPort;
	// The highest port to use
	private int highPort;
	// Used to check the availability of ports
	private LinkedList<Integer> availablePort;
	@Getter
	private boolean stop;

	// The docker daemon
	@Getter
	private DockerManager dockerManager;
	// The bungee
	@Getter
	@Setter
	private Server bungee;
	// A key-value map containing the id of the server as the key and the server as
	// the value
	private HashMap<String, Server> servers;

	public Vps(Main main, ServerVps sVps, int lowPort, int highPort) {
		this.main = main;
		this.sVps = sVps;
		this.lowPort = lowPort;
		this.highPort = highPort;
		this.availablePort = new LinkedList<>();
		for (int i = lowPort; i < highPort; i++)
			availablePort.add(i);
		// Shuffle the list
		Collections.shuffle(availablePort);

		servers = new HashMap<>();
		dockerManager = new DockerManager(this);
		try {
			dockerManager.initializeConnection(sVps.getHost(), sVps.getPath());
		} catch (Exception ex) {
			// Error ?
			LOG.error("Error while connecting to Docker in vps {} at host {}", sVps.getId(), sVps.getHost());
			LOG.error("", ex);
		}
	}

	/**
	 * Try to open a server.<br />
	 * The server opening (request to the API, directory move, config etc) are done
	 * inside a new Thread (so in async) (If you want to execute actions once the
	 * server is created, just use the parameter then)
	 * 
	 * @param type  The kind of server (HUB, KOTH, ...)
	 * @param then  The action to execute once the server is created and launched
	 * @param error The action to execute if error occures
	 */
	public void openServer(String type, Consumer<Server> then, Runnable error) {
		if (stop)
			throw new IllegalStateException("Cannot open a server if the VPS is stopped !");
		// Check type
		if (!main.getServerConfigManager().exist(type))
			throw new IllegalStateException("Type " + type + " doesn't exist !");
		LOG.info("Opening a new server on vps {} (type = {})", sVps.getId(), type);
		// Get port
		int port = getAndLockPort();
		if (port == -1) {
			// No available port
			LOG.error("No available port");
			error.run();
			return;
		}
		openServer(type, port, then, error);
	}

	private void openServer(String type, int port, Consumer<Server> then, Runnable error) {
		if (stop)
			throw new IllegalStateException("Cannot open a server if the VPS is stopped !");
		LOG.info("Using port {}", port);
		main.getNetworkManager().network().getServerService().asyncAddServer(type.toUpperCase() + "_" + port, type,
				port, new Callback<ServerDataOutput.Server>() {

					@Override
					public void onResponse(
							com.froxynetwork.froxynetwork.network.output.data.server.ServerDataOutput.Server response) {
						// Cool
						LOG.info("Server created on REST server, id = {}, creationTime = {}", response.getId(),
								response.getCreationTime());
						try {
							dockerManager.startContainer(type, port, config -> {
								config.withEnv("EULA=true", "ID=" + response.getId(),
										"CLIENTID=" + response.getAuth().getClientId(),
										"CLIENTSECRET=" + response.getAuth().getClientSecret());
							}, container -> {
								// Update
								ServerDocker serverDocker = new ServerDocker(sVps.getId(), container.getId());
								response.setDocker(serverDocker);
								main.getNetworkManager().getNetwork().getServerService().asyncEditServerDocker(
										response.getId(), serverDocker, new Callback<EmptyDataOutput.Empty>() {

											@Override
											public void onResponse(Empty empty) {
												LOG.info("All is ok for server {}", response.getId());
											}

											@Override
											public void onFailure(RestException ex) {
												LOG.error("Error while saving docker id {} for server {} :",
														container.getId(), response.getId());
												LOG.error("", ex);
												retry();
											}

											@Override
											public void onFatalFailure(Throwable t) {
												LOG.error("Fatal Error while saving docker id {} for server {} :",
														container.getId(), response.getId());
												LOG.error("", t);
												retry();
											}

											private void retry() {
												boolean ok = false;
												for (int i = 1; i <= 10 && !ok; i++) {
													try {
														main.getNetworkManager().getNetwork().getServerService()
																.syncEditServerDocker(response.getId(), serverDocker);
														ok = true;
													} catch (RestException ex) {
														LOG.error("Error #{} while saving docker id {} for server {} :",
																i, container.getId(), response.getId());
														LOG.error("", ex);
													} catch (Exception ex) {
														LOG.error(
																"Fatal Error #{} while saving docker id {} for server {} :",
																i, container.getId(), response.getId());
														LOG.error("", ex);
													}
												}
												if (!ok) {
													// Wtf ????
													LOG.error(
															"Cannot register docker id {} for server {}, we'll stop it !",
															container.getId(), response.getId());
													// So here this server will not have a docker id registered on rest.
													// If this app crashes, we cannot get again and linked this server
													// This is why we'll stop this server

													dockerManager.stopContainer(container.getId(), () -> {
														// Free port
														freePort(response.getPort());
														// Send request to delete the server
														main.getNetworkManager().network().getServerService()
																.asyncDeleteServer(response.getId(), null);
														// All is ok
														if (error != null)
															error.run();
													}, true);
												}
											}
										});
								Server srv = new Server(response.getId(), response, Vps.this, container.getId());
								registerServer(srv);
								then.accept(srv);
							});
						} catch (Exception ex) {
							LOG.error("An error has occured while opening docker: ", ex);
							// Free port
							freePort(response.getPort());
							// Send request to delete the file
							main.getNetworkManager().network().getServerService().asyncDeleteServer(response.getId(),
									null);
							error.run();
							return;
						}
					}

					@Override
					public void onFailure(RestException ex) {
						// Error
						LOG.error(
								"Error while asking REST server for type = {} and port = {}: return code = {}, error message = {}",
								type, port, ex.getError().getCode(), ex.getError().getErrorMessage());
						LOG.error("", ex);
						freePort(port);
						LOG.info("Port {} is now free", port);
						error.run();
					}

					@Override
					public void onFatalFailure(Throwable t) {
						// Fatal
						LOG.error("Fatal error while asking REST server for type = {} and port = {}", type, port);
						LOG.error("", t);
						freePort(port);
						LOG.error("Port {} is now free", port);
						error.run();
					}
				});
	}

	/**
	 * Close a server<br />
	 * This method create a new Thread to execute actions (delete Process, request
	 * to API, delete server directory) so these actions are done in async.
	 * 
	 * @param srv  The server
	 * @param then The action to execute once the server is deleted
	 */
	public void closeServer(Server srv, Runnable then) {
		if (srv == null) {
			then.run();
			return;
		}
		closeServer(srv, then, false);
	}

	/**
	 * Close a server<br />
	 * This method will send a request to the server (via WebSocket) saying that
	 * this server must stopped<br />
	 * After 20 seconds, the method
	 * {@link #forceClose(ServerProcess, Runnable, boolean)} will be called to close
	 * the server. This method create a new Thread and execute all things in async
	 * mode if sync is false
	 * 
	 * @param srv  The ServerProcess
	 * @param then The action to execute once the server is deleted
	 * @param sync If true, execute all actions in sync mode
	 * 
	 * @see #forceClose(Server, Runnable, boolean)
	 */
	public void closeServer(Server srv, Runnable then, boolean sync) {
		if (srv == null)
			return;
		LOG.info("{}: closing server in {} mode", srv.getId(), sync ? "Sync" : "Async");
		Runnable r = () -> {
			if (srv.getWebSocketServerImpl() != null && srv.getWebSocketServerImpl().isConnected()) {
				// Send stop request via WebSocket
				// TODO Add stop reason and / or instant stop
				try {
					srv.getWebSocketServerImpl().sendMessage("MAIN", "stop", "now");
					// Sleep 20 seconds
					Thread.sleep(20000);
				} catch (Exception ex) {
					// Exception
					LOG.error("{}: An error has occured while sending a stop request", srv.getId());
				}
			} else {
				LOG.info("{}: Cannot send a stop request if there is not WebSocket liaison", srv.getId());
			}
			LOG.info("{}: Calling forceClose", srv.getId());
			forceClose(srv, then, sync);
		};
		if (sync)
			r.run();
		else
			new Thread(r, "Close Server " + srv.getId()).start();
	}

	/**
	 * Force close a server by stopping the container and sending a close request to
	 * the API<br />
	 * All these methods are done in async mode if sync is false
	 * 
	 * @param srv  The Server
	 * @param then The action to execute once the server is deleted
	 * @param sync If true, execute all actions in sync mode
	 */
	public void forceClose(Server srv, Runnable then, boolean sync) {
		Runnable deleteProcess = () -> {
			dockerManager.stopContainer(srv.getContainerId(), () -> {
				unregisterServer(srv.getId());
				// Send request to delete the file
				main.getNetworkManager().network().getServerService().asyncDeleteServer(srv.getId(),
						new Callback<EmptyDataOutput.Empty>() {

							@Override
							public void onResponse(Empty response) {
								LOG.info("OK");
							}

							@Override
							public void onFailure(RestException ex) {
								ex.printStackTrace();
							}

							@Override
							public void onFatalFailure(Throwable t) {
								t.printStackTrace();
							}
						});
				// All is ok
				if (then != null)
					then.run();
			}, sync);
		};
		if (sync) {
			// Sync
			deleteProcess.run();
		} else {
			// Async
			Thread t = new Thread(deleteProcess, "Delete Server " + srv.getId());
			t.run();
		}
	}

	public int getRunningServers() {
		return servers.size();
	}

	public Server getServer(String id) {
		return servers.get(id);
	}

	/**
	 * Register server and remove port used from the list
	 * 
	 * @param srv
	 */
	public void registerServer(Server srv) {
		servers.put(srv.getId(), srv);
		// Cast to Integer to remove specific element
		synchronized (availablePort) {
			availablePort.remove((Integer) srv.getRestServer().getPort());
		}
	}

	/**
	 * Remove server from list and free port
	 * 
	 * @param id
	 */
	public void unregisterServer(String id) {
		Server srv = servers.remove(id);
		if (srv != null)
			freePort(srv.getRestServer().getPort());
	}

	public boolean isFull() {
		return availablePort.size() == 0;
	}

	/**
	 * Set this VPS in "stopped" mode so no new servers will be created<br />
	 * THIS METHOD DOESN'T STOP RUNNING SERVERS<br />
	 * To stop running servers, call {@link #stopAll()}
	 */
	public void stop() {
		stop = true;
	}

	/**
	 * Call {@link #stop()}, and stop all running servers in async mode
	 */
	public void stopAll() {
		stop();
		// Create a new list to avoid CurrentModificationException

		// Close servers in async mode and wait
		List<Server> servers = new ArrayList<>(this.servers.values());
		// Used to wait
		CountDownLatch latch = new CountDownLatch(servers.size());
		for (Server srv : servers)
			closeServer(srv, () -> {
				LOG.info("Server {} closed", srv.getId());
				latch.countDown();
			}, false);
		try {
			latch.await(60, TimeUnit.SECONDS);
		} catch (InterruptedException ex) {
			LOG.error("Thread interrupted: ", ex);
		}
	}

	public String getId() {
		return sVps.getId();
	}

	private int getAndLockPort() {
		synchronized (availablePort) {
			if (availablePort.size() == 0)
				return -1;
			return availablePort.pollFirst();
		}
	}

	private void freePort(int port) {
		synchronized (availablePort) {
			availablePort.add(port);
		}
	}
}
