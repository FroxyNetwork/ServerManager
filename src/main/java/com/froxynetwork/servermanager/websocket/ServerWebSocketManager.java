package com.froxynetwork.servermanager.websocket;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.regex.Pattern;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.froxynetwork.froxynetwork.network.NetworkManager;
import com.froxynetwork.froxynetwork.network.output.Callback;
import com.froxynetwork.froxynetwork.network.output.RestException;
import com.froxynetwork.froxynetwork.network.output.data.ServerTesterDataOutput;
import com.froxynetwork.froxynetwork.network.output.data.ServerTesterDataOutput.ServerTester;
import com.froxynetwork.servermanager.Main;
import com.froxynetwork.servermanager.server.Server;

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
/**
 * The WebSocket Manager
 */
public class ServerWebSocketManager extends WebSocketServer {
	private final Logger LOG = LoggerFactory.getLogger(getClass());

	private Main main;
	@Getter
	private String url;
	@Getter
	private int port;
	private NetworkManager networkManager;
	@Getter
	private boolean stopped;
	private HashMap<WebSocket, WebSocketServerImpl> clients;

	public ServerWebSocketManager(Main main, String url, int port, NetworkManager networkManager) {
		super(new InetSocketAddress(url, port));
		this.main = main;
		LOG.info("WebSocket running on url = {} and port = {}", url, port);
		this.url = url;
		this.port = port;
		this.networkManager = networkManager;
		this.stopped = true;
		this.clients = new HashMap<>();
	}

	@Override
	public void start() {
		// Already running
		if (!stopped)
			return;
		stopped = false;
		super.start();
	}

	@Override
	public void stop() {
		try {
			stopped = true;
			super.stop();
		} catch (Exception ex) {
		}
	}

	@Override
	public void onStart() {
		LOG.info("Server started");
	}

	@Override
	public void onOpen(WebSocket conn, ClientHandshake handshake) {
		// New connection
		LOG.info("New connection: {} on port {}", conn.getRemoteSocketAddress().getHostString(),
				conn.getRemoteSocketAddress().getPort());
		WebSocketServerImpl wssi = new WebSocketServerImpl(conn);
		clients.put(conn, wssi);
	}

	private Pattern pattern = Pattern.compile(" ");

	@Override
	public void onMessage(WebSocket conn, String message) {
		// TODO CHANGE HERE (Auth information can be showed)
		LOG.info("Got message {} from {} port {}", message, conn.getRemoteSocketAddress().getHostString(),
				conn.getRemoteSocketAddress().getPort());
		WebSocketServerImpl wssi = clients.get(conn);
		if (wssi == null) {
			LOG.error("Server not registered !!!!!");
			return;
		}
		if (message == null || "".equalsIgnoreCase(message.trim()))
			return;
		int index = message.indexOf(' ');
		String channel = message.substring(0, index == -1 ? message.length() : index);
		String msg = message.substring(1 + (index == -1 ? channel.length() - 1 : index));
		onMessage(wssi, channel, msg);
	}

	@Override
	public void onError(WebSocket conn, Exception ex) {
		System.err.println("An error occured on connection " + conn.getRemoteSocketAddress() + ":" + ex);
	}

	@Override
	public void onClose(WebSocket conn, int code, String reason, boolean remote) {
		LOG.info("End connection: {} on port {}, code = {}, reason = {}, remote = {}",
				conn.getRemoteSocketAddress().getHostString(), conn.getRemoteSocketAddress().getPort(), code, reason,
				remote);
		WebSocketServerImpl wssi = clients.remove(conn);
		if (wssi == null) {
			LOG.error("Server not registered !!!!!");
			return;
		}
		if (wssi.getServer().isBungee()) {
			// Bungeecord disconnection
			LOG.error("A Bungeecord has disconnected !");
			if (wssi.getServer() != null)
				wssi.getServer().getVps().setBungee(null);
		}
		// Unlink the WebSocked client
		if (wssi.getServer() != null)
			wssi.getServer().setWebSocketServerImpl(null);
		wssi.setServer(null);

		wssi.onDisconnection(remote);
	}

