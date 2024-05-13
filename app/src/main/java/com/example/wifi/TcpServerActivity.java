package com.example.wifi;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

public class TcpServerActivity extends Activity implements OnClickListener {
	private final String TAG = "TcpServerActivity";
	private TextView mServerState, mTvReceive;
	private Button mBtnSet, mBtnStrat, mBtnSend;
	private EditText mEditMsg;
	private ServerSocket mServerSocket;
	public Socket mSocket;

	private SharedPreferences mSharedPreferences;
	private final int DEFAULT_PORT = 8086;
	private int mServerPort; //服务端端口

	private static final String SERVER_PORT = "server_port";
	private static final String SERVER_MESSAGETXT = "server_msgtxt";
	private OutputStream mOutStream;
	private InputStream mInStream;
	private SocketAcceptThread mAcceptThread;
	private SocketReceiveThread mReceiveThread;

	private HandlerThread mHandlerThread;
	//子线程中的Handler实例。
	private Handler mSubThreadHandler;

	private final int STATE_CLOSED = 1;
	private final int STATE_ACCEPTING= 2;
	private final int STATE_CONNECTED = 3;
	private final int STATE_DISCONNECTED = 4;

	private int mSocketConnectState = STATE_CLOSED;

	private String mRecycleMsg;
	private static final int MSG_TIME_SEND = 1;
	private static final int MSG_SOCKET_CONNECT = 2;
	private static final int MSG_SOCKET_DISCONNECT = 3;
	private static final int MSG_SOCKET_ACCEPTFAIL = 4;
	private static final int MSG_RECEIVE_DATA = 5;
	private static final int MSG_SEND_DATA = 6;
	private Handler mHandler = new Handler(){
		public void handleMessage(Message msg) {
			switch(msg.what){
				case MSG_TIME_SEND:
					writeMsg(mRecycleMsg);
					break;
				case MSG_SOCKET_CONNECT:
					mSocketConnectState = STATE_CONNECTED;
					mServerState.setText(R.string.state_connected);
					mReceiveThread = new SocketReceiveThread();
					mReceiveThread.start();
					break;
				case MSG_SOCKET_DISCONNECT:
					mSocketConnectState = STATE_DISCONNECTED;
					mServerState.setText(R.string.state_disconect_accept);
					startAccept();
					break;
				case MSG_SOCKET_ACCEPTFAIL:
					startAccept();
					break;
				case MSG_RECEIVE_DATA:
					String text = mTvReceive.getText().toString() +"\r\n" + (String)msg.obj;
					mTvReceive.setText(text);
					break;
				default:
					break;
			}
		};
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_server);
		mServerState = (TextView) findViewById(R.id.serverState);
		mBtnSet = (Button)findViewById(R.id.bt_server_set);
		mBtnStrat = (Button)findViewById(R.id.bt_server_start);
		mBtnSend = (Button)findViewById(R.id.bt_server_send);
		mEditMsg = (EditText)findViewById(R.id.server_sendMsg);
		mTvReceive = (TextView) findViewById(R.id.server_receive);
		mBtnSet.setOnClickListener(this);
		mBtnStrat.setOnClickListener(this);
		mBtnSend.setOnClickListener(this);
		mSharedPreferences = getSharedPreferences("setting", Context.MODE_PRIVATE);

		mServerPort = mSharedPreferences.getInt(SERVER_PORT, DEFAULT_PORT);

