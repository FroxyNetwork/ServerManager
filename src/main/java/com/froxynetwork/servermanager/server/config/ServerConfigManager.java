package com.froxynetwork.servermanager.server.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.froxynetwork.froxynetwork.network.output.Callback;
import com.froxynetwork.froxynetwork.network.output.RestException;
import com.froxynetwork.froxynetwork.network.output.data.server.config.ServerConfigDataOutput;
import com.froxynetwork.froxynetwork.network.output.data.server.config.ServerConfigDataOutput.ServersConfig;
import com.froxynetwork.froxynetwork.network.output.data.server.config.ServerConfigDataOutput.VpsConfigConfig;
import com.froxynetwork.servermanager.Main;

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
public class ServerConfigManager {
	private final Logger LOG = LoggerFactory.getLogger(getClass());

	private boolean actuallyReloading;

	private HashMap<String, ServerConfig> serversConfig;
	@Getter
	private List<ServerVps> vps;

	public ServerConfigManager() {
		this.serversConfig = new HashMap<>();
		this.vps = new ArrayList<>();
		this.actuallyReloading = false;
	}

	public void reload(Runnable then) throws RestException, Exception {
		if (actuallyReloading)
			throw new IllegalStateException("Servers are actually reloading, please retry later");
		actuallyReloading = true;
		LOG.info("Initializing Server Config");
		// Call retrofit
		Main.get().getNetworkManager().network().getServerConfigService()
				.asyncGetServerConfig(new Callback<ServerConfigDataOutput.ServersConfig>() {

					@Override
					public void onResponse(ServersConfig response) {
						HashMap<String, ServerConfig> newServersConfig = new HashMap<>();
						try {
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
								ServerConfig newSc = new ServerConfig(id, database, sc.getMin(), sc.getMax());
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
											System.arraycopy(vDatabase, 0, newDatabase, database.length,
													vDatabase.length);
										}
										ServerConfig vServerConfig = new ServerConfig(vId, newDatabase,
												variant.getMin(), variant.getMax());
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
							List<ServerVps> newVps = new ArrayList<>();
							for (com.froxynetwork.froxynetwork.network.output.data.server.config.ServerConfigDataOutput.VpsConfig vc : response
									.getVps()) {
								ServerVps vps = new ServerVps(vc.getId(), vc.getMaxServers());
								for (VpsConfigConfig c : vc.getConfig()) {
									vps.setMin(c.getType(), c.getMin());
									vps.setMax(c.getType(), c.getMax());
								}
								newVps.add(vps);
							}
							// Save
							vps = newVps;
							LOG.info("Got {} vps", vps.size());
							LOG.info("Server Config initialized");
							then.run();
						} catch (Exception ex) {
							// Unknown exception
							LOG.error("", ex);
						} finally {
							actuallyReloading = false;
						}
					}

					@Override
					public void onFatalFailure(Throwable t) {
						LOG.error("Fatal error: ", t);
						actuallyReloading = false;
						then.run();
					}

					@Override
					public void onFailure(RestException ex) {
						LOG.error("Failure: ", ex);
						actuallyReloading = false;
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

	public Collection<ServerConfig> getAll() {
		return serversConfig.values();
	}

	public ServerVps getVps(String vps) {
		for (ServerVps v : this.vps)
			if (v.getId().equalsIgnoreCase(vps))
				return v;
		return null;
	}
}
