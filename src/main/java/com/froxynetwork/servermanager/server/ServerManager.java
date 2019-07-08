package com.froxynetwork.servermanager.server;

import java.io.File;
import java.util.HashMap;
import java.util.function.Consumer;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.froxynetwork.froxynetwork.network.output.Callback;
import com.froxynetwork.froxynetwork.network.output.RestException;
import com.froxynetwork.froxynetwork.network.output.data.server.ServerDataOutput;
import com.froxynetwork.froxynetwork.network.output.data.server.ServerDataOutput.Server;
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
	private boolean[] availablePort;

	public ServerManager(Main main, File srvDir, File toDir, int lowPort, int highPort, boolean deleteDirectories,
			boolean deleteFiles) {
		servers = new HashMap<>();
		this.main = main;
		this.srvDir = srvDir;
		this.toDir = toDir;
		this.lowPort = lowPort;
		this.highPort = highPort;
		this.availablePort = new boolean[highPort - lowPort + 1];
		for (int i = 0; i < availablePort.length; i++)
			availablePort[i] = true;
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
		int port = -1;
		for (int i = 0; i < availablePort.length && port == -1; i++)
			if (availablePort[i])
				port = i + lowPort;
		if (port == -1) {
			// Not available port
			LOG.error("Not available port (opened)");
			error.run();
			return;
		}
		openServer(type, port, then, error);
	}

	private void openServer(String type, int port, Consumer<Server> then, Runnable error) {
		availablePort[port - lowPort] = false;
		LOG.info("Using port {}", port);
		main.getNetworkManager().network().getServerService().asyncAddServer(type.toUpperCase() + "_" + port, type,
				port, new Callback<ServerDataOutput.Server>() {

					@Override
					public void onResponse(Server response) {
						// Ok
						LOG.info("Server created on REST server, id = {}, creationTime = {}", response.getId(),
								response.getCreationTime());
						// TODO
						then.accept(response);
					}

					@Override
					public void onFailure(RestException ex) {
						// Error
						LOG.error(
								"Error while asking REST server for type = {} and port = {}: return code = {}, error message = {}",
								type, port, ex.getError().getCode(), ex.getError().getErrorMessage());
						LOG.error("", ex);
						availablePort[port - lowPort] = true;
						LOG.info("Port {} is now free", port);
						error.run();
					}

					@Override
					public void onFatalFailure(Throwable t) {
						// Fatal
						LOG.error("Fatal error while asking REST server for type = {} and port = {}", type, port);
						availablePort[port - lowPort] = true;
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
