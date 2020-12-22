# java-sse-client
Native Java 11 implementation of a HTTP event stream client to listen for Server-Sent Events (SSE)

### How to use:
``` java
HttpEventStreamClient client = new HttpEventStreamClient("https://sse.example.com", new EventStreamAdapter() {
			
			@Override
			public void onEvent(Event event) {
				System.out.println("RECEIVED EVENT: "+event.toString());
			}
			
			@Override
			public void onClose() {
				System.out.println("SSE Client closed");
			}
			
		});
		client.start().join();
```

### References:
 - [LupCode.com](https://lupcode.com)
 - [Lup.services](https://lup.services)