		initHandlerThraed();
	}

	@Override
	protected void onResume() {
		super.onResume();
		if(mSocketConnectState == STATE_CLOSED)
			mServerState.setText(R.string.state_closed);
		else if(mSocketConnectState == STATE_CONNECTED)
			mServerState.setText(R.string.state_connected);
		else if(mSocketConnectState == STATE_DISCONNECTED || mSocketConnectState == STATE_ACCEPTING)
			mServerState.setText(R.string.state_disconect_accept);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		Log.i(TAG, "onDestroy");
		//退出HandlerThread的Looper循环。
		mHandlerThread.quit();
		closeConnect();
		if(mServerSocket != null){
			try {
				mServerSocket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	@Override
	public void onClick(View v) {
		if (v.getId() == R.id.bt_server_set) {
			set();
		} else if (v.getId() == R.id.bt_server_start) {
			startServer();
		} else if (v.getId() == R.id.bt_server_send) {
			sendTxt();
		}
	}

	private void set(){
		View setview = LayoutInflater.from(this).inflate(R.layout.dialog_serverset, null);
		final EditText editport = (EditText)setview.findViewById(R.id.server_port);
		Button ensureBtn = (Button)setview.findViewById(R.id.server_ok);

		editport.setText(mSharedPreferences.getInt(SERVER_PORT, 8086) + "");

		final AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setView(setview); //设置dialog显示一个view
		final AlertDialog dialog = builder.show(); //dialog显示
		ensureBtn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				String port = editport.getText().toString();
				if(port != null && port.length() >0){
					mServerPort = Integer.parseInt(port);
				}
				SharedPreferences.Editor editor=mSharedPreferences.edit();
				editor.putInt(SERVER_PORT, mServerPort);
				editor.commit();
				dialog.dismiss(); //dialog消失
			}
		});

	}
	private void startServer() {
		if(mSocketConnectState != STATE_CLOSED) return;
		try {
			//开启服务、指定端口号
			mServerSocket = new ServerSocket(mServerPort);
		} catch (IOException e) {
			e.printStackTrace();
			mSocketConnectState = STATE_DISCONNECTED;
			Toast.makeText(this, "服务开启失败", Toast.LENGTH_SHORT).show();
			return;
		}
		startAccept();
		mServerState.setText(getString(R.string.state_opened));
		Toast.makeText(this, "服务开启", Toast.LENGTH_SHORT).show();
	}
	private void startAccept(){
		mSocketConnectState = STATE_ACCEPTING;
		mAcceptThread = new SocketAcceptThread();
		mAcceptThread.start();
	}
	private void sendTxt(){
		if(mRecycleMsg != null){
			//每次点击发送按钮发送数据，将之前的定时发送移除。
			mHandler.removeMessages(MSG_TIME_SEND);
			mRecycleMsg = null;
		}
		if(mSocket == null){
			Toast.makeText(this, "没有客户端连接", Toast.LENGTH_SHORT).show();
			return;
		}
		String str = mEditMsg.getText().toString();
		if(str.length() == 0)
			return;
		Message msg = new Message();
		msg.what = MSG_SEND_DATA;
		msg.obj = str;
		mSubThreadHandler.sendMessage(msg);
	}

	private void writeMsg(String msg){
		if(msg.length() == 0 || mOutStream == null)
			return;
		try {
			mOutStream.write(msg.getBytes());//发送
			mOutStream.flush();
		}catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void closeConnect(){
		try {
			if (mOutStream != null) {
				mOutStream.close();
			}
			if (mInStream != null) {
				mInStream.close();
			}
			if(mSocket != null){
				mSocket.close();  //关闭socket
				mSocket = null;
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		if(mReceiveThread != null){
			mReceiveThread.threadExit();
			mReceiveThread = null;
		}
	}

	class SocketAcceptThread extends Thread{
		@Override
		public void run() {
			try {
				//等待客户端的连接，Accept会阻塞，直到建立连接，
				//所以需要放在子线程中运行。
				mSocket = mServerSocket.accept();
				//获取输入流
				mInStream = mSocket.getInputStream();
				//获取输出流
				mOutStream = mSocket.getOutputStream();
			} catch (IOException e) {
				e.printStackTrace();
				mHandler.sendEmptyMessage(MSG_SOCKET_ACCEPTFAIL);
				return;
			}
			Log.i(TAG, "accept success");
			mHandler.sendEmptyMessage(MSG_SOCKET_CONNECT);
		}
	}
	class SocketReceiveThread extends Thread{
		private boolean threadExit = false;
		public void run(){
			byte[] buffer = new byte[1024];
			while(threadExit == false){
				try { //读取数据，返回值表示读到的数据长度。-1表示结束
					int count = mInStream.read(buffer);
					if(count == -1){
						Log.i(TAG, "read read -1");
						mHandler.sendEmptyMessage(MSG_SOCKET_DISCONNECT);
						break;
					}else{
						String receiveData;
						receiveData = new String(buffer, 0, count);
						Log.i(TAG, "read buffer:"+receiveData+",count="+count);
						Message msg = new Message();
						msg.what = MSG_RECEIVE_DATA;
						msg.obj = receiveData;
						mHandler.sendMessage(msg);
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

		void threadExit(){
			threadExit = true;
		}
	}

	private void initHandlerThraed() {
		//创建HandlerThread实例
		mHandlerThread = new HandlerThread("handler_thread");
		//开始运行线程
		mHandlerThread.start();
		//获取HandlerThread线程中的Looper实例
		Looper loop = mHandlerThread.getLooper();
		//创建Handler与该线程绑定。
		mSubThreadHandler = new Handler(loop){
			public void handleMessage(Message msg) {
				Log.i(TAG, "mSubThreadHandler handleMessage thread:"+Thread.currentThread());
				switch(msg.what){
					case MSG_SEND_DATA:
						writeMsg((String)msg.obj);
						break;
					default:
						break;
				}
			};
		};
	}
}
