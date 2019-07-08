package com.froxynetwork.servermanager.server;

import java.util.Date;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class Server {
	private int id;
	private String name;
	private String type;
	private int port;
	@Setter
	private ServerStatus status;
	private Date creationTime;
	@Setter
	private Date endTime;

	public enum ServerStatus {
		STARTING, WAITING, STARTED, ENDING, ENDED;

		/**
		 * Check if b is after or equals to a
		 * 
		 * @param a
		 *            The first status
		 * @param b
		 *            The second status
		 * 
		 * @return true if b is after or equals to a
		 */
		public static boolean isAfter(ServerStatus a, ServerStatus b) {
			return b.ordinal() >= a.ordinal();
		}
	}
}
