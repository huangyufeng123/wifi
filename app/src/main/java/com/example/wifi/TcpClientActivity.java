package com.example.wifi;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
import android.widget.TextView;
import android.widget.Toast;

public class TcpClientActivity extends Activity implements OnClickListener {
	private final String TAG = "TcpClientActivity";
	private Button mBtnSet, mBtnConnect, mBtnSend;
	private EditText mEditMsg;
	private TextView mClientState, mTvReceive;
	public Socket mSocket;
	
	private SharedPreferences mSharedPreferences;
	private final int DEFAULT_PORT= 8086;
	private String mIpAddress;  //服务端ip地址
	private int mClientPort; //端口,默认为8086，可以进行设置
	private static final String IP_ADDRESS = "ip_address";
	private static final String CLIENT_PORT = "client_port";
	private static final String CLIENT_MESSAGETXT = "client_msgtxt";
	
	private OutputStream mOutStream;
	private InputStream mInStream;
	private SocketConnectThread mConnectThread;
	private SocketReceiveThread mReceiveThread;
	
	private HandlerThread mHandlerThread;
    //子线程中的Handler实例。
    private Handler mSubThreadHandler;
    
	private final int STATE_DISCONNECTED = 1;
	private final int STATE_CONNECTING= 2;
	private final int STATE_CONNECTED = 3;
	private int mSocketConnectState = STATE_DISCONNECTED;
	
