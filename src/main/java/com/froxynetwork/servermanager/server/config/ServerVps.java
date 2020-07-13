package com.froxynetwork.servermanager.server.config;

import java.util.HashMap;
import java.util.Map.Entry;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

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
@ToString
@EqualsAndHashCode
public class ServerVps {
	@Getter
	private String id;
	@Getter
	private int maxServers;
	@ToString.Exclude
	private HashMap<String, Integer> min;
	@ToString.Exclude
	private HashMap<String, Integer> max;

	public ServerVps(String id, int maxServers) {
		this.id = id;
		this.maxServers = maxServers;
		this.min = new HashMap<>();
		this.max = new HashMap<>();
	}

	public int getMin(String id) {
		return min.get(id);
	}

	public void setMin(String id, int min) {
		this.min.put(id, min);
	}

	public int getMax(String id) {
		return max.get(id);
	}

	public void setMax(String id, int max) {
		this.max.put(id, max);
	}

	@ToString.Include(name = "min")
	public String min() {
		StringBuilder sb = new StringBuilder("min = [");
		for (Entry<String, Integer> e : min.entrySet())
			sb.append(e.getKey()).append(" = ").append(e.getValue()).append(", ");
		sb.setLength(sb.length() - 2);
		sb.append("]");
		return sb.toString();
	}

	@ToString.Include(name = "max")
	public String max() {
		StringBuilder sb = new StringBuilder("max = [");
		for (Entry<String, Integer> e : max.entrySet())
			sb.append(e.getKey()).append(" = ").append(e.getValue()).append(", ");
		sb.setLength(sb.length() - 2);
		sb.append("]");
		return sb.toString();
	}
}
