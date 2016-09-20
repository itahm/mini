package com.itahm;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.net.BindException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.Timer;
import java.util.TimerTask;

import com.itahm.http.Listener;
import com.itahm.http.Request;
import com.itahm.http.Response;

import org.json.JSONObject;
import org.json.JSONException;

public class Mini extends Listener {

	private final static int TIMEOUT = 5000;
	private final static int INTERVAL = 1000;
	private final static String FILE_NAME = "itahm.mini.json";
	
	private JSONObject json;
	private final RandomAccessFile file;
	private final FileChannel channel;
	private Timer timer;
	
	public Mini(int tcp) throws IOException{
		super(tcp);
		
		File saved = new File("."+ File.separator + FILE_NAME);
		boolean initialized = saved.isFile();
		
		file = new RandomAccessFile(saved, "rws");
		channel = file.getChannel();
		
		try {
			if (initialized) {
				ByteBuffer buffer = ByteBuffer.allocate((int)channel.size());
				
				channel.read(buffer);
				buffer.flip();
				
				try {
					json = new JSONObject(StandardCharsets.UTF_8.decode(buffer).toString());
				}
				catch (JSONException jsone) {
					throw new IOException(jsone);
				}
			}
			else {
				json = new JSONObject();
				
				json.put("127.0.0.1",
					new JSONObject()
						.put("address", "127.0.0.1")
						.put("echo", JSONObject.NULL)
						.put("x", 0)
						.put("y", 0)
						.put("ifEntry",
							new JSONObject()
								.put("itahm.com", "")
								.put("google.com", "")
								.put("8.8.8.8", "")));
				
				json.put("google.com",
					new JSONObject()
						.put("address", "google.com")
						.put("echo", JSONObject.NULL)
						.put("x", 100)
						.put("y", 100)
						.put("ifEntry",
							new JSONObject()
								.put("127.0.0.1", "")));
				
				json.put("itahm.com",
						new JSONObject()
							.put("address", "itahm.com")
							.put("echo", JSONObject.NULL)
							.put("x", 0)
							.put("y", -100)
							.put("ifEntry",
								new JSONObject()
									.put("127.0.0.1", "")));
				
				json.put("8.8.8.8",
						new JSONObject()
							.put("address", "8.8.8.8")
							.put("echo", JSONObject.NULL)
							.put("x", -100)
							.put("y", 100)
							.put("ifEntry",
								new JSONObject()
									.put("127.0.0.1", "")));
				save();
			}
			
			reload();
		}
		catch (IOException ioe) {
			close();
			
			throw ioe;
		}
	}

	private void reload() {
		String target;
		
		this.timer = new Timer(true);
		
		for (Object key : this.json.keySet()) {
			target = (String)key;
			
			try {
				this.timer.schedule(new Schedule(InetAddress.getByName(target), this.json.getJSONObject(target)), INTERVAL);
			} catch (UnknownHostException e) {
				e.printStackTrace();
			}
		}
	}
	
	private void save() {
		try {
			this.file.setLength(0);
			
			this.channel.write(ByteBuffer.wrap(this.json.toString().getBytes(StandardCharsets.UTF_8.name())));
		} catch (IOException ioe) {
			ioe.printStackTrace();
		}
	}
	
	private void sendResponse(Request request, Response response) {
		//response.setResponseHeader("Access-Control-Allow-Headers", "Authorization, Content-Type");
		response.setResponseHeader("Access-Control-Allow-Origin", "http://itahm.com");
		response.setResponseHeader("Access-Control-Allow-Credentials", "true");
		
		try {
			request.sendResponse(response);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	@Override
	protected void onRequest(Request request) {
		Response response;
		
		switch(request.getRequestMethod()) {
		case "GET":
			response = Response.getInstance(Response.Status.OK, this.json.toString()).setResponseHeader("Content-Type", "application/json");
			
			break;
		case "POST":
			try {
				this.timer.cancel();
				
				synchronized(this.json) {
					this.json = new JSONObject(new String(request.getRequestBody(), StandardCharsets.UTF_8.name()));
				
					reload();
					
					save();
				}				
				
				response = Response.getInstance(Response.Status.OK);
			} catch (UnsupportedEncodingException | JSONException e) {
				response = Response.getInstance(Response.Status.BADREQUEST);
			}
			
			break;
		default:
			response = Response.getInstance(Response.Status.NOTALLOWED);
		}
		
		sendResponse(request, response);
	}
	
	@Override
	public void close() {
		this.timer.cancel();
		
		try {
			this.channel.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		try {
			this.file.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		try {
			super.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public static void main(String [] args) {
		int tcp = 2015;
		
		if (args.length > 0) {
			try {
				tcp = Integer.parseInt(args[0]);
			} catch (NumberFormatException nfe) {
			}
		}
		
		try {
			final Mini mini = new Mini(tcp);
			
			Runtime.getRuntime().addShutdownHook(new Thread() {
				public void run() {
					mini.close();
				}
			});
		} catch (BindException be) {
			System.out.println(String.format("다른 응용프로그램이 TCP %d 를(을) 사용중입니다.", tcp));
		} catch (IOException ioe) {
			// TODO Auto-generated catch block
			ioe.printStackTrace();
		}
	}
	
	class Schedule extends TimerTask {

		private final JSONObject data;
		private final InetAddress target;
		
		public Schedule(InetAddress target, JSONObject data) {
			this.data = data;
			this.target = target;
		}
		
		@Override
		public void run() {
			try {
				this.data.put("echo", this.target.isReachable(TIMEOUT));
				
				timer.schedule(new Schedule(this.target, this.data), INTERVAL);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
	}
	
}
