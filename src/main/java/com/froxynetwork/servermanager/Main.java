package com.froxynetwork.servermanager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.froxynetwork.froxynetwork.network.NetworkManager;

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
public class Main {

	private final Logger LOG = LoggerFactory.getLogger(getClass());

	@Getter
	private NetworkManager networkManager;

	public Main(String[] args) {
		LOG.info("ServerManager initialization");
		if (args == null || args.length != 3) {
			LOG.error("Invalid argument number, please enter correct arguments ! (<url> <client_id> <client_secret>)");
			System.exit(1);
		}
		String url = args[0];
		String clientId = args[1];
		String clientSecret = args[2];
		if (LOG.isInfoEnabled()) {
			LOG.info("url = {}, client_id = {}, client_secret = {}", url, clientId, clientSecret == null ? "null" : "<hidden>");
			LOG.info("Initializing NetworkManager");
		}
		try {
			networkManager = new NetworkManager(url, clientId, clientSecret);
		} catch (Exception ex) {
			LOG.error("An error has occured while initializing NetworkManager: ", ex);
			System.exit(1);
		}
		LOG.info("NetworkManager initialized");
		LOG.info("ServerManager initialized");
	}

	public static void main(String[] args) {
		new Main(args);
	}
}
