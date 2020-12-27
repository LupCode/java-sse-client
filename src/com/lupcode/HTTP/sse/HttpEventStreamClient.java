package com.lupcode.HTTP.sse;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * HTTP Client that can listen for Server-Sent Events (SSE).
 * Implements full protocol and supports automatic reconnect.
 * @author LupCode.com (Luca Vogels)
 * @since 2020-12-26
 */
public class HttpEventStreamClient {
	
	/**
	 * Event that gets received by the {@link HttpEventStreamClient}
	 * @author LupCode.com (Luca Vogels)
	 * @since 2020-12-22
	 */
	public class Event {
		private final String event;
		private final String data;
		
		protected Event(String event, String data) {
			this.event = event;
			this.data = data;
		}
		
		/**
		 * Event type/description that has been received (can be null)
		 * @return  Type/description of the event or null
		 */
		public String getEvent() {
			return event;
		}
		
		/**
		 * Data that has been received (UTF-8)
		 * @return Received data
		 */
		public String getData() {
			return data;
		}
		
		@Override
		public String toString() {
			return new StringBuilder(getClass().getSimpleName()).append("{event=").append(event)
					.append("; data=").append(data).append("}").toString();
		}
	}
	
	
	protected abstract class InternalEventStreamAdapter extends EventStreamAdapter {
		public void onStartFirst(HttpResponse<Void> lastResponse) {}
		public void onStartLast(HttpResponse<Void> lastResponse, HttpRequest.Builder builder) {}
	}
	

	protected URI uri;
	protected HttpRequestMethod method = HttpRequestMethod.GET;
	protected BodyPublisher requestBody = null;
	protected HttpClient.Version version = null;
	protected TreeMap<String, String> headers = new TreeMap<>();
	protected long timeout, retryCooldown;
	protected boolean autoStopIfNoEvents;
	protected final AtomicBoolean hasReceivedEvents = new AtomicBoolean(false); // internal use
	
	protected HttpClient client = null;
	protected long lastEventID = 1;
	protected HashSet<EventStreamListener> listeners = new HashSet<>();
	protected HashSet<InternalEventStreamAdapter> internalListeners = new HashSet<>();
	protected CompletableFuture<HttpResponse<Void>> running = null;
	
	/**
	 * Creates a HTTP client that listens for Server-Sent Events (SSE).
	 * Starts listening after calling {@link HttpEventStreamClient#start()}
	 * @param url URL the client should listen at
	 * @param listener Event stream listeners that listen for arriving events (optional)
	 */
	public HttpEventStreamClient(String url, EventStreamListener... listener) {
		this(url, null, null, null, null, -1, -1, false, null, listener);
	}
	
	/**
	 * Creates a HTTP client that listens for Server-Sent Events (SSE).
	 * Starts listening after calling {@link HttpEventStreamClient#start()}
	 * @param url URL the client should listen at
	 * @param headers HTTP headers that should be set for the request. 
	 * SSE specific headers will get overwritten [Accept, Cache-Control, Last-Event-ID] (optional)
	 * @param listener Event stream listeners that listen for arriving events (optional)
	 */
	public HttpEventStreamClient(String url, Map<String, String> headers, EventStreamListener... listener) {
		this(url, null, null, null, headers, -1, -1, false, null, listener);
	}
	
	/**
	 * Creates a HTTP client that listens for Server-Sent Events (SSE).
	 * Starts listening after calling {@link HttpEventStreamClient#start()}
	 * @param url URL the client should listen at
	 * @param method HTTP method that should be used to request the event stream (default GET)
	 * @param requestBody HTTP request body that gets sent along the request (optional)
	 * @param headers HTTP headers that should be set for the request. 
	 * SSE specific headers will get overwritten [Accept, Cache-Control, Last-Event-ID] (optional)
	 * @param listener Event stream listeners that listen for arriving events (optional)
	 */
	public HttpEventStreamClient(String url, HttpRequestMethod method, BodyPublisher requestBody, Map<String, String> headers, EventStreamListener... listener) {
		this(url, method, requestBody, null, headers, -1, -1, false, null, listener);
	}
	
