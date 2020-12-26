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
	public void onEvent(Event event) {}

	@Override
	public void onError(Throwable throwable) {
		throwable.printStackTrace();
	}
	
	@Override
	public void onReconnect(HttpResponse<Void> response) {}

	@Override
	public void onClose(HttpResponse<Void> response) {}
}
