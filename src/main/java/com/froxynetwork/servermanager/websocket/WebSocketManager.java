package com.froxynetwork.servermanager.websocket;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashMap;

import org.java_websocket.framing.CloseFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.froxynetwork.froxynetwork.network.websocket.WebSocketFactory;
import com.froxynetwork.froxynetwork.network.websocket.WebSocketServer;
import com.froxynetwork.froxynetwork.network.websocket.WebSocketServerImpl;
import com.froxynetwork.froxynetwork.network.websocket.auth.WebSocketTokenAuthentication;
import com.froxynetwork.servermanager.Main;
import com.froxynetwork.servermanager.server.Server;

import lombok.Getter;

/**
 * MIT License
 *
 * Copyright (c) 2020 FroxyNetwork
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
public class WebSocketManager {
	private final Logger LOG = LoggerFactory.getLogger(getClass());
	@Getter
	private WebSocketServer webSocketServer;
	private HashMap<WebSocketServerImpl, Server> links;
	@Getter
	private String url;
	@Getter
	private int port;

	public WebSocketManager(String url, int port) {
		this.url = url;
		this.port = port;
		links = new HashMap<>();
		webSocketServer = WebSocketFactory.server(new InetSocketAddress(url, port),
				new WebSocketTokenAuthentication(Main.get().getNetworkManager()));
		webSocketServer.registerWebSocketConnection(this::onNewConnection);
		webSocketServer.start();
	}

	private void onNewConnection(WebSocketServerImpl wssi) {
		wssi.registerWebSocketAuthentication(() -> {
			Object obj = wssi.get(WebSocketTokenAuthentication.TOKEN);
			String id = obj == null ? null : obj.toString();
			if (id == null || "".equalsIgnoreCase(id.trim())) {
				// Wtf ?
				LOG.error("WebSocket is authentified but doesn't have an id ! Closing it");
				wssi.disconnect(CloseFrame.NORMAL, "Id doesn't exist");
				return;
			}
			Server srv = Main.get().getServerManager().getServer(id);
			if (srv != null) {
				// This is a server that is running but that lost his connection
				// Let's check if a connection exists
				if (srv.getWebSocket() != null && srv.getWebSocket().isConnected()) {
					// A connection exists, close this one
					LOG.error(
							"WebSocket tried to authenticate as server {} but this server is already linked ! Closing it",
							srv.getId());
					wssi.disconnect(CloseFrame.NORMAL, "This server is already linked");
				} else {
					// A connection doesn't exist
					srv.resumeWebSocket(wssi);
					links.put(wssi, srv);
				}
			} else {
				srv = Main.get().getServerManager().getCreatingServer(id);
				if (srv == null) {
					// Server doesn't exist, disconnecting
					LOG.error("WebSocket tried to authenticate as server {} but this server doesn't exist ! Closing it",
							id);
					wssi.disconnect(CloseFrame.NORMAL, "This id doesn't exist");
					return;
				}
				// A connection should not exists so we don't have to check for it
				Main.get().getServerManager().loadServer(srv, wssi);
				links.put(wssi, srv);
			}
		});
		wssi.registerWebSocketDisconnection(remote -> {
			links.remove(wssi);
			Object obj = wssi.get(WebSocketTokenAuthentication.TOKEN);
			if (obj == null)
				return;
			Server srv = Main.get().getServerManager().getServer(obj.toString());
			if (srv == null)
				return;
			srv.resumeWebSocket(null);
			wssi.closeAll();
		});
	}

	public Server get(WebSocketServerImpl wssi) {
		return links.get(wssi);
	}

	public void stop() {
		for (WebSocketServerImpl wssi : links.keySet())
			wssi.closeAll();
		try {
			webSocketServer.stop();
		} catch (IOException ex) {
			ex.printStackTrace();
		} catch (InterruptedException ex) {
			ex.printStackTrace();
		}
	}
}
