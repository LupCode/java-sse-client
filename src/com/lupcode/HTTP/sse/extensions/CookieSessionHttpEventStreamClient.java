package com.lupcode.HTTP.sse.extensions;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublisher;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.lupcode.HTTP.sse.EventStreamListener;
import com.lupcode.HTTP.sse.HttpEventStreamClient;
import com.lupcode.HTTP.sse.HttpRequestMethod;

/**
 * HTTP Client that can listen for Server-Sent Events (SSE). 
 * In addition stores all received cookies and sends them in each request.
 * Implements full protocol and supports automatic reconnect.
 * @author LupCode.com (Luca Vogels)
 * @since 2020-12-26
 */
public class CookieSessionHttpEventStreamClient extends HttpEventStreamClient {

	protected HashMap<String, String> cookies = new HashMap<>();
	protected HashMap<String, String> cookies2 = new HashMap<>();
	
	/**
	 * Creates a HTTP client that listens for Server-Sent Events (SSE).
	 * Starts listening after calling {@link HttpEventStreamClient#start()}
	 * @param url URL the client should listen at
	 * @param listener Event stream listeners that listen for arriving events (optional)
	 */
	public CookieSessionHttpEventStreamClient(String url, EventStreamListener... listener) {
		this(url, null, null, null, null, -1, -1, -1, false, null, listener);
	}
	
