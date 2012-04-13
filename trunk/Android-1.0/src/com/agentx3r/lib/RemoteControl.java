/*
 * Copyright (C) 2012 Paul Bovbel, paul@bovbel.com
 * 
 * This file is part of the Mover-Bot robot platform (http://code.google.com/p/mover-bot/)
 * 
 * Mover-Bot is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This source code is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this source code. If not, see http://www.gnu.org/licenses/
 */

package com.agentx3r.lib;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;


import android.os.Handler;
import android.os.Looper;
import android.os.Message;

/** Configures an IP connection and its input/output streams.
 * 
 * construct with port to create server and listen for clients
 * construct with ip and port to connect to server as client
 * 
 * Call this.send to sent a byte array
 * Override onReceive to process incoming bytes
 */

public abstract class RemoteControl extends Thread{

	//Running Vars
	public boolean connected = false;
	boolean isServer = false;

	ServerSocket controlServerSocket;
	Socket controlSocket;

	ServerSocket imageServerSocket;
	public Socket imageSocket;

	BufferedReader input;
	BufferedWriter output;
	Handler controlSender;
	Thread controlListener;
	//Linkback to UI Thread
	Handler UIHandler;

	//Default Parameters
	String controlIp = "127.0.0.1";
	int controlPort = 9090;
	int imagePort = 9095;

	//Constants
	public final String CLRF = "\r\n";

	protected RemoteControl(int port, Handler ui){

		//Server setup: Load in listening port
		super("RCServer");
		isServer = true;
		this.setDaemon(true);
		controlPort = port;
		imagePort = port + 5;
		UIHandler = ui;
		this.start();
	}

	protected RemoteControl(String ip, int port, Handler ui){

		//Client setup: Load in target IP and Port
		super("RCClient");
		this.setDaemon(true);
		controlIp = ip;
		controlPort = port;
		imagePort = port + 5;
		UIHandler = ui;
		this.start();
	}

	//send message to rover
	public void send(String command){
		if (controlSender != null){
			Message msg = controlSender.obtainMessage();
			msg.obj = command;
			controlSender.sendMessage(msg);
		}
	}

	//send messages to rover
	public void send(String[] commands){
		StringBuilder command = new StringBuilder();
		for (String temp : commands){
			command.append(temp);
		}
		send(command.toString());
	}

	public abstract void onReceive(String[] msg);

	public abstract void onNotify(String msg);

	public abstract void onStart();

	public abstract void onConnected();

	public abstract void onDisconnected();

	public abstract void onShutdown();

	public void shutdown() throws IOException{
		this.interrupt();
		this.disconnect();
		UIHandler.post(new Runnable() {
			public void run() {
				onShutdown();
			}
		});		
	}

	//Process messages from rover
	public void receive(String[] commands){
		for (String command : commands){
			//check not empty command
			if (command.matches(".*\\w.*")){
				final String msg[] = command.split("=");
				UIHandler.post(new Runnable() {
					public void run() {
						onReceive(msg);
					}
				});		

			}
		}
	}

	//Shutdown connection
	public void disconnect() throws IOException{
		if(controlSender !=null){
			controlSender.getLooper().quit();
			controlSender = null;
		}
		if(controlListener !=null){
			controlListener.interrupt();
			controlListener = null;
		}
		if(input !=null){
			input.close();
			input = null;
		}
		if(output !=null){
			output.close();
			output = null;
		}	
		if(controlSocket !=null){
			controlSocket.close();
			controlSocket = null;
		}

		connected = false;

		UIHandler.post(new Runnable() {
			public void run() {
				onNotify("Wifi Disconnected\n");
				onDisconnected();
			}
		});
	}

	public void run() {
		try {
			while(!isInterrupted()){

				if(isServer){
					controlServerSocket = new ServerSocket(controlPort);
					imageServerSocket = new ServerSocket(imagePort);
					UIHandler.post(new Runnable() {
						public void run() {
							onNotify("Listening on IP: " + Util.getLocalIpAddress() + ":" + controlPort + "\n"); 
							onStart();
						}
					});	
					controlSocket = controlServerSocket.accept();
					imageSocket = imageServerSocket.accept();
				}else{
					InetAddress controlAddr = InetAddress.getByName(controlIp);
					controlSocket = new Socket(controlAddr, controlPort);
					imageSocket = new Socket(controlAddr, imagePort);
				}
				connected = controlSocket.isConnected();
				if(connected){
					UIHandler.post(new Runnable() {
						public void run() {
							onNotify("Connected to: " + controlSocket.getInetAddress().toString()  +"\n");
							onConnected();
						}
					});					
					//setup input
					input = new BufferedReader(new InputStreamReader(controlSocket.getInputStream()));
					controlListener = new Thread(new Runnable() {
						boolean running = true;
						
						public void run() {
							String line = null;
							while(running){
								try{
									//Handle incoming messages
									while (input != null && (line = input.readLine()) != null && running){
										receive(line.split(";"));
										Thread.sleep(5);
									}
								}catch (final Exception e){
									UIHandler.post(new Runnable() {
										public void run() {
											onNotify("Wifi Receive Failed " + e.toString() + "\n");
										}
									});
									running = false;
								}
							}
						}
					});
					controlListener.setDaemon(true);
					controlListener.setName("RemoteControlClientListener");
					controlListener.start();		

					//setup output
					output = new BufferedWriter(new OutputStreamWriter(controlSocket.getOutputStream()));
					Looper.prepare();
					controlSender = new Handler() {
						public void handleMessage(Message msg) {
							try{
								//Handle outgoing messages
								output.write((String)msg.obj + CLRF);
								output.flush();
								Thread.sleep(5);
							}catch(final Exception e){

								UIHandler.post(new Runnable() {
									public void run() {
										onNotify("Wifi Send Failed " + e.toString() + "\n");
									}
								});
								this.getLooper().quit();
							}						
						}
					};
					Looper.loop();

				}else{
					throw new Exception("Server timed out");
				}
			}
		}catch (final Exception e){
			UIHandler.post(new Runnable() {
				public void run() {
					onNotify(e.getMessage() + "\n");
				}
			});
		}finally{
			//On client disconnect					
			try {
				disconnect();
			} catch (IOException e) {
			}
		}


		if(isServer){
			//On server shutdown
			try {
				controlServerSocket.close();

				UIHandler.post(new Runnable() {
					public void run() {
						onNotify("Server Shutdown\n");
					}
				});
			} catch (final Exception e) {
				UIHandler.post(new Runnable() {
					public void run() {
						onNotify("Error Closing Socket " + e.getMessage() + "\n");
					}
				});
			}
		}
	}
}