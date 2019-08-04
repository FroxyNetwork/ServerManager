package com.froxynetwork.servermanager.server;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.froxynetwork.froxynetwork.network.output.Callback;
import com.froxynetwork.froxynetwork.network.output.RestException;
import com.froxynetwork.froxynetwork.network.output.data.server.ServerDataOutput;
import com.froxynetwork.froxynetwork.network.output.data.server.ServerDataOutput.Server;
import com.froxynetwork.servermanager.Main;
import com.froxynetwork.servermanager.util.ZipUtil;

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
	private Main main;

	// Servers with id
	private HashMap<Integer, Server> servers;
	// Directory where are servers to copy
	private File srvDir;
	// Directory where will be servers
	private File toDir;
	// The lowest port to use
	private int lowPort;
	// The highest port to use
	private int highPort;
	// Used to check the availability of ports
	private List<Integer> availablePort;

	public ServerManager(Main main, File srvDir, File toDir, int lowPort, int highPort, boolean deleteDirectories,
			boolean deleteFiles) {
		servers = new HashMap<>();
		this.main = main;
		this.srvDir = srvDir;
		this.toDir = toDir;
		this.lowPort = lowPort;
		this.highPort = highPort;
		this.availablePort = new ArrayList<>(highPort - lowPort + 1);
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
						FileUtils.deleteDirectory(f);
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
	 * Try to open a server
	 * 
	 * @param type
	 *            The type of server (HUB, KOTH, ...)
	 * @param then
	 *            The action to execute once the server is created and launched
	 * @param error
	 *            The action to execute if error occures
	 */
	public synchronized void openServer(String type, Consumer<Server> then, Runnable error) {
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
		LOG.info("Using port {}", port);
		main.getNetworkManager().network().getServerService().asyncAddServer(type.toUpperCase() + "_" + port, type,
				port, new Callback<ServerDataOutput.Server>() {

					@Override
					public void onResponse(Server response) {
						// Ok
						LOG.info("Server created on REST server, id = {}, creationTime = {}", response.getId(),
								response.getCreationTime());
						File srcServ = new File(srvDir, type + ".zip");
						File toServ = new File(toDir, response.getId());
						LOG.info("{}: Extracting file {} to directory {}", response.getId(), srcServ.getAbsolutePath(),
								toServ.getAbsolutePath());
						if (!srcServ.exists()) {
							// Source server doesn't exist
							freePort(port);
							LOG.error("{}: Source server doesn't exists !", response.getId());
							main.getNetworkManager().network().getServerService().asyncDeleteServer(response.getId(),
									null);
							error.run();
							return;
						}
						if (!srcServ.isFile()) {
							// Source server is not a directory
							freePort(port);
							LOG.error("{}: Source server is not a file !", response.getId());
							main.getNetworkManager().network().getServerService().asyncDeleteServer(response.getId(),
									null);
							error.run();
							return;
						}
						if (!srcServ.canRead()) {
							// Cannot read source server
							freePort(port);
							LOG.error("{}: Source server cannot be read (no read access) !", response.getId());
							main.getNetworkManager().network().getServerService().asyncDeleteServer(response.getId(),
									null);
							error.run();
							return;
						}
						if (toServ.exists()) {
							// Directory already exists
							freePort(port);
							LOG.error("{}: Destination server directory already exist !", response.getId());
							main.getNetworkManager().network().getServerService().asyncDeleteServer(response.getId(),
									null);
							error.run();
							return;
						}
						try {
							try {
								ZipUtil.unzipAndMove(srcServ, toServ);
							} catch (IOException ex) {
								// Cannot read source server
								freePort(port);
								LOG.error("{}: An error has occured while extracting archive {} to {}",
										response.getId(), srcServ.getAbsolutePath(), toServ.getAbsolutePath());
								LOG.error("", ex);
								main.getNetworkManager().network().getServerService()
										.asyncDeleteServer(response.getId(), null);
								error.run();
								return;
							}
							if (!toServ.exists() || !toServ.isDirectory()) {
								// Whut ????
								freePort(port);
								LOG.error("{}: An unknown error has occured while creating directories !",
										response.getId());
								main.getNetworkManager().network().getServerService()
										.asyncDeleteServer(response.getId(), null);
								error.run();
								return;
							}
							// Done, now let's configure the server
							// server.properties
							LOG.info("{}: Modifying server.properties file", response.getId());
							PrintWriter writerServer = new PrintWriter(
									new FileOutputStream(new File(toServ, "server.properties"), true));
							writerServer.println("server-name=" + response.getName());
							writerServer.println("server-port=" + response.getPort());
							writerServer.close();
							LOG.info("{}: File server.properties has succesfully been edited", response.getId());
							LOG.info("{}: Saving auth access", response.getId());
							File authFile = new File(toServ + File.separator + "plugins" + File.separator
									+ "FroxyNetwork" + File.separator + "auth");
							authFile.getParentFile().mkdirs();
							if (!authFile.createNewFile()) {
								freePort(port);
								LOG.error("{}: Cannot create auth file !", response.getId());
								main.getNetworkManager().network().getServerService()
										.asyncDeleteServer(response.getId(), null);
								error.run();
								return;
							}
							PrintWriter writerConfig = new PrintWriter(new FileOutputStream(authFile, true));
							writerConfig.write(response.getAuth().getClientId() + '\n');
							writerConfig.write(response.getAuth().getClientSecret() + '\n');
							writerConfig.close();
							LOG.info("{}: Auth access saved", response.getId());
							then.accept(response);
						} catch (IOException ex) {
							LOG.error("{}: An error has occured while moving directory {} to {}", response.getId(),
									srcServ.getAbsolutePath(), toServ.getAbsolutePath());
							LOG.error("", ex);
							freePort(port);
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
						freePort(port);
						LOG.error("Port {} is now free", port);
						error.run();
					}
				});
	}

	/**
	 * Close a server
	 * 
	 * @param srv
	 *            The server
	 */
	public void closeServer(Server srv) {

	}

	private int getAndLockPort() {
		synchronized (availablePort) {
			if (availablePort.size() == 0)
				return -1;
			return availablePort.remove(0);
		}
	}

	private void freePort(int port) {
		synchronized (availablePort) {
			availablePort.add(port);
		}
	}

	/**
	 * Close a server
	 * 
	 * @param id
	 *            The id of the server
	 */
	public void closeServer(int id) {
		closeServer(servers.get(id));
	}
}
