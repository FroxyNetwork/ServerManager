package com.froxynetwork.serverManager;

import com.froxynetwork.serverManager.network.output.Callback;
import com.froxynetwork.serverManager.network.output.PlayerDataOutput.Player;
import com.froxynetwork.serverManager.network.output.RestException;
import com.froxynetwork.serverManager.network.service.ServiceManager;

import lombok.Getter;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

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
public class App {
	@Getter
	private static App instance;
	@Getter
	private Retrofit retrofit;
	@Getter
	private ServiceManager serviceManager;

	public App() throws Exception {
		instance = this;
		// TODO URL in config file
		retrofit = new Retrofit.Builder().addConverterFactory(GsonConverterFactory.create())
				.baseUrl("http://localhost/").build();
		serviceManager = new ServiceManager();
		System.out.println("SENDING ASYNC GET PLAYER");
		serviceManager.getPlayerService().asyncGetPlayer("0ddlyoko", new Callback<Player>() {
			@Override
			public void onResponse(Player response) {
				System.out.println("ASYNC response: " + response);
			}

			@Override
			public void onFailure(RestException ex) {
				System.out
						.println("Error: " + ex.getError().getCode() + ", message: " + ex.getError().getErrorMessage());
			}

			@Override
			public void onFatalFailure(Throwable t) {
				System.out.println("Error: " + t);
			}
		});
		System.out.println("SENDING SYNC GET PLAYER");
		Player response = serviceManager.getPlayerService().syncGetPlayer("0ddlyoko");
		System.out.println("SYNC response: " + response);
	}

	public static void main(String[] args) throws Exception {
		new App();
	}
}