	/**
	 * Called when a message is received from a client
	 * 
	 * @param wssi
	 * @param channel
	 * @param msg
	 */
	private void onMessage(WebSocketServerImpl wssi, String channel, String msg) {
		// Authentification
		if ("auth".equalsIgnoreCase(channel)) {
			if (wssi.isAuthentified()) {
				// Already authentified
				return;
			}
			String[] split = pattern.split(msg);
			if (split.length != 3) {
				// Wrong length, disconnecting ...
				wssi.disconnect();
				return;
			}
			String id = split[0];
			String clientId = split[1];
			String token = split[2];
			LOG.info("Client id {} try to connect to the WebSocket, checking ...", id);
			if ("bungeecord".equalsIgnoreCase(id)) {
				// Bungeecord

				networkManager.getNetwork().getServerTesterService().asyncCheckServer("bungeecord", clientId, token,
						new Callback<ServerTesterDataOutput.ServerTester>() {

							@Override
							public void onResponse(ServerTester st) {
								if (st.isOk()) {
									// Ok, send a response
									LOG.info("BungeeCord is now authentified ! Loading him ...", id);
									// Load server
									try {
										Server serv = main.getServerManager().getServer(id);
										// If server is not registered, we'll stop it (to prevent problems)
										if (serv == null) {
											// Just, how is it possible ?
											LOG.info(
													"Sending stop request to bungeecord {} because it's not linked to a registered bungee",
													id);
											wssi.sendMessage("MAIN", "stop", "");
											wssi.disconnect();
											return;
										}

										if (serv.getVps().getBungee() != null) {
											// Close
											LOG.error(
													"WARNING : A server is trying to connect as the bungee ! id = {}, clientId = {}, ip = {}",
													id, clientId,
													wssi.getClient().getRemoteSocketAddress().getHostString());
											wssi.sendMessage("MAIN", "stop", "");
											wssi.disconnect();
											return;
										}
										// From here the bungee is registered, we'll just link the bungee and the
										// WebSocket
										serv.setWebSocketServerImpl(wssi);
										wssi.setServer(serv);
										serv.getVps().setBungee(serv);
										LOG.info("Server loaded");

										// Done, sending connection ok
										wssi.sendMessage("MAIN", "connection", "ok");
									} catch (Exception ex) {
										LOG.error("Error while getting server {} :", id);
										LOG.error("", ex);
										// Disconnecting
										wssi.disconnect();
									}
								} else {
									// Not ok, disconnecting ...
									LOG.info("Client {} has send incorrect id / token", id);
									wssi.disconnect();
								}
							}

							@Override
							public void onFailure(RestException ex) {
								LOG.error("Client {} has send incorrect id / token (error {})", id,
										ex.getError().getErrorId());
								// Not ok, disconnecting ...
								wssi.disconnect();
							}

							@Override
							public void onFatalFailure(Throwable t) {
								LOG.error("Fatal error while checking client {} : ", id);
								LOG.error("", t);
								// Not ok, disconnecting ...
								wssi.disconnect();
							}
						});
				return;
			}
			networkManager.getNetwork().getServerTesterService().asyncCheckServer(id, clientId, token,
					new Callback<ServerTesterDataOutput.ServerTester>() {

						@Override
						public void onResponse(ServerTester st) {
							if (st.isOk()) {
								// Ok, send a response
								LOG.info("Client id {} is now authentified ! Loading server ...", id);
								try {
									Server serv = main.getServerManager().getServer(id);
									// If server is not registered, we'll stop it (to prevent problems)
									if (serv == null) {
										// Just, how is it possible ?
										LOG.info(
												"Sending stop request to server {} because it's not linked to a registered server",
												id);
										wssi.sendMessage("MAIN", "stop", "");
										wssi.disconnect();
										return;
									}
									// From here the server is registered, we'll just link the server and the
									// WebSocket
									serv.setWebSocketServerImpl(wssi);
									wssi.setServer(serv);
									LOG.info("Server loaded");
									// Done, sending connection ok
									wssi.sendMessage("MAIN", "connection", "ok");
								} catch (Exception ex) {
									LOG.error("Error while getting server {} :", id);
									LOG.error("", ex);
									// Disconnecting
									wssi.disconnect();
								}
							} else {
								// Not ok, disconnecting ...
								LOG.info("Client {} has send incorrect id / token", id);
								wssi.disconnect();
							}
						}

						@Override
						public void onFailure(RestException ex) {
							LOG.error("Client {} has send incorrect id / token (error {})", id,
									ex.getError().getErrorId());
							// Not ok, disconnecting ...
							wssi.disconnect();
						}

						@Override
						public void onFatalFailure(Throwable t) {
							LOG.error("Fatal error while checking client {} : ", id);
							LOG.error("", t);
							// Not ok, disconnecting ...
							wssi.disconnect();
						}
					});
		} else if ("register".equalsIgnoreCase(channel)) {
			// The user needs to be authentified
			if (wssi.isAuthentified())
				// TODO Add check for channels
				wssi.addChannelListener(msg);
		} else if ("unregister".equalsIgnoreCase(channel)) {
			wssi.removeChannelListener(msg);
		} else {
			// The user needs to be authentified
			if (!wssi.isAuthentified())
				return;
			// Send message
			for (WebSocketServerImpl client : clients.values())
				if (client.isListening(channel))
					client.sendMessage(wssi.getServer().getId(), channel, msg);
		}
	}
}