	/**
	 * Creates a HTTP client that listens for Server-Sent Events (SSE).
	 * Starts listening after calling {@link HttpEventStreamClient#start()}
	 * @param url URL the client should listen at
	 * @param method HTTP method that should be used to request the event stream (default GET)
	 * @param requestBody HTTP request body that gets sent along the request (optional)
	 * @param headers HTTP headers that should be set for the request. 
	 * SSE specific headers will get overwritten [Accept, Cache-Control, Last-Event-ID] (optional)
	 * @param timeout Timeout in milliseconds for the HTTP client before it reconnects (if negative then ignored)
	 * @param retryCooldown Cooldown in milliseconds after connection loss before starting to reconnect (negative for no cooldown)
	 * @param listener Event stream listeners that listen for arriving events (optional)
	 */
	public HttpEventStreamClient(String url, HttpRequestMethod method, BodyPublisher requestBody, Map<String, String> headers, long timeout, long retryCooldown, EventStreamListener... listener) {
		this(url, method, requestBody, null, headers, timeout, retryCooldown, false, null, listener);
	}
	
	/**
	 * Creates a HTTP client that listens for Server-Sent Events (SSE).
	 * Starts listening after calling {@link HttpEventStreamClient#start()}
	 * @param url URL the client should listen at
	 * @param method HTTP method that should be used to request the event stream (default GET)
	 * @param requestBody HTTP request body that gets sent along the request (optional)
	 * @param version Specific HTTP version that should be used to request (optional)
	 * @param headers HTTP headers that should be set for the request. 
	 * SSE specific headers will get overwritten [Accept, Cache-Control, Last-Event-ID] (optional)
	 * @param timeout Timeout in milliseconds for the HTTP client before it reconnects (if negative then ignored)
	 * @param retryCooldown Cooldown in milliseconds after connection loss before starting to reconnect (negative for no cooldown)
	 * @param autoStopIfNoEvents If true then client automatically stops if connection closes and no events have been received since the last (re-)connect
	 * @param client HTTP client that should be used (optional)
	 * @param listener Event stream listeners that listen for arriving events (optional)
	 */
	public HttpEventStreamClient(String url, HttpRequestMethod method, BodyPublisher requestBody, HttpClient.Version version, Map<String, String> headers, long timeout, long retryCooldown, boolean autoStopIfNoEvents, HttpClient client, EventStreamListener... listener) {
		this.uri = URI.create(url);
		this.method = method!=null ? method : this.method;
		this.requestBody = requestBody;
		this.version = version;
		this.timeout = timeout;
		this.retryCooldown = retryCooldown;
		this.autoStopIfNoEvents = autoStopIfNoEvents;
		this.client = client;
		setHeaders(headers);
		addListener(listener);
	}
	
	/**
	 * URI this client listens for events
	 * @return URI that is used to listen for events
	 */
	public URI getURI() {
		return uri;
	}
	
	/**
	 * URL string this client listens for events
	 * @return URL string that is used to listen for events
	 */
	public String getURL() {
		return uri.toString();
	}
	
	/**
	 * Returns the HTTP method type that client uses for HTTP requests
	 * @return HTTP request method type
	 */
	public HttpRequestMethod getHttpMethod() {
		return method;
	}
	
	/**
	 * Sets the HTTP method type that client uses for HTTP requests
	 * @param method HTTP request method type used for HTTP requests
	 */
	public void setHttpMethod(HttpRequestMethod method) {
		this.method = method!=null ? method : HttpRequestMethod.GET;
	}
	
	/**
	 * Returns the HTTP body used for requests (can be null)
	 * @return HTTP request body or null
	 */
	public BodyPublisher getHttpRequestBody() {
		return requestBody;
	}
	
	/**
	 * Sets a HTTP body used for requests. 
	 * Only needed for certain HTTP request methods otherwise ignored.
	 * @param requestBody HTTP request body or null
	 */
	public void setHttpRequestBody(BodyPublisher requestBody) {
		this.requestBody = requestBody;
	}
	
	/**
	 * Returns HTTP version if a specific one is set that should be used
	 * @return HTTP version that is forced to be used or null
	 */
	public HttpClient.Version getHttpVersion(){
		return version;
	}
	
	/**
	 * Sets a specific HTTP version that should be used. 
	 * If null then HTTP client will automatically determine appropriate version
	 * @param version HTTP version that is forced to be used or null
	 */
	public void setHttpVersion(HttpClient.Version version) {
		this.version = version;
	}
	
	/**
	 * Returns HTTP headers that will be used for HTTP requests
	 * @return HTTP headers map
	 */
	public Map<String, String> getHeaders(){
		return new TreeMap<>(headers);
	}
	
