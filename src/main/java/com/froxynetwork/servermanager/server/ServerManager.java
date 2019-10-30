package com.froxynetwork.servermanager.server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.froxynetwork.froxynetwork.network.output.Callback;
import com.froxynetwork.froxynetwork.network.output.RestException;
import com.froxynetwork.froxynetwork.network.output.data.server.ServerDataOutput;
import com.froxynetwork.servermanager.Main;

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
	private Main main;

	// Servers with id
	private HashMap<String, Server> servers;
	// The lowest port to use
	private int lowPort;
	// The highest port to use
	private int highPort;
	// Used to check the availability of ports
	private LinkedList<Integer> availablePort;

	public ServerManager(Main main, int lowPort, int highPort) {
		servers = new HashMap<>();
		this.main = main;
		this.lowPort = lowPort;
		this.highPort = highPort;
		this.availablePort = new LinkedList<>();
		for (int i = lowPort; i < highPort; i++)
			availablePort.add(i);
		// Shuffle the list
		Collections.shuffle(availablePort);
	}

	/**
	 * Try to open a server.<br />
	 * The server opening (request to the API, directory move, config etc) are done
	 * in async so the server is not created and launched at the end of this method
	 * (So to execute actions once the server is created, just use the parameter
	 * then)
	 * 
	 * @param type  The type of server (HUB, KOTH, ...)
	 * @param then  The action to execute once the server is created and launched
	 * @param error The action to execute if error occures
	 */
	public synchronized void openServer(String type, Consumer<Server> then, Runnable error) {
		if (stop)
			throw new IllegalStateException("Cannot open a server if the app is stopped !");
		// Check type
		if (!main.getServerConfigManager().exist(type))
			throw new IllegalStateException("Type " + type + " doesn't exist !");
		LOG.info("Opening a new server (type = {})", type);
		// Get port
		// TODO Change this method ?
		int port = getAndLockPort();
		if (port == -1) {
			// Not available port
			LOG.error("Not available port (opened)");
			error.run();
			return;
		}
		openServer(type, port, then, error);
	}

	private void openServer(String type, int port, Consumer<Server> then, Runnable error) {
		if (stop)
			throw new IllegalStateException("Cannot open a server if the app is stopping !");
		LOG.info("Using port {}", port);
		main.getNetworkManager().network().getServerService().asyncAddServer(type.toUpperCase() + "_" + port, type,
				port, new Callback<ServerDataOutput.Server>() {

					@Override
					public void onResponse(ServerDataOutput.Server response) {
						// Ok
						LOG.info("Server created on REST server, id = {}, creationTime = {}", response.getId(),
								response.getCreationTime());
						try {
							main.getDockerManager().startContainer(type, port, config -> {
								// Configure variables
								config.withEnv("EULA=true", "ID=" + response.getId(),
										"CLIENTID=" + response.getAuth().getClientId(),
										"CLIENTSECRET=" + response.getAuth().getClientSecret());
							}, container -> {
								Server srv = new Server(response.getId(), response, container);
								servers.put(response.getId(), srv);
								then.accept(srv);
							});
						} catch (Exception ex) {
							LOG.error("An error has occured while opening docker");
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
		if (srv == null)
			return;
		closeServer(servers.get(srv.getId()), then, false);
	}

	/**
	 * Close a server<br />
	 * This method create a new Thread to execute actions (delete Process, request
	 * to API, delete server directory) so these actions are done in async.
	 * 
	 * @param id   The id of the server
	 * @param then The action to execute once the server is deleted
	 */
	public void closeServer(String id, Runnable then) {
		if (id == null || "".equalsIgnoreCase(id.trim()))
			return;
		closeServer(servers.get(id), then, false);
	}

	/**
	 * Close a server<br />
	 * This method will firstly send a request to the server (via WebSocket) saying
	 * that this server must stopped<br />
	 * After 20 seconds, a stop command will be executed on the server<br />
	 * 20 seconds later, the method
	 * {@link #forceClose(ServerProcess, Runnable, boolean)} will be called to close
	 * the server. This method create a new Thread and execute all things in async
	 * mode if sync is false
	 * 
	 * @param srv  The ServerProcess
	 * @param then The action to execute once the server is deleted
	 */
	private void closeServer(Server srv, Runnable then, boolean sync) {
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
					// Send stop command
					// TODO Send stop command
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
	 * 
	 * @see #deleteServerProcess(ServerProcess)
	 */
	private void forceClose(Server srv, Runnable then, boolean sync) {
		Runnable deleteProcess = () -> {
			main.getDockerManager().stopContainer(srv.getContainer().getId(), () -> {
				// Free port
				freePort(srv.getRestServer().getPort());
				// Send request to delete the file
				main.getNetworkManager().network().getServerService().asyncDeleteServer(srv.getId(), null);
				servers.remove(srv.getId());
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

	public Server getServer(String id) {
		return servers.get(id);
	}

	public void addServer(Server server) {
		servers.put(server.getId(), server);
	}

	/**
	 * @return All servers
	 */
	public List<Server> getServers() {
		return new ArrayList<>(servers.values());
	}

	/**
	 * Stop the ServerManager
	 */
	public void stop() {
		this.stop = true;
		for (Server srv : servers.values())
			closeServer(srv, null, true);
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
