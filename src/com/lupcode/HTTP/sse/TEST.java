package com.lupcode.HTTP.sse;

import java.io.IOException;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.List;
import java.util.Map;

import com.lupcode.HTTP.sse.HttpEventStreamClient.Event;
import com.lupcode.HTTP.sse.extensions.CookieSessionHttpEventStreamClient;

public class TEST {

	public static void main(String[] args) throws Exception {
		
		
		String COOKIE = "AISSessionId=5fb1d514daf20e5a_3481278_RXqTpeyb_MTcyLjE2LjAuNzY6ODA!_0000000oDPH";
		
		
		HttpClient client = HttpClient.newBuilder().cookieHandler(new CookieHandler() {
			
			CookieManager cm = new CookieManager();
			
			@Override
			public void put(URI uri, Map<String, List<String>> responseHeaders) throws IOException {
				System.out.println("PUT: "+uri+" -> "+responseHeaders);
				cm.put(uri, responseHeaders);
			}
			
			@Override
			public Map<String, List<String>> get(URI uri, Map<String, List<String>> requestHeaders) throws IOException {
				Map<String, List<String>> values = cm.get(uri, requestHeaders);
				System.out.println("GET: "+uri+" -> "+values);
				return values;
			}
		}).build();
		
		
		System.out.println("NORMAL VISIT:");
		HttpResponse<String> resp = client.send(
				HttpRequest.newBuilder(URI.create("https://webradio.ffh.de/")).build(), 
				BodyHandlers.ofString()
				);
		System.out.println(resp.headers().map());
		System.out.println();
		
		
		System.out.println("SSE VISIT:");
		CookieSessionHttpEventStreamClient sse = new CookieSessionHttpEventStreamClient("https://mp3.ffh.de/metadata?type=json", new EventStreamAdapter() {
			
			@Override
			public void onEvent(Event event) {
				System.out.println("EVENT: "+event);
			}
			
			@Override
			public void onReconnect(HttpResponse<Void> response) {
				System.out.println("RECONNECT: "+response.headers().map());
			}
			
			@Override
			public void onClose(HttpResponse<Void> response) {
				System.out.println("CLOSE: "+response.headers().map());
			}
		});
		sse.parseCookie(COOKIE);
		sse.start().join();
	}

}