	/**
	 * Adds HTTP headers that will be used for HTTP requests (overwrites existing ones).
	 * SSE specific headers cannot be overwritten (Accept, Cache-Control, Last-Event-ID)
	 * @param headers HTTP headers that should be added
	 */
	public void addHeaders(Map<String, String> headers) {
		if(headers==null) return;
		for(Entry<String, String> entry : headers.entrySet())
			this.headers.put(entry.getKey().trim().toLowerCase(), entry.getValue());
	}
	
	/**
	 * Sets HTTP headers that will be used for HTTP requests (removes all existing ones).
	 * SSE specific headers cannot be overwritten (Accept, Cache-Control, Last-Event-ID)
	 * @param headers HTTP headers that should be added
	 */
	public void setHeaders(Map<String, String> headers) {
		if(headers==null) return;
		this.headers.clear();
		addHeaders(headers);
	}
	
	/**
	 * Sets/Removes a HTTP header that will be used for HTTP requests.
	 * SSE specific headers cannot be overwritten (Accept, Cache-Control, Last-Event-ID)
	 * @param key Key of the header (cannot be null or blank)
	 * @param value Value that should be set (if null then key gets removed)
	 */
	public void setHeader(String key, String value) {
		if(key==null || key.isBlank()) throw new NullPointerException("Key cannot be null or blank");
		if(value!=null && !value.isBlank())
			this.headers.put(key.trim().toLowerCase(), value);
		else this.headers.remove(key.trim().toLowerCase());
	}
	
	/**
	 * Returns the value of the HTTP headers for a specific key
	 * @param key Key the value should be returned for (cannot be null or empty)
	 * @return Value that is set for the HTTP header or null if not set
	 */
	public String getHeader(String key) {
		return key!=null ? this.headers.get(key.trim().toLowerCase()) : null;
	}
	
	/**
	 * Removes a HTTP header so it gets no longer used for HTTP requests
	 * @param key Key of header that should be removed 
	 * @return Previously set value or null if not previously set
	 */
	public String removeHeader(String key) {
		if(key==null) return null;
		return this.headers.remove(key.trim().toLowerCase());
	}
	
	/**
	 * Removes multiple HTTP headers so they no longer will be used for HTTP requests
	 * @param keys Keys of the HTTP headers
	 */
	public void removeHeaders(String... keys) {
		if(keys==null) return;
		for(String key: keys)
			this.headers.remove(key.trim().toLowerCase());
	}
	
	/**
	 * Removes all HTTP headers so no custom HTTP headers will be sent in the HTTP requests
	 */
	public void clearHeaders() {
		this.headers.clear();
	}
	
	/**
	 * Returns the timeout in milliseconds for the HTTP client before it reconnects (if negative then ignored)
	 * @return Timeout in milliseconds
	 */
	public long getTimeout() {
		return timeout;
	}
	
	/**
	 * Sets the timeout in milliseconds for the HTTP client before it reconnects (if negative then ignored)
	 * @param timeout Timeout in milliseconds
	 */
	public void setTimeout(long timeout) {
		this.timeout = timeout;
	}
	
	/**
	 * Returns the cooldown in milliseconds that this client 
	 * will wait before reconnecting after a connection loss
	 * @return Cooldown in milliseconds
	 */
	public long getRetryCooldown() {
		return this.retryCooldown;
	}
	
	/**
	 * Sets the cooldown in milliseconds that this client 
	 * will wait before reconnection after a connection loss
	 * @param retryCooldown Cooldown in milliseconds (negative for no cooldown)
	 */
	public void setRetryCooldown(long retryCooldown) {
		this.retryCooldown = retryCooldown;
	}
	
	/**
	 * Returns if the client automatically stops if connection 
	 * closes and no events have been received since the last (re-)connect
	 * @return True if auto stop enabled
	 */
	public boolean isAutoStopIfNoEvents() {
		return autoStopIfNoEvents;
	}
	
	/**
	 * Sets if the client should automatically stop if connection 
	 * closes and no events have been received since the last (re-)connect
	 * @param autoStopIfNoEvents If true client automatically stops
	 */
	public void setAutoStopIfNoEvents(boolean autoStopIfNoEvents) {
		this.autoStopIfNoEvents = autoStopIfNoEvents;
	}
	
