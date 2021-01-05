package com.lupcode.HTTP.sse;

import java.net.http.HttpResponse;

import com.lupcode.HTTP.sse.HttpEventStreamClient.Event;

/**
 * Base class for event stream listeners that can be used for the {@link HttpEventStreamClient}
 * @author LupCode.com (Luca Vogels)
 * @since 2020-12-22
 */
public abstract class EventStreamAdapter implements EventStreamListener {

	@Override
	public void onEvent(HttpEventStreamClient client, long eventID, Event event) {}

	@Override
	public void onError(HttpEventStreamClient client, Throwable throwable) {
		throwable.printStackTrace();
	}
	
	@Override
	public void onReconnect(HttpEventStreamClient client, HttpResponse<Void> response, boolean hasReceivedEvents, long lastEventID) {}
	
	@Override
	public void onClose(HttpEventStreamClient client, HttpResponse<Void> response) {}
}
