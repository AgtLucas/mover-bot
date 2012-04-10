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

package com.agentx3r.moverbot;

import java.io.BufferedInputStream;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapActivity;
import com.google.android.maps.MapController;
import com.google.android.maps.MapView;
//import com.google.android.maps.MyLocationOverlay;

import com.agentx3r.lib.RemoteControl;
import com.agentx3r.lib.TaskTemplate;
import com.agentx3r.lib.Util;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** This is the manually-launched activity for the project. It will connect
 * to another device running the Mover-bot server.
 */

public class RoverClientActivity extends MapActivity {

	private static final String TAG = "RoverClient";
	
	//Network
	private static String defaultIp="192.168.1.116";
	public static final int controlPort = 9090;// DESIGNATE A PORT
	private RemoteControlClient remoteConnection;
	
	//Task UI
	LinearLayout status_box;
	Task network;
	Task battery;
	private int battery_level;
	Task motors;
	Task light;
	Task mag;
	Task accel;
	Task gps;
	TextView console;
	ScrollView console_scroll;
	
	//Video
	Bitmap bm;
	
	//Map UI
	MapView mapView;
	MapController mapController;
	Bitmap arrow;
	ImageView map_arrow;
	TextView accel_overlay;

	//Touchpad Settings
	int touch_width;
	int touch_height;
	final int reverse_percent=100;
	final int dead_zone=15;
	final int turn_sensitivity=2;

	//Handler, Popup
	private Handler UIHandler = new Handler();
	private Dialog popup;