	/**
	 * {@link HttpClient} that gets used for HTTP requests. 
	 * May be null if not specified and not started yet
	 * @return {@link HttpClient} that is used for HTTP requests (may be null)
	 */
	public HttpClient getHttpClient() {
		return client;
	}
	
	/**
	 * Sets if a specific {@link HttpClient} should be used for HTTP requests. 
	 * If null a new {@link HttpClient} instance will be created
	 * @param client HTTP client that should be used (null for new instance)
	 */
	public void setHttpClient(HttpClient client) {
		this.client = client;
	}
	
	/**
	 * Returns ID of the latest event
	 * @return ID of latest event
	 */
	public long getLastEventID() {
		return lastEventID;
	}
	
	/**
	 * Sets the event id that should be sent on next start/reconnect. 
	 * May be overwritten by the server in the mean time. 
	 * Call {@link HttpEventStreamClient#start()} to force sending of new id
	 * @param id Event id that should be sent in HTTP header (Last-Event-ID)
	 */
	public void setLastEventID(long id) {
		this.lastEventID = id;
	}
	
	/**
	 * Returns a set containing all added listeners
	 * @return Set of all added listeners
	 */
	public Set<EventStreamListener> getListeners(){
		return new HashSet<>(listeners);
	}
	
	/**
	 * Removes all listeners so they no longer get called
	 */
	public void removeAllListeners() {
		listeners.clear();
	}
	
	/**
	 * Adds a listener so it gets called on new events. 
	 * Multiple adding of same listener will only add once
	 * @param listener Listener(s) that should be added
	 */
	public void addListener(EventStreamListener... listener) {
		for(EventStreamListener l : listener)
			if(l!=null) this.listeners.add(l);
	}
	
	/**
	 * Removes the listeners so they no longer get called
	 * @param listener Listeners that should be removed
	 */
	public void removeListener(EventStreamListener... listener) {
		for(EventStreamListener l : listener)
			if(l!=null) this.listeners.remove(l);
	}
	
	/**
	 * Returns if client is currently listening for SSE events
	 * @return
	 */
	public boolean isRunning() {
		return running!=null && !running.isDone();
	}
	
