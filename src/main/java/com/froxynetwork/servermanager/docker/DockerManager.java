package com.froxynetwork.servermanager.docker;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.froxynetwork.servermanager.Main;
import com.froxynetwork.servermanager.server.Server;
import com.froxynetwork.servermanager.server.Vps;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.exception.NotModifiedException;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Event;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.Info;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.api.model.Ports.Binding;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.command.EventsResultCallback;

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
public class DockerManager {
	private Logger LOG = LoggerFactory.getLogger(getClass());

	private Vps vps;
	private String host;
	private DockerClientConfig config;
	private DockerClient client;
	private boolean connected;

	public DockerManager(Vps vps) {
		this.vps = vps;
		this.connected = false;
	}

	public Vps getVps() {
		return vps;
	}

	public void initializeConnection(String host, String certPath) throws Exception {
		this.host = host;
		this.config = DefaultDockerClientConfig.createDefaultConfigBuilder().withDockerHost(host)
				.withDockerTlsVerify(true).withDockerCertPath(certPath).build();
		reconnect();
	}

	public void reconnect() throws Exception {
		if (connected) {
			// Already connected, disconnecting
			try {
				client.close();
			} catch (IOException ex) {
				// Silence Exception
			}
		}
		connected = false;
		client = DockerClientBuilder.getInstance(config).build();
		Info info = client.infoCmd().exec();
		if (info == null) {
			// Error
			throw new Exception("Cannot contact Docker Daemon at " + host);
		}
		connected = true;

		// Events
		EventsResultCallback callback = new EventsResultCallback() {

			@Override
			public void onNext(Event e) {
				System.out.println("Event: " + e.getAction());
				if ("stop".equalsIgnoreCase(e.getAction())) {
					// Docker stop event
					String id = e.getId();
					System.out.println("STOP EVENT: actor = " + e.getActor() + ", from = " + e.getFrom() + ", id = "
							+ e.getId() + ", status = " + e.getStatus() + ", type = " + e.getType() + ", node = "
							+ e.getNode());
					Server srv = vps.getServerFromDocker(id);
					if (srv == null) {
						LOG.error("Docker {} has shutdown but is not linked to any server !", id);
						return;
					} else {
						// Stop server
						if (!srv.isClosed())
							Main.get().getServerManager().closeServer(srv, () -> {
								LOG.info("Server closed");
							});
					}
				}
			}
		};
		try {
			client.eventsCmd().exec(callback).close();
			LOG.info("Event registered");
		} catch (IOException ex) {
			LOG.error("IOException while registering docker events: ", ex);
		}
	}

	/**
	 * Start a container in async mode.
	 * 
	 * @param name      The name of the container
	 * @param configure Used to configure the container before starting him
	 *                  (environment)
	 * @param then      The action to execute once the docker is run
	 */
	public void startContainer(String name, int port, Consumer<CreateContainerCmd> configure,
			Consumer<CreateContainerResponse> then) {
		if (!connected)
			throw new IllegalStateException("Not connected !");
		new Thread(() -> {
			// LOG
			LOG.info("Starting container {}", name);
			ExposedPort tcp25565 = ExposedPort.tcp(25565);
			Ports portBindings = new Ports();
			portBindings.bind(tcp25565, Binding.bindPort(port));
			CreateContainerCmd cmd = client.createContainerCmd(name);
			configure.accept(cmd);
			CreateContainerResponse container = cmd.withExposedPorts(tcp25565).withPortBindings(portBindings).exec();
			client.startContainerCmd(container.getId()).exec();
			LOG.info("Container {} started", container.getId());
			then.accept(container);
		}, "startContainer - " + name).run();
	}

	/**
	 * Stop a container
	 * 
	 * @param id   The id of the container
	 * @param then The action to execute once the container is stopped
	 * @param sync If true, run in sync mode
	 */
	public void stopContainer(String id, Runnable then, boolean sync) {
		if (!connected)
			throw new IllegalStateException("Not connected !");
		Runnable run = () -> {
			// LOG
			LOG.info("Stopping container {}", id);
			try {
				client.stopContainerCmd(id).withTimeout(2).exec();
			} catch (NotFoundException | NotModifiedException ex) {
				LOG.info("Docker {} already removed", id);
			} catch (Exception ex) {
				LOG.error("Error while stopping container {} :", id);
				LOG.error("", ex);
			}
			try {
				client.removeContainerCmd(id).exec();
			} catch (NotFoundException ex) {
				LOG.info("Docker {} already removed", id);
			} catch (Exception ex) {
				LOG.error("Error while removing container {} :", id);
				LOG.error("", ex);
			}
			LOG.info("Container {} stopped and removed", id);
			then.run();
		};
		if (sync)
			run.run();
		else
			new Thread(run, "stopContainer - " + id).start();
	}

	/**
	 * Return running dockers<br />
	 * This method send a request to the Docker Daemon to retrieve 
	 * 
	 * @return Running dockers
	 */
	public void getDockers(Consumer<List<Container>> result) {
		if (!connected)
			throw new IllegalStateException("Not connected !");
		new Thread(() -> {
			List<Container> containers = null;
			try {
				containers = client.listContainersCmd().exec();
			} catch (Exception ex) {
				LOG.error("An error has occured while retrieving running dockers: ", ex);
			} finally {
				result.accept(containers);
			}
		}).start();
	}

	/**
	 * Close the client
	 */
	public void close() {
		if (!connected)
			return;
		try {
			client.close();
		} catch (IOException ex) {
			// Silence IOException
		}
	}
}
