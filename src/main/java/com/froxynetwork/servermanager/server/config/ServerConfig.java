package com.froxynetwork.servermanager.server.config;

import java.util.ArrayList;
import java.util.List;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

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
@Getter
@ToString
@EqualsAndHashCode
public class ServerConfig {
	private String type;
	private String[] database;
	private List<ServerConfig> childrens;
	@Setter
	private ServerConfig parent;
	@Setter
	private Loaded loaded;

	public ServerConfig(String type, String[] database) {
		this.type = type;
		this.database = database;
		this.childrens = new ArrayList<>();
		this.loaded = Loaded.NOT_DOWNLOADED;
	}

	public void addChildren(ServerConfig serverConfig) {
		this.childrens.add(serverConfig);
	}

	public enum Loaded {
		DONE,
		NOT_DOWNLOADED,
		ERROR
	}
}
