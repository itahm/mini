package com.itahm.http;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.Timer;


public abstract class Listener extends Timer implements Runnable, Closeable {

	private final static int BUF_SIZE = 2048;
	
	private final ServerSocketChannel channel;
	private final ServerSocket listener;
	private final Selector selector;
	private final ByteBuffer buffer;
	private final Set<Request> connections = new HashSet<Request>();
	
	private Boolean closed = false;
	
	public Listener() throws IOException {
		this("0.0.0.0", 80);
	}

	public Listener(String ip) throws IOException {
		this(ip, 80);
	}
	
	public Listener(int tcp) throws IOException {
		this("0.0.0.0", tcp);
	}
	
	public Listener(String ip, int tcp) throws IOException {
		this(new InetSocketAddress(InetAddress.getByName(ip), tcp));
	}
	
	public Listener(InetSocketAddress addr) throws IOException {
		channel = ServerSocketChannel.open();
		listener = channel.socket();
		selector = Selector.open();
		buffer = ByteBuffer.allocateDirect(BUF_SIZE);
		
		listener.bind(addr);
		channel.configureBlocking(false);
		channel.register(selector, SelectionKey.OP_ACCEPT);
		
		new Thread(this).start();
		
		onStart();
	}
	
	private void onConnect() {
		SocketChannel channel = null;
		Request request;
		
		try {
			channel = this.channel.accept();
			request = new Request(channel, this);
			
			channel.configureBlocking(false);
			channel.register(this.selector, SelectionKey.OP_READ, request);
			
			connections.add(request);
			
			return;
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		if (channel != null) {
			try {
				channel.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	private void onRead(SelectionKey key) {
		SocketChannel channel = (SocketChannel)key.channel();
		Request request = (Request)key.attachment();
		int bytes = 0;
		
		this.buffer.clear();
		
		try {
			bytes = channel.read(buffer);
			
			if (bytes != -1) {
				if (bytes > 0) {
					this.buffer.flip();
					
					request.parse(this.buffer);
				}
				
				return;
			}
		} catch (IOException ioe) {
			// Client RESET에 의한 예외일 수 있음.
		}
		
		closeRequest(request);
	}

	public void closeRequest(Request request) {
		request.close();
		
		connections.remove(request);
		
		onClose(request);
	}
	
	public int getConnectionSize() {
		return connections.size();
	}
	
	@Override
	public void close() throws IOException {
		synchronized (this.closed) {
			if (this.closed) {
				return;
			}
		
			this.closed = true;
		}
		
		for (Request request : connections) {
			request.close();
		}
			
		connections.clear();
		
		cancel();
		
		this.selector.wakeup();
	}

	@Override
	public void run() {
		Iterator<SelectionKey> iterator = null;
		SelectionKey key = null;
		int count;
		
		while(!this.closed) {
			try {
				count = this.selector.select();
			} catch (IOException e) {
				e.printStackTrace();
				
				continue;
			}
			
			if (count > 0) {
				iterator = this.selector.selectedKeys().iterator();
				while(iterator.hasNext()) {
					key = iterator.next();
					iterator.remove();
					
					if (!key.isValid()) {
						continue;
					}
					
					if (key.isAcceptable()) {
						onConnect();
					}
					else if (key.isReadable()) {
						onRead(key);
					}
				}
			}
		}
		
		try {
			this.selector.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		try {
			this.listener.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	protected void onStart() {
	}
	
	protected void onRequest(Request request) {
	}
	
	protected void onClose(Request request) {
	}
	
	public static void main(String [] args) throws IOException {
		final Listener server = new Listener() {

			@Override
			protected void onRequest(Request request) {
				
				String uri = request.getRequestURI();
				String method = request.getRequestMethod();
				
				if (method.toLowerCase().equals("get")) {
					if ("/".equals(uri)) {
						uri = "/index.html";
					}
					
					Response response = Response.getInstance(Response.Status.OK, new File("."+ uri));
						
					if (response == null) {
						response = Response.getInstance(Response.Status.NOTFOUND);
					}
					
					try {
						request.sendResponse(response);
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				else {
					Response response = Response.getInstance(Response.Status.NOTALLOWED);
					
					try {
						request.sendResponse(response);
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}

			@Override
			protected void onClose(Request request) {
			}

		};
		
		System.in.read();
		
		server.close();
	}
	
}
