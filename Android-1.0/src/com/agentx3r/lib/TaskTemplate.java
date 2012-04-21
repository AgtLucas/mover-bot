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

import com.agentx3r.moverbot.R;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

/* Utility class for handling status lights for an application.
 * 
 * Override getActivity and getHolder with the appropriate container to add
 * status lights programmatically.
 */

public abstract class TaskTemplate {
	public ImageView led;
	public TextView text;
	public boolean enabled;

	public abstract Activity setActivity();
	public abstract ViewGroup setHolder();
	
	public TaskTemplate(String name){
		View status_bar = setActivity().getLayoutInflater().inflate(R.layout.status_bar, null);
		text = (TextView) status_bar.findViewById(R.id.text);
		text.setText(name +":");

		led = (ImageView) status_bar.findViewById(R.id.led);
		this.disable();
		enabled = false;

		setHolder().addView(status_bar);
	}

	public void enable(){
		led.setImageResource(R.drawable.green_led);	
		enabled = true;
	}

	public void pause(){
		led.setImageResource(R.drawable.yellow_led);	
		enabled = false;
	}

	public void disable(){
		led.setImageResource(R.drawable.red_led);
		enabled = false;
	}

}