	/**
	 * Creates a HTTP client that listens for Server-Sent Events (SSE).
	 * Starts listening after calling {@link HttpEventStreamClient#start()}
	 * @param url URL the client should listen at
	 * @param headers HTTP headers that should be set for the request. 
	 * SSE specific headers will get overwritten [Accept, Cache-Control, Last-Event-ID] (optional)
	 * @param listener Event stream listeners that listen for arriving events (optional)
	 */
	public CookieSessionHttpEventStreamClient(String url, Map<String, String> headers, EventStreamListener... listener) {
		this(url, null, null, null, headers, -1, -1, -1, false, null, listener);
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
	public CookieSessionHttpEventStreamClient(String url, HttpRequestMethod method, BodyPublisher requestBody, Map<String, String> headers, EventStreamListener... listener) {
		this(url, method, requestBody, null, headers, -1, -1, -1, false, null, listener);
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
	public CookieSessionHttpEventStreamClient(String url, HttpRequestMethod method, BodyPublisher requestBody, Map<String, String> headers, long timeout, long retryCooldown, EventStreamListener... listener) {
		this(url, method, requestBody, null, headers, timeout, retryCooldown, -1, false, null, listener);
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
	 * @param maxReconnectsWithoutEvents How often client can reconnect 
	 * without receiving events before it stops (zero for no reconnect, negative for infinitely)
	 * @param resetEventIDonReconnect If true then event id will be set back to zero on reconnect
	 * @param client HTTP client that should be used (optional)
	 * @param listener Event stream listeners that listen for arriving events (optional)
	 */
	public CookieSessionHttpEventStreamClient(String url, HttpRequestMethod method, BodyPublisher requestBody, HttpClient.Version version, Map<String, String> headers, long timeout, long retryCooldown, int maxReconnectsWithoutEvents, boolean resetEventIDonReconnect, HttpClient client, EventStreamListener... listener) {
		super(url, method, requestBody, version, headers, timeout, retryCooldown, maxReconnectsWithoutEvents, resetEventIDonReconnect, client, listener);
		
		internalListeners.add(new InternalEventStreamAdapter() {
			
			private void process(HttpResponse<Void> response) {
				if(response==null) return;
				parseCookiesFromHeaders(response.headers().map());
			}
			
			@Override
			public void onStartLast(HttpResponse<Void> lastResponse, HttpRequest.Builder request){
				StringBuilder sb = new StringBuilder();
				
				if(!cookies.isEmpty()) {
					for(Entry<String, String> cookie : cookies.entrySet())
						sb.append("; ").append(cookie.getKey()).append("=").append(cookie.getValue());
					if(sb.length() > 0)
						request.setHeader("cookie", sb.substring(2));
					sb.setLength(0);
				}

				if(!cookies2.isEmpty()) {
					for(Entry<String, String> cookie : cookies2.entrySet())
						sb.append("; ").append(cookie.getKey()).append("=").append(cookie.getValue());
					if(sb.length() > 0)
						request.setHeader("cookie2", sb.substring(2));
					sb.setLength(0);
				}
			}
			
			@Override
			public void onStartFirst(HttpResponse<Void> response){
				process(response);
			}
			
			@Override
			public void onClose(HttpEventStreamClient client, HttpResponse<Void> response) {
				process(response);
			}
			
		});
	}
	
	/**
	 * Returns the cookie key/value pairs
	 * @return Cookies
	 */
	public Set<Entry<String, String>> getCookies() {
		return cookies.entrySet();
	}
	
	/**
	 * Returns the value of a cookie
	 * @param key Key of cookie that should be returned (case sensitive)
	 * @return Value of cookie or null if not set or key null
	 */
	public String getCookie(String key) {
		return key!=null ? cookies.get(key) : null;
	}
	
	/**
	 * Sets a cookie
	 * @param key Key of cookie that should be set (case sensitive)
	 * @param value Value of the cookie or null to delete cookie
	 * @return This instance
	 * @throws NullPointerException if key is null or blank
	 */
	public CookieSessionHttpEventStreamClient setCookie(String key, String value) throws NullPointerException {
		if(key==null || key.isBlank()) throw new NullPointerException("Key cannot be null or blank");
		if(value!=null)
			cookies.put(key, value);
		else
			cookies.remove(key);
		return this;
	}
	
	/**
	 * Removes a cookie
	 * @param key Key of cookie that should be removed (case sensitive)
	 * @return This instance
	 * @throws NullPointerException if key is null or blank
	 */
	public CookieSessionHttpEventStreamClient removeCookie(String... key) throws NullPointerException {
		if(key==null) throw new NullPointerException("Key cannot be null");
		for(String k : key) {
			if(k==null || k.isBlank()) throw new NullPointerException("Key cannot be null or blank");
			cookies.remove(k);
		} return this;
	}
	
	/**
	 * Parses a raw cookie header string of a request
	 * @param raw Raw cookie string in the format:  name=value; name2=value2; name3=value3
	 * @return This instance
	 * @throws NullPointerException if raw is null
	 * @throws IllegalArgumentException if raw has invalid format
	 */
	public CookieSessionHttpEventStreamClient parseCookie(String raw) throws NullPointerException, IllegalArgumentException {
		if(raw==null) throw new NullPointerException("Raw cannot be null");
		String[] args = raw.split(";");
		for(String arg : args) {
			int index = arg.indexOf("=");
			if(index <= 0) throw new IllegalArgumentException("Cookie invalid format");
			cookies.put(arg.substring(0, index), arg.substring(index+1));
		} return this;
	}
	
	/**
	 * Parses a raw set-cookie header string of a response
	 * @param raw Raw cookie string in the format:  <cookie-name>=<cookie-value>; Domain=<domain-value>; Secure; HttpOnly
	 * @return This instance
	 * @throws NullPointerException if raw is null
	 * @throws IllegalArgumentException if raw has invalid format
	 */
	public CookieSessionHttpEventStreamClient parseSetCookie(String raw) throws NullPointerException, IllegalArgumentException {
		if(raw==null) throw new NullPointerException("Raw cannot be null");
		int indexSign = raw.indexOf("=");
		if(indexSign <= 0) throw new IllegalArgumentException("Set-Cookie invalid format");
		int indexEnd = raw.indexOf(";", indexSign+1);
		cookies.put(raw.substring(0, indexSign), raw.substring(indexSign+1, indexEnd>0 ? indexEnd : raw.length()));
		return this;
	}
	
	
	/**
	 * Parses all cookie information from the given HTTP headers
	 * @param headers Headers that should be parsed
	 * @return This instance
	 * @throws NullPointerException if headers is null
	 */
	public CookieSessionHttpEventStreamClient parseCookiesFromHeaders(Map<String, List<String>> headers) throws NullPointerException {
		if(headers==null) throw new NullPointerException("Headers cannot be null");
		List<String> list = headers.get("cookie");
		if(list != null && !list.isEmpty())
			for(String c : list) {
				parseCookie(c);
			}
		
		list = headers.get("set-cookie");
		if(list != null && !list.isEmpty())
			for(String c : list) {
				parseSetCookie(c);
			}
		
		list = headers.get("cookie2");
		if(list != null && !list.isEmpty())
			for(String c : list) {
				parseCookie2(c);
			}
		
		list = headers.get("set-cookie2");
		if(list != null && !list.isEmpty())
			for(String c : list) {
				parseSetCookie2(c);
			}
		
		return this;
	}
	
	
	/**
	 * Returns the cookie 2 key/value pairs
	 * @return Cookies 2
	 */
	@Deprecated
	public Set<Entry<String, String>> getCookies2() {
		return cookies2.entrySet();
	}
	
	/**
	 * Returns the value of a cookie 2
	 * @param key Key of cookie that should be returned (case sensitive)
	 * @return Value of cookie or null if not set or key null
	 */
	@Deprecated
	public String getCookie2(String key) {
		return key!=null ? cookies2.get(key) : null;
	}
	
	/**
	 * Sets a cookie 2
	 * @param key Key of cookie that should be set (case sensitive)
	 * @param value Value of the cookie or null to delete cookie
	 * @return This instance
	 * @throws NullPointerException if key is null or blank
	 */
	@Deprecated
	public CookieSessionHttpEventStreamClient setCookie2(String key, String value) throws NullPointerException {
		if(key==null || key.isBlank()) throw new NullPointerException("Key cannot be null or blank");
		if(value!=null)
			cookies2.put(key, value);
		else
			cookies2.remove(key);
		return this;
	}
	
	/**
	 * Removes a cookie 2
	 * @param key Key of cookie that should be removed (case sensitive)
	 * @return This instance
	 * @throws NullPointerException if key is null or blank
	 */
	@Deprecated
	public CookieSessionHttpEventStreamClient removeCookie2(String... key) throws NullPointerException {
		if(key==null) throw new NullPointerException("Key cannot be null");
		for(String k : key) {
			if(k==null || k.isBlank()) throw new NullPointerException("Key cannot be null or blank");
			cookies2.remove(k);
		} return this;
	}
	
	/**
	 * Parses a raw cookie2 header string of a request
	 * @param raw Raw cookie string in the format:  name=value; name2=value2; name3=value3
	 * @return This instance
	 * @throws NullPointerException if raw is null
	 * @throws IllegalArgumentException if raw has invalid format
	 */
	@Deprecated
	public CookieSessionHttpEventStreamClient parseCookie2(String raw) throws NullPointerException, IllegalArgumentException {
		if(raw==null) throw new NullPointerException("Raw cannot be null");
		String[] args = raw.split(";");
		for(String arg : args) {
			int index = arg.indexOf("=");
			if(index <= 0) throw new IllegalArgumentException("Cookie2 invalid format");
			cookies2.put(arg.substring(0, index), arg.substring(index+1));
		} return this;
	}
	
	/**
	 * Parses a raw set-cookie2 header string of a response
	 * @param raw Raw cookie string in the format:  <cookie-name>=<cookie-value>; Domain=<domain-value>; Secure; HttpOnly
	 * @return This instance
	 * @throws NullPointerException if raw is null
	 * @throws IllegalArgumentException if raw has invalid format
	 */
	@Deprecated
	public CookieSessionHttpEventStreamClient parseSetCookie2(String raw) throws NullPointerException, IllegalArgumentException {
		if(raw==null) throw new NullPointerException("Raw cannot be null");
		int indexSign = raw.indexOf("=");
		if(indexSign <= 0) throw new IllegalArgumentException("Set-Cookie2 invalid format");
		int indexEnd = raw.indexOf(";", indexSign+1);
		cookies2.put(raw.substring(0, indexSign), raw.substring(indexSign+1, indexEnd>0 ? indexEnd : raw.length()));
		return this;
	}
}
