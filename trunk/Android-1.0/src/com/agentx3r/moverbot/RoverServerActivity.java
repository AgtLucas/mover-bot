package com.agentx3r.moverbot;

import java.io.BufferedOutputStream;
import java.util.List;

import com.agentx3r.lib.RemoteControl;
import com.agentx3r.lib.TaskTemplate;
import com.agentx3r.lib.USBControl;
import com.agentx3r.lib.Util;

import android.app.Activity;
import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.Camera.Parameters;

/** This activity is launched automatically when the appropriate USB
 * accessory is connected. It then sets up a server and waits for a 
 * Mover-bot client to connect.
 * 
 * Will likely give nullpointer exception if you try to manually launch it.
 */

public class RoverServerActivity extends Activity {

	private static final String TAG = "RoverServer";
	
	//Handler, Threads
	private Handler UIHandler = new Handler();
	private RemoteControlServer remoteConnection;
	private USBControlServer usbConnection;
	
	//Video
	SurfaceView preview;
	SurfaceHolder previewHolder;
	Camera camera;

	//UI task statusbox
	LinearLayout status_box;
	Task network;
	Task battery;
	Task motors;
	Task streaming;
	Task mag;
	Task light;
	Task accel;
	Task gps;
	Task usb;

	//Other UI
	TextView console;
	Button killswitch;
	ToggleButton toggle_network;
	ToggleButton toggle_usb;
	ScrollView console_scroll;
	int defTimeOut = 0;
	private static final int DELAY = 3600000; //6 minute screen timeout

	//Protocol Messages
	final static byte SYNC = 's';
	final static byte BATTERY_LEVEL = 'b';
	int battery_level;
	final static byte SPEED ='v';
	final static byte TURN ='t';
	final static byte DRIVE ='m';
	final static byte ENABLED ='e';
	final static byte DISABLED ='d';

	//Sensors
	private SensorManager mSensorManager;
	private Sensor accSensor;
	private Sensor magSensor;
	LocationManager mLocationManager;
	LocationProvider locationProvider;

	//Server   
	public static final int dataPort = 9090;// DESIGNATE A PORT

