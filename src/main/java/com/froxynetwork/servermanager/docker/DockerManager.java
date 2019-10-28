package com.froxynetwork.servermanager.docker;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
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
	private DockerClient client;
	private List<String> runningContainers;

	public DockerManager() {
		runningContainers = new ArrayList<>();
	}

	public void initializeConnection(String host, String certPath) {
		DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().withDockerHost(host)
				.withDockerTlsVerify(true).withDockerCertPath(certPath).build();
		client = DockerClientBuilder.getInstance(config).build();
		Info info = client.infoCmd().exec();
		System.out.println(info);

		// Events
		EventsResultCallback callback = new EventsResultCallback() {

			@Override
			public void onNext(Event e) {
				System.out.println("Event: " + e.getAction());
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
	 * @param name The name of the container
	 */
	public void startContainer(String name, Consumer<CreateContainerResponse> then) {
		new Thread(() -> {
			// LOG
			LOG.info("Starting container {}", name);
			ExposedPort tcp25565 = ExposedPort.tcp(25565);
			Ports portBindings = new Ports();
			// TODO Change port
			portBindings.bind(tcp25565, Binding.bindPort(25565));
			CreateContainerResponse container = client.createContainerCmd(name).withEnv("EULA=true")
					.withExposedPorts(tcp25565).withPortBindings(portBindings).exec();
			runningContainers.add(container.getId());
			client.startContainerCmd(container.getId()).exec();
			LOG.info("Container started");
		}, "startContainer - " + name).run();
	}
}
