package com.lupcode.HTTP.sse;

import java.net.http.HttpResponse;

/**
 * Interface to implement event stream listeners for the {@link HttpEventStreamClient}
 * @author LupCode.com (Luca Vogels)
 * @since 2020-12-22
 */
public interface EventStreamListener {
	
	/**
	 * Gets called if a new event has been received
	 * @param event Event that has been received
	 */
	public void onEvent(HttpEventStreamClient.Event event);
	
	/**
	 * Gets called if an error has occurred
	 * @param throwable Error that occurred
	 */
	public void onError(Throwable throwable);
	
	/**
	 * Gets called if {@link HttpEventStreamClient} lost connection and will reconnect
	 * @param response Last response received from server (may be null)
	 */
	public void onReconnect(HttpResponse<Void> response);
	
	/**
	 * Gets called if client has been closed
	 * @param response Last response received from server
	 */
	public void onClose(HttpResponse<Void> response);
}