	//Activity Lifecycle
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.server);

		setupUI();
		setupUSB();
		setupNetwork();
		setupSensors();

	}

	@Override
	protected void onResume() {
		super.onResume();

		resumeSensors();
	}

	@Override
	public void onPause() {
		super.onPause();

		pauseSensors();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();

		closeNetwork();
		closeUSB();

		//Screen Timeout
		Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT, defTimeOut);

				System.runFinalizersOnExit(true);
				System.exit(0);
	}

	//UI Methods
		private void setupUI(){

			//Screen timeout
			defTimeOut = Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT, DELAY);
			Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT, DELAY);

			//Tasks
			status_box = (LinearLayout)findViewById(R.id.StatusBox);
			network = new Task("Network");
			usb = new Task("USB");		
			motors = new Task("Motors");
			streaming = new Task("Video");
			light = new Task("Light");
			accel = new Task("Accel");
			mag = new Task("Mag");		
			gps = new Task("GPS");
			battery = new Task("Battery");
			battery.led.setImageResource(R.drawable.stat_sys_battery_0);
			console = (TextView)findViewById(R.id.ConsoleText);
			console_scroll= (ScrollView)findViewById(R.id.ConsoleScroll);
			killswitch = (Button)findViewById(R.id.killswitch);
			killswitch.setOnClickListener(new Button.OnClickListener()
			{
				public void onClick(View arg0) {
					usbConnection.driveEnabled(false);
				}

			});

			ToggleButton toggle_streaming= (ToggleButton)findViewById(R.id.toggle_network);
			toggle_streaming.setChecked(false);
			toggle_streaming.setOnCheckedChangeListener(new ToggleButton.OnCheckedChangeListener(){

				public void onCheckedChanged(CompoundButton toggle_network,	boolean isChecked) {
					if(remoteConnection.connected == true){				
						if(isChecked){
							streaming.enable();
						}else{
							streaming.disable();
						}
					}else{
						console("Not connected!\n");
						streaming.disable();
					}
				}
			});

			ToggleButton toggle_usb= (ToggleButton)findViewById(R.id.toggle_usb);
			toggle_usb.setChecked(true);
			toggle_usb.setEnabled(false);

		}

		private class Task extends TaskTemplate{

			Task(String name) {super(name);}
			@Override
			public Activity getActivity() {	return RoverServerActivity.this;}
			@Override
			public ViewGroup getHolder() {return status_box;}
		}
	
	//Sensor Methods
	private void setupSensors(){

		console("Configuring Sensors...");
		List<Sensor> sensors;

		mSensorManager = (SensorManager)getSystemService(Context.SENSOR_SERVICE);
		sensors = mSensorManager.getSensorList(Sensor.TYPE_LINEAR_ACCELERATION);
		if(sensors.size() > 0)
		{
			console("Accel..");
			accel.pause();
			accSensor = sensors.get(0);
		}

		sensors = mSensorManager.getSensorList(Sensor.TYPE_ORIENTATION);
		if(sensors.size() > 0)
		{
			console("Mag..");
			mag.pause();
			magSensor = sensors.get(0);
		}

		gps.pause();
		console("GPS..");
		mLocationManager=(LocationManager)this.getSystemService(Context.LOCATION_SERVICE);
		console("Done\n");
	}
	private void resumeSensors(){

		mSensorManager.registerListener(accSensorListener, accSensor, SensorManager.SENSOR_DELAY_NORMAL);
		mSensorManager.registerListener(magSensorListener, magSensor, SensorManager.SENSOR_DELAY_NORMAL);
		mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, gpsListener);
		mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, gpsListener);
		
		console("Starting All Sensors...");
		console("Done\n");
	}
	private void pauseSensors(){
		console("Pausing All Sensors...");

		mSensorManager.unregisterListener(accSensorListener);
		accel.pause();

		mSensorManager.unregisterListener(magSensorListener);
		mag.pause();

		mLocationManager.removeUpdates(gpsListener);
		gps.pause();

		console("Done\n");
	}
	
	//Sensor Listeners
	private SensorEventListener accSensorListener=new SensorEventListener()
	{
		long timeout = SystemClock.uptimeMillis();
		final long delay = 50;

		public void onSensorChanged(SensorEvent event)
		{   
			accel.enable();

			if(SystemClock.uptimeMillis() > timeout){

				if(remoteConnection != null && remoteConnection.connected){
					remoteConnection.send("acc=" + event.values[0] + "," + event.values[1] + "," + event.values[2]);
				}

				timeout = SystemClock.uptimeMillis() + delay;

			}
		}
		public void onAccuracyChanged(Sensor sensor, int accuracy) {}
	};

	private SensorEventListener magSensorListener=new SensorEventListener()
	{
		long timeout = SystemClock.uptimeMillis();
		final long delay = 500;

		public void onSensorChanged(SensorEvent event)
		{   
			mag.enable();

			if(SystemClock.uptimeMillis() > timeout){

				//console("mag=" + event.values[0] +"\n");
				if(remoteConnection != null && remoteConnection.connected){					
					remoteConnection.send("mag=" + event.values[0] +"\n");
				}

				timeout = SystemClock.uptimeMillis() + delay;
			}
		}
		public void onAccuracyChanged(Sensor sensor, int accuracy) {}
	};


	private LocationListener gpsListener = new LocationListener() {

		private Location previous_location;

		public void onLocationChanged(Location location) {

			gps.enable();
			if (remoteConnection != null && remoteConnection.connected && Util.isBetterLocation(location, previous_location)){

				Double latitude = (Double)location.getLatitude();
				Double longtude = (Double)location.getLongitude();
				Float bearing = (Float)location.getBearing();
				String locationStamp = latitude.toString()+","+longtude.toString()+","+bearing.toString();
				//console("Location Update\n");

				previous_location = location;
				remoteConnection.send("gps=" + locationStamp);

			}	
		}

		public void onProviderDisabled(String arg0) {}
		public void onProviderEnabled(String arg0) {}
		public void onStatusChanged(String arg0, int arg1,Bundle arg2) {}
	};

	//Network Setup
	private void setupNetwork()	{

		console("Starting Network...");
		network.pause();
		remoteConnection = new RemoteControlServer(dataPort, UIHandler);
		console("Done\n");
	}

	private void closeNetwork(){
		console("Closing Network...");
		try {
			if (remoteConnection != null){
				remoteConnection.shutdown();
			}
			console("Done\n");
		} catch (Exception e) {
			toast("Close network "+e.getMessage());
		}finally{
			//failsafe motors disable
			usbConnection.driveEnabled(false);
			network.disable();		
		}
	}

	private void setupUSB(){

		console("Starting USB...");
		usbConnection = new USBControlServer(UIHandler);
		console("Done\n");
	}

	private void closeUSB(){
		console("Closing USB...");
		usbConnection.closeAccessory();
		usbConnection.destroyReceiver();
		console("Done\n");	
	}

	public class USBControlServer extends USBControl{

		public USBControlServer(Handler ui) {
			super(getApplicationContext(), ui);
		}

		@Override
		public void onReceive(byte[] msg) {

			int i = 0;

			switch (msg[i+1]) {
			case BATTERY_LEVEL:
				int battery_level = (int)msg[i+2];
				remoteConnection.send("batt="+battery_level);
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

				break;
			}	
		}

		@Override
		public void onNotify(String msg) {
			console(msg);
		}

		@Override
		public void onConnected() {
			usb.enable();
		}

		@Override
		public void onDisconnected() {
			usb.pause();
			finish();
		}

		byte[] msg = new byte[3];

		void setSpeed(int speed){
			msg[0] = SYNC;
			msg[1] = SPEED;
			msg[2] = (byte) speed;
			usbConnection.send(msg);
		}

		void setTurn(int turn){
			msg[0] = SYNC;
			msg[1] = TURN;
			msg[2] = (byte)turn;
			usbConnection.send(msg);
		}

		void driveEnabled(boolean drive){
			msg[0] = SYNC;
			msg[1] = DRIVE;
			if (drive == true){
				msg[2] = ENABLED;
				console("Motors Enabled\n");
				motors.enable();
			}else{
				msg[2] = DISABLED;
				console("Motors Disabled\n");
				motors.disable();
			}
			usbConnection.send(msg);
			remoteConnection.send("drive="+Boolean.toString(drive));
		}

	}

	private class RemoteControlServer extends RemoteControl{

		RemoteControlServer(int port, Handler ui) {
			super(port, ui);
		}

		@Override
		public void onReceive(String[] msg) {
			
			console(msg[0] + msg[1] + "\n");
			
			if(msg[0].equals("speed")){
				if(usbConnection != null){
					usbConnection.setSpeed(Integer.parseInt(msg[1]));
				}

			}else if(msg[0].equals("turn")){
				//turn update
				if(usbConnection != null){
					usbConnection.setTurn(Integer.parseInt(msg[1]));
				}
			}else if(msg[0].equals("drive")){
				//drive update
				if(usbConnection != null){
					usbConnection.driveEnabled(msg[1].equals("true"));
				}
			}else if(msg[0].equals("light")){
				//light update
				if(camera!=null){		    					
					Camera.Parameters p = camera.getParameters();
					if(msg[1].equals("true")){
						p.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
						light.enable();
					}else{
						p.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
						light.disable();
					}
					remoteConnection.send("light="+String.valueOf(light.enabled));
					camera.setParameters(p);					
				}
			}else{
				//catchall
				console("Message Error:" + msg[0] + "=" + msg[1]);
			}

		}


		@Override
		public void onNotify(String msg) {console(msg);}

		@Override
		public void onConnected() {
			network.enable();
			startCamera();
		}

		@Override
		public void onDisconnected() {network.pause();}

		@Override
		public void onStart() {	network.pause();}

		@Override
		public void onShutdown() {network.disable();}

	}

	void startCamera(){
		if(camera != null){
			camera.release();
			camera = null;
		}
		try {
			HandlerThread imageThread = new HandlerThread("imageSender");
			imageThread.setDaemon(true);
			imageThread.start();

			final Handler imageSender = new Handler(imageThread.getLooper());
			final BufferedOutputStream output = new BufferedOutputStream(remoteConnection.imageSocket.getOutputStream());
			final int w = 352; final int h = 288;
			if (camera == null) {
				camera=Camera.open();
				camera.setDisplayOrientation(90);
				preview=(SurfaceView)findViewById(R.id.preview);
				previewHolder = preview.getHolder();
				camera.setPreviewDisplay(previewHolder);
				previewHolder.addCallback(surfaceCallback);
				previewHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
				Parameters params = camera.getParameters(); 
				params.setPreviewFormat(ImageFormat.NV21);
				params.setPreviewSize(w,h);
				params.setJpegQuality(20);
				camera.setParameters(params);
			}
			camera.setPreviewCallback(new PreviewCallback() {
				//				int frame_num = 0;
				long timeout = SystemClock.uptimeMillis();
				final long delay = 100;

				public void onPreviewFrame(final byte[] data, Camera arg1) {
					//					
					if (SystemClock.uptimeMillis() > timeout && streaming.enabled && remoteConnection.connected){						
						imageSender.post(new Runnable(){
							public void run(){
								try {
									//									frame_num++;
									YuvImage frame = new YuvImage(data, ImageFormat.NV21, w, h, null);																		
									frame.compressToJpeg(new Rect(0, 0, w, h), 80,output);
									output.flush();
								} catch (Exception e) {
									console(e);
								}

							}
						});

						timeout = SystemClock.uptimeMillis() + delay;
					}
				}

			});

			camera.startPreview();
			streaming.pause();
			console("Camera started");
		} catch (Exception e) {
			console(e);
		}
	}

	SurfaceHolder.Callback surfaceCallback=new SurfaceHolder.Callback() {
		public void surfaceCreated(SurfaceHolder holder) {
			try {
				camera.setPreviewDisplay(previewHolder);
			} catch (Exception e) {
				console(e);
			}
		}

		public void surfaceChanged(SurfaceHolder holder, int format, int width,	int height) {
			camera.startPreview();
		}

		public void surfaceDestroyed(SurfaceHolder holder) {		}
	};


	//Helper
	public void toast (final Object msg){
		UIHandler.post(new Runnable() {
			@Override
			public void run() {
				Toast.makeText(getApplicationContext(), msg.toString(), Toast.LENGTH_SHORT).show();	
				Log.i(TAG, msg.toString());
			}
		});
	}

	public void console (final Object msg){
		UIHandler.post(new Runnable() {
			@Override
			public void run() {
				Log.i(TAG, msg.toString());
				console.append(msg.toString());
				console_scroll.fullScroll(View.FOCUS_DOWN);
			}
		});
	}

}