	private static final int MSG_TIME_SEND = 1;
	private static final int MSG_SOCKET_CONNECT = 2;
	private static final int MSG_SOCKET_DISCONNECT = 3;
	private static final int MSG_SOCKET_CONNECTFAIL = 4;
	private static final int MSG_RECEIVE_DATA = 5;
	private static final int MSG_SEND_DATA = 6;
	private Handler mHandler = new Handler(){
		public void handleMessage(Message msg) {
			switch(msg.what){
			case MSG_TIME_SEND:
				break;
			case MSG_SOCKET_CONNECT:
				mSocketConnectState = STATE_CONNECTED;
				mClientState.setText(R.string.state_connected);
				mBtnConnect.setText(R.string.disconnect);
				mReceiveThread = new SocketReceiveThread();
				mReceiveThread.start();
				break;
			case MSG_SOCKET_DISCONNECT:
				mClientState.setText(R.string.state_disconected);
				mBtnConnect.setText(R.string.connect);
				mSocketConnectState = STATE_DISCONNECTED;
				closeConnection();
				break;
			case MSG_SOCKET_CONNECTFAIL:
				mSocketConnectState = STATE_DISCONNECTED;
				mBtnConnect.setText(R.string.connect);
				mClientState.setText(R.string.state_connect_fail);
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
		setContentView(R.layout.activity_client);
		mBtnSet = (Button)findViewById(R.id.bt_client_set);
		mBtnConnect = (Button)findViewById(R.id.bt_client_connect);
		mBtnSend = (Button)findViewById(R.id.bt_client_send);
		mEditMsg = (EditText)findViewById(R.id.client_sendMsg);
		mClientState = (TextView) findViewById(R.id.client_state);
		mTvReceive = (TextView) findViewById(R.id.client_receive);
		mBtnSet.setOnClickListener(this);
		mBtnConnect.setOnClickListener(this);
		mBtnSend.setOnClickListener(this);
		mSharedPreferences = getSharedPreferences("setting", Context.MODE_PRIVATE);
		//获取保存的ip地址、客户端端口号
		mIpAddress = mSharedPreferences.getString(IP_ADDRESS, null);
		mClientPort = mSharedPreferences.getInt(CLIENT_PORT, DEFAULT_PORT);
		initHandlerThraed();
	}

	@Override
	protected void onResume() {
		super.onResume();
		if(mSocketConnectState == STATE_CONNECTED){
			mBtnConnect.setText(R.string.disconnect);
			mClientState.setText(R.string.state_connected);
		}else if(mSocketConnectState == STATE_DISCONNECTED){
			mBtnConnect.setText(R.string.connect);
			mClientState.setText(R.string.state_disconected);
		}
		else if(mSocketConnectState == STATE_CONNECTING){
			mClientState.setText(R.string.state_connecting);
			mClientState.setText(R.string.state_connected);
		}
	}
	@Override
	protected void onDestroy() {
		super.onDestroy();
		//退出HandlerThread的Looper循环。
        mHandlerThread.quit();
		closeConnection();
	}

	@Override
	public void onClick(View v) {
		int id = v.getId();

		if (id == R.id.bt_client_set) {
			set();
		} else if (id == R.id.bt_client_connect) {
			if (mSocketConnectState == STATE_CONNECTED) {
				closeConnection();
			} else {
				startConnect();
			}
		} else if (id == R.id.bt_client_send) {
			sendTxt();
		}
	}
	
	private void set(){
		View setview = LayoutInflater.from(this).inflate(R.layout.dialog_clientset, null);
		final EditText ipAddress = (EditText) setview.findViewById(R.id.edtt_ipaddress);
		final EditText editport = (EditText)setview.findViewById(R.id.client_port);
		Button ensureBtn = (Button)setview.findViewById(R.id.client_ok);
		
		ipAddress.setText(mSharedPreferences.getString(IP_ADDRESS, null));
		editport.setText(mSharedPreferences.getInt(CLIENT_PORT, 8086) + "");
		
		final AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setView(setview); //设置dialog显示一个view
		final AlertDialog dialog = builder.show(); //dialog显示
		ensureBtn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				String port = editport.getText().toString();
				mIpAddress = ipAddress.getText().toString();
				if(port != null && port.length() >0){
					mClientPort = Integer.parseInt(port);
				}
				SharedPreferences.Editor editor=mSharedPreferences.edit();
				editor.putString(IP_ADDRESS, mIpAddress);
				editor.putInt(CLIENT_PORT, mClientPort);
				editor.commit();
				dialog.dismiss(); //dialog消失
			}
		});
		
	}
	private void startConnect() {
		Log.i(TAG,"startConnect");
		if(mIpAddress == null || mIpAddress.length() == 0){
			Toast.makeText(this, "请设置ip地址", Toast.LENGTH_LONG).show();
			return;
		}
		if(mSocketConnectState != STATE_DISCONNECTED) return;
		mConnectThread = new SocketConnectThread();
		mConnectThread.start();
		mSocketConnectState = STATE_CONNECTING;
		mClientState.setText(R.string.state_connecting);
	}
	
	private void sendTxt(){
		if(mSocket == null){
			Toast.makeText(this, "没有连接", Toast.LENGTH_SHORT).show();
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
		Log.i(TAG, "writeMsg msg="+msg);
		if(msg.length() == 0 || mOutStream == null)
			return;
		try {
			mOutStream.write(msg.getBytes());//发送
			mOutStream.flush();
		}catch (Exception e) {
			e.printStackTrace();
		}
		
	}
	
	public void closeConnection(){
		try {
			if (mOutStream != null) {
            	mOutStream.close(); //关闭输出流
            	mOutStream = null;
			}
			if (mInStream != null) {
            	mInStream.close(); //关闭输入流
            	mInStream = null;
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
		mSocketConnectState = STATE_DISCONNECTED;
		mBtnConnect.setText(R.string.connect);
		mClientState.setText(R.string.state_disconected);
    }
	
	class SocketConnectThread extends Thread{
		public void run(){
			try {
				//连接服务端，指定服务端ip地址和端口号。
				mSocket = new Socket(mIpAddress,mClientPort);
				if(mSocket != null){
					//获取输出流、输入流
					mOutStream = mSocket.getOutputStream();
					mInStream = mSocket.getInputStream();
				}
			} catch (Exception e) {
				e.printStackTrace();
				mHandler.sendEmptyMessage(MSG_SOCKET_CONNECTFAIL);
				return;
			}
			Log.i(TAG,"connect success");
			mHandler.sendEmptyMessage(MSG_SOCKET_CONNECT);
		}
		
	}
	class SocketReceiveThread extends Thread{
		private boolean threadExit;
		public SocketReceiveThread() {
			threadExit = false;
		}
		public void run(){
			byte[] buffer = new byte[1024];
			while(threadExit == false){
	        	try {
	        		//读取数据，返回值表示读到的数据长度。-1表示结束
	        		int count = mInStream.read(buffer);
		            if( count == -1){
		            	Log.i(TAG, "read read -1");
		            	mHandler.sendEmptyMessage(MSG_SOCKET_DISCONNECT);
		            	break;
		            }else{
		            	String receiveData = new String(buffer, 0, count);
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
