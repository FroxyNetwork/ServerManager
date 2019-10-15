package com.froxynetwork.servermanager.server;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.froxynetwork.froxynetwork.network.output.Callback;
import com.froxynetwork.froxynetwork.network.output.RestException;
import com.froxynetwork.froxynetwork.network.output.data.server.ServerDataOutput;
import com.froxynetwork.servermanager.Main;
import com.froxynetwork.servermanager.util.ZipUtil;

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
	private Main main;

	// Servers with id
	private HashMap<String, Server> servers;
	// Directory where are servers to copy
	@Getter
	private File srvDir;
	// Directory where servers will be
	private File toDir;
	// The lowest port to use
	private int lowPort;
	// The highest port to use
	private int highPort;
	// Used to check the availability of ports
	private LinkedList<Integer> availablePort;

	public ServerManager(Main main, File srvDir, File toDir, int lowPort, int highPort, boolean deleteDirectories,
			boolean deleteFiles) {
		servers = new HashMap<>();
		this.main = main;
		this.srvDir = srvDir;
		this.toDir = toDir;
		this.lowPort = lowPort;
		this.highPort = highPort;
		this.availablePort = new LinkedList<>();
		for (int i = lowPort; i < highPort; i++)
			availablePort.add(i);
		// Shuffle the list
		Collections.shuffle(availablePort);
		// Delete all servers
		boolean error = false;
		LOG.info("deleteDirectories = {}, deleteFiles = {}", deleteDirectories, deleteFiles);
		LOG.info("Number of directories / files = {}", toDir.list().length);
		int nbrDirectories = 0;
		int nbrFiles = 0;
		for (File f : toDir.listFiles()) {
			try {
				if (f.isDirectory()) {
					nbrDirectories++;
					if (deleteDirectories) {
						LOG.info("Deleting directory {}", f.getName());
						File mc = new File(f + File.separator + "minecraft_server.jar");
						boolean ok = true;
						;
						if (mc.exists() && mc.isFile()) {
							LOG.info("Minecraft server detected, trying deleting the server");
							// Minecraft directory
							try {
								ok = mc.delete();
							} catch (Exception ex) {
								ok = false;
								// Error
							}
						}
						if (ok)
							FileUtils.deleteDirectory(f);
						else
							LOG.warn("Skipping directory {}", f.getName());
					} else
						LOG.warn("Skipping directory {}", f.getName());
				} else if (f.isFile()) {
					nbrFiles++;
					if (deleteFiles) {
						LOG.info("Deleting file {}", f.getName());
						f.delete();
					} else
						LOG.warn("Skipping file {}", f.getName());
				}
			} catch (Exception ex) {
				// Error
				error = true;
				LOG.error("An error has occured while deleting the server directory {}", f.getName());
				LOG.error("", ex);
			}
		}
		if (deleteDirectories)
			LOG.info("{} directories deleted !", nbrDirectories);
		else
			LOG.info("{} directories passed !", nbrDirectories);
		if (deleteFiles)
			LOG.info("{} files deleted !", nbrFiles);
		else
			LOG.info("{} files passed !", nbrFiles);
		if (error)
			throw new IllegalStateException("An error has occured while trying to load ServerManager");
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
			throw new IllegalStateException("Cannot open a server if the app is stopping !");
		// Check type
		if (!main.getServerConfigManager().exist(type))
			throw new IllegalStateException("Type " + type + " doesn't exist !");
		LOG.info("Opening a new server (type = {})", type);
		// Get port
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
						File srcServ = new File(srvDir, type + ".zip");
						File toServ = new File(toDir, response.getId());
						LOG.info("{}: Extracting file {} to directory {}", response.getId(), srcServ.getAbsolutePath(),
								toServ.getAbsolutePath());
						if (!srcServ.exists()) {
							// Source server doesn't exist
							LOG.error("{}: Source server doesn't exists !", response.getId());
							// Free port
							freePort(response.getPort());
							// Send request to delete the file
							main.getNetworkManager().network().getServerService().asyncDeleteServer(response.getId(),
									null);
							deleteServerDirectory(response.getId());
							error.run();
							return;
						}
						if (!srcServ.isFile()) {
							// Source server is not a directory
							LOG.error("{}: Source server is not a file !", response.getId());
							// Free port
							freePort(response.getPort());
							// Send request to delete the file
							main.getNetworkManager().network().getServerService().asyncDeleteServer(response.getId(),
									null);
							deleteServerDirectory(response.getId());
							error.run();
							return;
						}
						if (!srcServ.canRead()) {
							// Cannot read source server
							LOG.error("{}: Source server cannot be read (no read access) !", response.getId());
							// Free port
							freePort(response.getPort());
							// Send request to delete the file
							main.getNetworkManager().network().getServerService().asyncDeleteServer(response.getId(),
									null);
							deleteServerDirectory(response.getId());
							error.run();
							return;
						}
						if (toServ.exists()) {
							// Directory already exists
							LOG.error("{}: Destination server directory already exist !", response.getId());
							// Free port
							freePort(response.getPort());
							// Send request to delete the file
							main.getNetworkManager().network().getServerService().asyncDeleteServer(response.getId(),
									null);
							deleteServerDirectory(response.getId());
							error.run();
							return;
						}
						try {
							try {
								ZipUtil.unzipAndMove(srcServ, toServ);
							} catch (IOException ex) {
								// Cannot read source server
								LOG.error("{}: An error has occured while extracting archive {} to {}",
										response.getId(), srcServ.getAbsolutePath(), toServ.getAbsolutePath());
								LOG.error("", ex);
								// Free port
								freePort(response.getPort());
								// Send request to delete the file
								main.getNetworkManager().network().getServerService()
										.asyncDeleteServer(response.getId(), null);
								deleteServerDirectory(response.getId());
								error.run();
								return;
							}
							if (!toServ.exists() || !toServ.isDirectory()) {
								// Whut ????
								LOG.error("{}: An unknown error has occured while creating directories !",
										response.getId());
								// Free port
								freePort(response.getPort());
								// Send request to delete the file
								main.getNetworkManager().network().getServerService()
										.asyncDeleteServer(response.getId(), null);
								deleteServerDirectory(response.getId());
								error.run();
								return;
							}
							// Done, now let's configure the server
							// server.properties
							LOG.info("{}: Editing server.properties file", response.getId());
							PrintWriter writerServer = new PrintWriter(
									new FileOutputStream(new File(toServ, "server.properties"), true));
							writerServer.println("server-name=" + response.getName());
							writerServer.println("server-port=" + response.getPort());
							writerServer.close();
							LOG.info("{}: File server.properties has succesfully been edited", response.getId());
							LOG.info("{}: Saving auth access", response.getId());
							File authFile = new File(toServ + File.separator + "plugins" + File.separator + "FroxyCore"
									+ File.separator + "auth");
							authFile.getParentFile().mkdirs();
							if (!authFile.createNewFile()) {
								LOG.error("{}: Cannot create auth file !", response.getId());
								// Free port
								freePort(response.getPort());
								// Send request to delete the file
								main.getNetworkManager().network().getServerService()
										.asyncDeleteServer(response.getId(), null);
								deleteServerDirectory(response.getId());
								error.run();
								return;
							}
							// The server needs to know his id, his client id and his client secret to be
							// able to connect to the WebSocket
							PrintWriter writerConfig = new PrintWriter(new FileOutputStream(authFile, true));
							writerConfig.write(response.getId() + '\n');
							writerConfig.write(response.getAuth().getClientId() + '\n');
							writerConfig.write(response.getAuth().getClientSecret() + '\n');
							writerConfig.close();
							LOG.info("{}: Auth access saved", response.getId());

							LOG.info("{}: Starting server", response.getId());
							// TODO Find another way to do that
							Process p = Runtime.getRuntime().exec(
									"java -Xms512M -Xmx512M -jar minecraft_server.jar nogui " + response.getId() + "\"",
									null, toServ);
							Server srv = new Server(response.getId(), response, p);
							servers.put(response.getId(), srv);
							then.accept(srv);
						} catch (IOException ex) {
							LOG.error("{}: An error has occured while moving directory {} to {}", response.getId(),
									srcServ.getAbsolutePath(), toServ.getAbsolutePath());
							LOG.error("", ex);
							// Free port
							freePort(response.getPort());
							// Send request to delete the file
							main.getNetworkManager().network().getServerService().asyncDeleteServer(response.getId(),
									null);
							deleteServerDirectory(response.getId());
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
	 * After 20 seconds, the method
	 * {@link #forceClose(ServerProcess, Runnable, boolean)} is called to close the
	 * server. This method create a new Thread and execute all things in async mode
	 * if sync is false
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
	 * Force close a server by killing his process, sending a close request to the
	 * API and deleting the server directory<br />
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
			// Delete Server Process
			deleteServerProcess(srv);
			// Free port
			freePort(srv.getRestServer().getPort());
			// Send request to delete the file
			main.getNetworkManager().network().getServerService().asyncDeleteServer(srv.getId(), null);
			// Delete files and directories
			deleteServerDirectory(srv.getId());
			servers.remove(srv.getId());
			// All is ok
			if (then != null)
				then.run();
			// Process isn't alive
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

	/**
	 * Delete process<br />
	 * WARNING: This method isn't async, it's better to call this method in async
	 * mode
	 */
	private void deleteServerProcess(Server srv) {
		int count = 0;
		LOG.info("deleteServerProcess: srv = {}, hasProcess = {}", srv.getId(), srv.getProcess() != null);
		if (srv.getProcess() == null) {
			LOG.info("No Process linked to {} !", srv.getId());
			return;
		}
		while (srv.getProcess().isAlive()) {
			count++;
			// If after 20 seconds the process isn't killed, we'll just force kill the app
			LOG.info("{}: Destroy process: Try #{}", srv.getId(), count);
			if (count >= 20)
				srv.getProcess().destroyForcibly();
			else
				srv.getProcess().destroy();
			try {
				if (srv.getProcess().isAlive())
					Thread.sleep(1000);
			} catch (InterruptedException ex) {
				// No need to catch
			}
		}
	}

	/**
	 * Delete server directory<br />
	 * WARNING: This method isn't async, it's better to call this method in async
	 * mode
	 */
	private void deleteServerDirectory(String id) {
		LOG.info("{}: Process destroid, deleting the jar file", id);
		File directory = new File(toDir, id);
		File jar = new File(directory, "minecraft_server.jar");
		int count = 0;
		while (jar.exists()) {
			count++;
			LOG.info("{}: Deleting server file: Try #{}", id, count);
			try {
				if (!jar.delete())
					LOG.error("{}: Error while deleting jar file", id);
			} catch (Exception ex) {
				LOG.error("{}: Error while deleting jar file", id);
				LOG.error("", ex);
			}
			try {
				if (jar.exists())
					Thread.sleep(1000);
			} catch (InterruptedException ex) {
				// No need to catch
			}
		}
		LOG.info("{}: Jar file deleted, deleting the directory", id);
		count = 0;
		while (directory.exists()) {
			count++;
			LOG.info("{}: Deleting server directory: Try #{}", id, count);
			try {
				FileUtils.deleteDirectory(directory);
			} catch (Exception ex) {
				LOG.error("{}: Error while deleting server directory", id);
				LOG.error("", ex);
			}
			try {
				if (directory.exists())
					Thread.sleep(1000);
			} catch (InterruptedException ex) {
				// No need to catch
			}
		}
		LOG.info("{}: Directory deleted", id);
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
