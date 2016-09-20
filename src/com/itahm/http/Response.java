package com.itahm.http;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class Response {

	public final static String CRLF = "\r\n";
	public final static String FIELD = "%s: %s"+ CRLF;
	
	private final Map<String, String> header = new HashMap<String, String>();
	private String startLine;
	private byte [] body;
	
	public enum Status {
		OK, BADREQUEST, UNAUTHORIZED, NOTFOUND, NOTALLOWED, VERSIONNOTSUP, CONFLICT
	};
	
	private Response(Status status, byte [] bytes) {
		int code = 200;
		String reason = "OK";
		
		body = bytes;
		
		switch (status) {
		case BADREQUEST:
			code = 400;
			reason = "Bad request";
			
			try {
				bytes = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"></head><body><h1>HTTP1.1 400 Bad request</h1></body></html>"
					.getBytes(StandardCharsets.UTF_8.name());
			} catch (UnsupportedEncodingException e) {
			}
			
			break;
		case NOTFOUND:
			code = 404;
			reason = "Not found";
			
			try {
				bytes = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"></head><body><h1>HTTP1.1 404 Not found</h1></body></html>"
					.getBytes(StandardCharsets.UTF_8.name());
			} catch (UnsupportedEncodingException e) {
			}
			
			body = bytes;
			
			break;
		case NOTALLOWED:
			code = 405;
			reason = "Method Not Allowed";
		
			try {
				bytes = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"></head><body><h1>HTTP1.1 405 Method Not Allowed</h1></body></html>"
					.getBytes(StandardCharsets.UTF_8.name());
			} catch (UnsupportedEncodingException e) {
			}
			
			setResponseHeader("Allow", "GET");
			
			break;
		case UNAUTHORIZED:
			code = 401;
			reason = "Unauthorized";
			
			try {
				bytes = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"></head><body><h1>HTTP1.1 401 Unauthorized</h1></body></html>"
					.getBytes(StandardCharsets.UTF_8.name());
			} catch (UnsupportedEncodingException e) {
			}
			
			break;
		case VERSIONNOTSUP:
			code = 505;
			reason = "HTTP Version Not Supported";
			
			try {
				bytes = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"></head><body><h1>HTTP1.1 505 HTTP Version Not Supported</h1></body></html>"
					.getBytes(StandardCharsets.UTF_8.name());
			} catch (UnsupportedEncodingException e) {
			}
			
			break;
		case CONFLICT:
			code = 409;
			reason = "Conflict";
			
			try {
				bytes = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"></head><body><h1>HTTP1.1 409 Conflict</h1></body></html>"
					.getBytes(StandardCharsets.UTF_8.name());
			} catch (UnsupportedEncodingException e) {
			}
			
			break;
		case OK:
		}
		
		startLine = String.format("HTTP/1.1 %d %s" +CRLF, code, reason);
	}
	
	public static Response getInstance(Status status) {
		return new Response(status, new byte [0]);
	}
	
	public static Response getInstance(Status status, String body) {
		try {
			return new Response(status, body.getBytes(StandardCharsets.UTF_8.name()));
		} catch (UnsupportedEncodingException e) {
			return null;
		}
	}
	
	public static Response getInstance(Status status, File body) {
		try (RandomAccessFile raf = new RandomAccessFile(body, "r")) {
			FileChannel fc = raf.getChannel();
			int size = (int)fc.size();
			ByteBuffer buffer = ByteBuffer.allocate(size);
			byte [] bytes;
			
			fc.read(buffer);
			
			buffer.flip();
			
			bytes = new byte [buffer.remaining()];
			
			buffer.get(bytes);
			
			return new Response(status, bytes).setResponseHeader("Content-type", Files.probeContentType(body.toPath()));
		} catch (IOException ioe) {
			return null;
		}
	}

	public Response setResponseHeader(String name, String value) {
		this.header.put(name, value);
		
		return this;
	}
	
	public ByteBuffer build() throws IOException {
		if (this.startLine == null || this.body == null) {
			throw new IOException("malformed http request!");
		}
		
		StringBuilder sb = new StringBuilder();
		Iterator<String> iterator;		
		String key;
		byte [] header;
		byte [] message;
		
		sb.append(this.startLine);
		sb.append(String.format(FIELD, "Content-Length", String.valueOf(this.body.length)));
		
		iterator = this.header.keySet().iterator();
		while(iterator.hasNext()) {
			key = iterator.next();
			
			sb.append(String.format(FIELD, key, this.header.get(key)));
		}
		
		sb.append(CRLF);
		
		header = sb.toString().getBytes(StandardCharsets.US_ASCII.name());
		
		message = new byte [header.length + this.body.length];
		
		System.arraycopy(header, 0, message, 0, header.length);
		System.arraycopy(this.body, 0, message, header.length, this.body.length);
		
		return ByteBuffer.wrap(message);
	}
	
}
