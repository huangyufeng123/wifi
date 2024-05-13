package com.example.wifi;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

public class MainActivity extends Activity implements OnClickListener {
	private final String TAG = "MainActivity";
	private Button mBtnClient;
	private Button mBtnServer;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		mBtnClient = (Button) findViewById(R.id.bt_client);
		mBtnServer = (Button) findViewById(R.id.bt_server);
		mBtnClient.setOnClickListener(this);
		mBtnServer.setOnClickListener(this);
		
	}

	@Override
	public void onClick(View v) {
		if(v.getId()==R.id.bt_client){
			//做Tcp客户端
			Intent client = new Intent(this,TcpClientActivity.class);
			startActivity(client);
		}
		else if(v.getId()==R.id.bt_server){
			//做Tcp服务端
			Intent server = new Intent(this,TcpServerActivity.class);
			startActivity(server);
		}
	}
}
