package com.lupcode.HTTP.sse;

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
	 * Gets called if {@link HttpEventStreamClient} lost connection and starts reconnecting
	 */
	public void onReconnect();
	
	/**
	 * Gets called if client has been closed
	 */
	public void onClose();
}