	//Screen timeout
	int defTimeOut = 0;
	private static final int DELAY = 3600000; //6 minute screen timeout

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.client);

		//UI setup
		setupUI();

	}

	public void onPause() {	
		super.onPause();

	}

	public void onDestroy() {	
		super.onDestroy();
		try{
			remoteConnection.shutdown();
		}catch(Exception e){
			console("Error shutting down connection\n");
		}
		Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT, defTimeOut);
	}



	//------UI
	private void setupUI(){

		mapView = (MapView) findViewById(R.id.mapView);      
		mapView.setBuiltInZoomControls(true);
		mapController = mapView.getController();
		mapController.setCenter(new GeoPoint(43642644, -79387100));
		mapController.setZoom(19);
		//		locationOverlay = new MyLocationOverlay(this, mapView);
		//		mapView.getOverlays().add(locationOverlay);
		//		mapView.postInvalidate();
		arrow = BitmapFactory.decodeResource(getApplicationContext().getResources(), R.drawable.mapdot);
		map_arrow = (ImageView)findViewById(R.id.mapArrow);

		console = (TextView)findViewById(R.id.ConsoleText);
		console_scroll= (ScrollView)findViewById(R.id.ConsoleScroll);
		console("Device IP: " +Util.getLocalIpAddress() +"\n");
		console("Gateway IP: " +Util.getHostIpAddress(getApplicationContext())+"\n");
		accel_overlay = (TextView)findViewById(R.id.AccelText);
		status_box = (LinearLayout)findViewById(R.id.StatusBox);
		final LinearLayout video_box = (LinearLayout)findViewById(R.id.VideoBox);
		video_box.post(new Runnable() {   //get size after touchpad drawn
			public void run() {
				ViewGroup.LayoutParams params = video_box.getLayoutParams();
				params.width = video_box.getHeight()*288/352;
				video_box.setLayoutParams(params);
			}
		});

		//Screen timeout
		defTimeOut = Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT, DELAY);
		Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT, DELAY);

		final ImageView touchpad = (ImageView)findViewById(R.id.touchpad);

		touchpad.post(new Runnable() {   //get size after touchpad drawn
			public void run() {
				touch_width = touchpad.getWidth();
				touch_height = touchpad.getHeight();
			}
		});

		touchpad.setOnTouchListener(new View.OnTouchListener() {

			long timeout = SystemClock.uptimeMillis();
			final long delay = 10;

			public boolean onTouch(View v, MotionEvent event) {

				int y = 0;
				int x = 0;
				switch (event.getAction()) {
				case MotionEvent.ACTION_DOWN:
				case MotionEvent.ACTION_MOVE:
					//Measure touch
					y =  (touch_height-(int)event.getY())*(100+reverse_percent)/touch_height-reverse_percent;
					x = ((int)event.getX()-touch_width/2)*100/touch_width*2;
					//Deadzone
					if (Math.abs(y) < dead_zone){
						y = 0;
					}
					//send data with delay
					if(SystemClock.uptimeMillis() > timeout){
						if (remoteConnection != null && remoteConnection.connected){					
							remoteConnection.send("speed="+y+";turn="+x/turn_sensitivity);					
						}
						timeout = SystemClock.uptimeMillis() + delay;
					}
					break;
				case MotionEvent.ACTION_UP:
					//send data
					if (remoteConnection != null && remoteConnection.connected){					
						remoteConnection.send("speed="+y+";turn="+x/turn_sensitivity);				
						remoteConnection.send("speed="+y+";turn="+x/turn_sensitivity);	
						remoteConnection.send("speed="+y+";turn="+x/turn_sensitivity);	
					}
					break;
				}
				return true;
			}
		});		
		//Drive button
		Button drivebutton = (Button)findViewById(R.id.drivebutton);
		drivebutton.setOnClickListener(new Button.OnClickListener(){

			public void onClick(View v) {

				if (remoteConnection != null && remoteConnection.connected){
					remoteConnection.send("drive="+String.valueOf(!motors.enabled));
				}else{
					toast("Not Connected!\n");
				}
			}
		});

		Button lightbutton = (Button)findViewById(R.id.lightbutton);
		lightbutton.setOnClickListener(new Button.OnClickListener(){

			public void onClick(View v) {

				if (remoteConnection != null && remoteConnection.connected){
					remoteConnection.send("light="+String.valueOf(!light.enabled));
				}else{
					toast("Not Connected!\n");
				}
			}
		});

		network = new Task("Network");
		motors = new Task("Motors");
		light = new Task("Light");
		accel = new Task("Accel");
		mag = new Task("Mag");
		gps = new Task("GPS");
		battery = new Task("Battery");

	}

	//----TASK DISPLAY------
	private class Task extends TaskTemplate{

		Task(String name) { super(name); }
		@Override
		public Activity getActivity() { return RoverClientActivity.this; }
		@Override
		public ViewGroup getHolder() { return status_box; }
	}

	//-------OPTIONS
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);

		menu.add("Connect...");
		menu.add("Disconnect");

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {

		if (item.getTitle() == "Connect...") {
			if (remoteConnection != null && remoteConnection.connected) {				
				toast("Already Connected!");
			}else{
				connectionPopup();
			}
		}

		if (item.getTitle() == "Disconnect") {
			try{
				remoteConnection.disconnect();
			}catch(Exception e){
				console("Error disconnecting from server\n");
			}
		}
		return true;
	}


	//------------POPUP------------------
	private void connectionPopup(){

		popup = new Dialog(RoverClientActivity.this);
		popup.setContentView(R.layout.connectionsetting);
		popup.setTitle("Connect to Server");
		popup.setCancelable(true);

		final EditText serverIpInput = (EditText) popup.findViewById(R.id.ipAdress);
		serverIpInput.setText(defaultIp);

		//Connect Button
		Button connect = (Button) popup.findViewById(R.id.connect);
		connect.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {

				if (remoteConnection != null && remoteConnection.connected) {
					toast("Already Connected!");
				}else if(!Util.validIP(serverIpInput.getText().toString())){
					toast("Invalid IP!");
				}else{		
					network.pause();
					remoteConnection = new RemoteControlClient(serverIpInput.getText().toString(), controlPort, UIHandler);
					popup.hide();
				}
			}

		});

		//Auto Connect button
		Button autoconnect = (Button) popup.findViewById(R.id.autoconnect);
		autoconnect.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {

				if (remoteConnection != null && remoteConnection.connected) {
					toast("Already connected!");
				}else{
					network.pause();
					remoteConnection = new RemoteControlClient(Util.getHostIpAddress(getApplicationContext()), controlPort, UIHandler);
					popup.hide();
				}
			}
		});

		popup.show();

	}

	//----COMMUNICATION---------------
	private class RemoteControlClient extends RemoteControl{

		RemoteControlClient(String ip, int port, Handler ui) {
			super(ip, port, ui);
		}

		@Override
		public void onReceive(String[] msg) {
			if(msg[0].equals("light")){
				//Enable/disable flashlight
				if (msg[1].equals("true")){
					light.enable();
				}else if (msg[1].equals("false")){
					light.disable();
				}else{
					toast("Message Error");
				}
			}else if(msg[0].equals("batt")){
				//Parse battery level
				battery_level = Integer.parseInt(msg[1]);
				battery.text.setText("Batt: " + battery_level + "%");
				int temp = battery_level/20;
				switch(temp){
				case 5:
					temp = R.drawable.stat_sys_battery_100;
					break;
				case 4:
					temp = R.drawable.stat_sys_battery_80;
					break;
				case 3:
					temp = R.drawable.stat_sys_battery_60;
					break;
				case 2:
					temp = R.drawable.stat_sys_battery_40;
					break;
				case 1:
					temp = R.drawable.stat_sys_battery_20;
					break;
				case 0:
					temp = R.drawable.stat_sys_battery_0;
					break;
				}
				battery.led.setImageResource(temp);

			}else if(msg[0].equals("gps")){
				//Location Data
				gps.enable();
				String[] geo = msg[1].split(",");								
				mapController.setCenter(new GeoPoint((int)(Double.parseDouble(geo[0]) * 1e6), (int)(Double.parseDouble(geo[1]) * 1e6)));

			}else if(msg[0].equals("acc")){
				//Accelerometer data
				accel.enable();
				String[] accel = msg[1].split(",");							
				accel_overlay.setText("AccX: " + accel[0] + "\nAccY: " + accel[1] + "\nAccZ: " + accel[2] );

			}else if(msg[0].equals("mag")){
				//Compass data
				mag.enable();
				float bearing = Float.parseFloat(msg[1]);
				int w = arrow.getWidth();
				int h = arrow.getHeight();
				// Setting post rotate to 90
				Matrix mtx = new Matrix();
				mtx.postRotate(bearing);
				// Rotating Bitmap
				Bitmap rotatedarrow = Bitmap.createBitmap(arrow, 0, 0, w, h, mtx, true);
				map_arrow.setImageBitmap(rotatedarrow);

			}else if(msg[0].equals("drive")){
				//Parse drive status
				if (msg[1].equals("true")){
					motors.enable();
				}else if (msg[1].equals("false")){
					motors.disable();
				}else{
					toast("Message Error");
				}
			}
		}

		@Override
		public void onNotify(String msg) {console(msg);}

		@Override
		public void onConnected() {network.enable();startVideo();}

		@Override
		public void onDisconnected() {network.disable();}

		@Override
		public void onStart() {network.pause();}

		@Override
		public void onShutdown() {network.disable();}

	}



	void startVideo(){

		try{
			final ImageView frame=(ImageView)findViewById(R.id.frame);

			Thread imageThread = new Thread(new Runnable(){
				public void run(){
					try{
						console("Listening for video\n");
						BufferedInputStream input = new BufferedInputStream(remoteConnection.imageSocket.getInputStream());
						byte[] buffer = new byte[512000];	
						//						int frame_num = 0;
						while(remoteConnection.connected){
							int i = 0;							
							boolean capture = false;						
							byte prev = 0;
							byte cur = 0;

							while(!capture){								
								cur = (byte)input.read();
								//SOI
								if (cur == (byte)0xD8 && prev == (byte)0xFF){
									//console("found header!\n");
									buffer[i++] = prev;
									buffer[i++] = cur;

									while(!capture){

										buffer[i++] = (byte)input.read();
										//EOI
										if (buffer[i-1] == (byte)0xD9 && buffer[i-2] == (byte)0xFF){
											//console("done capture\n");
											bm = BitmapFactory.decodeByteArray(buffer, 0, i);
											if(bm != null){									
												//console("setting bitmap");
												int w = bm.getWidth();
												int h = bm.getHeight();
												// Setting post rotate to 90
												Matrix mtx = new Matrix();
												mtx.postRotate(90);
												// Rotating Bitmap
												final Bitmap rotatedbm = Bitmap.createBitmap(bm, 0, 0, w, h, mtx, true);

												UIHandler.post(new Runnable() {
													public void run() {
														frame.setImageBitmap(rotatedbm);											
													}
												});					
											}else{
												console("failed to decode\n");
											}
											capture = true;
											//											frame_num++;
										}

									}

								}else{
									prev = cur;
								}
							}
						}
					}catch(Exception e){
						console(e);						
					}
				}
			},"imageSender");
			imageThread.setDaemon(true);
			imageThread.start();
		}catch (Exception e){
			console(e);
		}


	}

	public void toast (final Object msg){
		UIHandler.post(new Runnable() {
			public void run() {
				Toast.makeText(getApplicationContext(), msg.toString(), Toast.LENGTH_SHORT).show();	
				Log.i(TAG, msg.toString());
			}
		});
	}

	public void console (final Object msg){
		UIHandler.post(new Runnable() {
			public void run() {
				Log.i(TAG, msg.toString());
				console.append(msg.toString());
				console_scroll.fullScroll(View.FOCUS_DOWN);
			}
		});
	}

	@Override
	protected boolean isRouteDisplayed() {return false;}

}