	/**
	 * Starts listening for SSE events and immediately returns. 
	 * If client looses connection then automatically reconnects. 
	 * Multiple calls will not start multiple listening 
	 * but calls {@link EventStreamListener#onReconnect()} on listeners
	 * @return This client instance
	 */
	public HttpEventStreamClient start() {
		for(InternalEventStreamAdapter listener : internalListeners)
			try {
				listener.onStartFirst((running!=null && running.isDone()) ? running.get() : null);
			} catch (InterruptedException | ExecutionException ex) {
				for(InternalEventStreamAdapter l : internalListeners)
					try { l.onError(this, ex); } catch (Exception ex1) {}
			}
		if(running!=null) {
			for(InternalEventStreamAdapter listener : internalListeners)
				try {
					listener.onReconnect(this, running.isDone() ? running.get() : null, hasReceivedEvents.get());
				} catch (Exception ex) {
					for(EventStreamListener l : internalListeners)
						try { l.onError(this, ex); } catch (Exception ex1) {}
				}
			for(EventStreamListener listener : listeners)
				try {
					listener.onReconnect(this, running.isDone() ? running.get() : null, hasReceivedEvents.get());
				} catch (Exception ex) {
					for(EventStreamListener l : listeners)
						try { l.onError(this, ex); } catch (Exception ex1) {}
				}
		}
		hasReceivedEvents.set(false);
		
		if(client==null) client = HttpClient.newHttpClient();
		HttpRequest.Builder request = HttpRequest.newBuilder(uri);
		switch (method) {
			case GET: request.GET(); break;
			case POST: request.POST(requestBody); break;
			case PUT: request.PUT(requestBody); break;
			case DELETE: request.DELETE(); break;
			default: break;
		}
		if(version != null) request.version(version);
		for(Entry<String, String> entry : headers.entrySet())
			request.setHeader(entry.getKey(), entry.getValue());
		request.setHeader("Accept", "text/event-stream");
		request.setHeader("Cache-Control", "no-cache");
		request.setHeader("Last-Event-ID", lastEventID+"");
		if(timeout>=0) request.timeout(Duration.ofMillis(timeout));
		
		for(InternalEventStreamAdapter listener : internalListeners)
			try {
				listener.onStartLast((running!=null && running.isDone()) ? running.get() : null, request);
			} catch (InterruptedException | ExecutionException e1) {}
		
		running = client.sendAsync(request.build(), BodyHandlers.ofByteArrayConsumer(new Consumer<Optional<byte[]>>() {
			StringBuilder sb = new StringBuilder(), data = new StringBuilder();
			String event = null;
			@Override
			public void accept(Optional<byte[]> t) {
				if(t.isPresent()) {
					hasReceivedEvents.set(true);
					sb.append(new String(t.get(), StandardCharsets.UTF_8));
					int index;
					while((index = sb.indexOf("\n\n")) >= 0) {
						String[] lines = sb.substring(0, index).split("\n");
						sb.delete(0, index+2);
						for(String line : lines) {
							int idx = line.indexOf(':');
							if(idx<=0) continue; // ignore invalids or comments
							String key = line.substring(0, idx), value = line.substring(idx+1).trim();
							switch(key.trim().toLowerCase()) {
								case "event":
									this.event = value;
									break;
									
								case "data":
									if(data.length() > 0) data.append("\n");
									data.append(value);
									break;
									
								case "id":
									try {
										lastEventID = Long.parseLong(value);
									} catch (Exception ex) {
										for(InternalEventStreamAdapter l : internalListeners)
											try { l.onError(HttpEventStreamClient.this, ex); } catch (Exception ex1) {}
										for(EventStreamListener l : listeners)
											try { l.onError(HttpEventStreamClient.this, ex); } catch (Exception ex1) {}
									}
									break;
									
								case "retry":
									try {
										retryCooldown = Long.parseLong(value);
									} catch (Exception ex) {
										for(InternalEventStreamAdapter l : internalListeners)
											try { l.onError(HttpEventStreamClient.this, ex); } catch (Exception ex1) {}
										for(EventStreamListener l : listeners)
											try { l.onError(HttpEventStreamClient.this, ex); } catch (Exception ex1) {}
									}
									break;
								
								default: break;
							}
						}
						Event event = new Event(this.event, this.data.toString());
						for(InternalEventStreamAdapter listener : internalListeners)
							try {
								listener.onEvent(HttpEventStreamClient.this, event);
							} catch (Exception ex) {
								for(InternalEventStreamAdapter l : internalListeners)
									try { l.onError(HttpEventStreamClient.this, ex); } catch (Exception ex1) {}
							}
						for(EventStreamListener listener : listeners)
							try {
								listener.onEvent(HttpEventStreamClient.this, event);
							} catch (Exception ex) {
								for(EventStreamListener l : listeners)
									try { l.onError(HttpEventStreamClient.this, ex); } catch (Exception ex1) {}
							}
						this.data.setLength(0);
						lastEventID++;
					}
				}
			}
		}));
		running.handleAsync(new BiFunction<HttpResponse<Void>, Throwable, Void>() {

			@Override
			public Void apply(HttpResponse<Void> t, Throwable u) {
				if(u != null) {
					for(InternalEventStreamAdapter listener : internalListeners)
						try { listener.onError(HttpEventStreamClient.this, u); } catch (Exception e) {}
					for(EventStreamListener listener : listeners)
						try { listener.onError(HttpEventStreamClient.this, u); } catch (Exception e) {}
				}
				
				
				if(!autoStopIfNoEvents || hasReceivedEvents.get()) {
					if(running != null) {
						if(retryCooldown>0) try { Thread.sleep(retryCooldown); } catch (Exception e) {}
						start();
					}
				} else { stop(); }
				return null;
			}
		});
		return this;
	}
	
	/**
	 * Blocks until this client has stopped listening. 
	 * If not listening then returns immediately 
	 * @return This client instance
	 */
	public HttpEventStreamClient join() {
		while(running != null)
			try { running.join(); } catch (Exception e) {}
		return this;
	}
	
	/**
	 * Stops without reconnecting.
	 * Executes {@link EventStreamListener#onClose()} on listeners
	 * @return This client instance
	 */
	public HttpEventStreamClient stop() {
		CompletableFuture<HttpResponse<Void>> run = running;
		running = null;
		if(run!=null) run.cancel(true);
		for(InternalEventStreamAdapter listener : internalListeners)
			try { listener.onClose(this, run.get()); } catch (Exception e) {}
		for(EventStreamListener listener : listeners)
			try { listener.onClose(this, run.get()); } catch (Exception e) {}
		return this;
	}
}

