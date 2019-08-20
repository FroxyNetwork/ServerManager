package com.froxynetwork.servermanager.server.config;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.froxynetwork.froxynetwork.network.output.Callback;
import com.froxynetwork.froxynetwork.network.output.RestException;
import com.froxynetwork.froxynetwork.network.output.data.server.config.ServerConfigDataOutput;
import com.froxynetwork.froxynetwork.network.output.data.server.config.ServerConfigDataOutput.ServersConfig;
import com.froxynetwork.servermanager.Main;
import com.froxynetwork.servermanager.server.config.ServerConfig.Loaded;

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
public class ServerConfigManager {
	private final Logger LOG = LoggerFactory.getLogger(getClass());

	private Main main;
	private int downloadThread;
	private HashMap<String, ServerConfig> serversConfig;

	public ServerConfigManager(Main main, int downloadThread) {
		this.main = main;
		this.downloadThread = downloadThread;
		this.serversConfig = new HashMap<>();
	}

	public void reload(Runnable then) throws RestException, Exception {
		LOG.info("Initializing Server Config");
		// Call retrofit
		HashMap<String, ServerConfig> newServersConfig = new HashMap<>();
		main.getNetworkManager().network().getServerConfigService()
				.asyncGetServerConfig(new Callback<ServerConfigDataOutput.ServersConfig>() {

					@Override
					public void onResponse(ServersConfig response) {

						int countType = 0;
						int countSubType = 0;
						for (com.froxynetwork.froxynetwork.network.output.data.server.config.ServerConfigDataOutput.ServerConfig sc : response
								.getTypes()) {
							countType++;
							String id = sc.getId();
							LOG.info("Loading {}", id);
							String[] database = sc.getDatabase();

							com.froxynetwork.froxynetwork.network.output.data.server.config.ServerConfigDataOutput.ServerConfig[] variants = sc
									.getVariants();
							ServerConfig newSc = new ServerConfig(id, database);
							newServersConfig.put(id, newSc);
							if (variants != null) {
								for (com.froxynetwork.froxynetwork.network.output.data.server.config.ServerConfigDataOutput.ServerConfig variant : variants) {
									countSubType++;
									String vId = variant.getId();
									LOG.info("Loading {} (variant of {})", vId, id);
									String[] vDatabase = variant.getDatabase();
									String[] newDatabase;
									if (vDatabase == null || vDatabase.length == 0)
										newDatabase = database;
									else if (database == null || database.length == 0)
										newDatabase = vDatabase;
									else {
										// Concatenate the both array
										newDatabase = Arrays.copyOf(database, database.length + vDatabase.length);
										System.arraycopy(vDatabase, 0, newDatabase, database.length, vDatabase.length);
									}
									ServerConfig vServerConfig = new ServerConfig(vId, newDatabase);
									vServerConfig.setParent(newSc);
									newSc.addChildren(vServerConfig);
									newServersConfig.put(vId, vServerConfig);
									LOG.info("{} loaded", vId);
								}
							}
							LOG.info("{} loaded", id);
						}
						LOG.info("Loaded {} types and {} subtypes (total: {})", countType, countSubType,
								(countType + countSubType));
						// Save
						serversConfig = newServersConfig;
						LOG.info("Downloading servers");
						File outputDir = main.getServerManager().getSrvDir();
						Collection<ServerConfig> colServerConfig = newServersConfig.values();
						// We use ForkJoinPool to execute the download in parallel
						// See here: https://stackoverflow.com/a/33076283/8008251
						ForkJoinPool fork = new ForkJoinPool(downloadThread);
						try {
							List<ForkJoinTask<ServerConfig>> forks = new ArrayList<>();
							for (ServerConfig sc : colServerConfig) {
								forks.add(fork.submit(() -> {
									try {
										LOG.info("Downloading {}.zip", sc.getType());
										main.getNetworkManager().network().getServerDownloadService()
												.syncDownloadServer(sc.getType(),
														new File(outputDir, sc.getType() + ".zip"));
										// Ok
										sc.setLoaded(Loaded.DONE);
										LOG.info("{}.zip downloaded", sc.getType());
									} catch (RestException ex) {
										sc.setLoaded(Loaded.ERROR);
										LOG.error("Error while downloading server type {}:", sc.getType());
										LOG.error("", ex);
									} catch (Exception ex) {
										sc.setLoaded(Loaded.ERROR);
										LOG.error("Error while downloading server type {}:", sc.getType());
										LOG.error("", ex);
									}
									return sc;
								}));
							}
							// Wait for each
							for (ForkJoinTask<ServerConfig> forkServerConfig : forks)
								forkServerConfig.get();
							// Done
						} catch (InterruptedException ex) {
							ex.printStackTrace();
						} catch (ExecutionException ex) {
							ex.printStackTrace();
						}
						LOG.info("Server Config initialized");
						then.run();
					}

					@Override
					public void onFatalFailure(Throwable t) {
						LOG.error("Fatal error: ", t);
						then.run();
					}

					@Override
					public void onFailure(RestException ex) {
						LOG.error("Failure: ", ex);
						then.run();
					}
				});
	}

	public ServerConfig get(String type) {
		return serversConfig.get(type);
	}

	public boolean exist(String type) {
		return serversConfig.containsKey(type);
	}

	public boolean isLoaded(String type) {
		return exist(type) && serversConfig.get(type).getLoaded() == Loaded.DONE;
	}

	public Collection<ServerConfig> getAll() {
		return serversConfig.values();
	}
}
