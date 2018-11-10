package com.xuhao.didi.oksocket;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SwitchCompat;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.xuhao.didi.core.iocore.interfaces.ISendable;
import com.xuhao.didi.core.pojo.OriginalData;
import com.xuhao.didi.core.iocore.interfaces.IPulseSendable;
import com.xuhao.didi.oksocket.adapter.LogAdapter;
import com.xuhao.didi.oksocket.data.DefaultSendBean;
import com.xuhao.didi.oksocket.data.HandShake;
import com.xuhao.didi.oksocket.data.LogBean;
import com.xuhao.didi.oksocket.data.PulseBean;
import com.xuhao.didi.socket.client.sdk.OkSocket;
import com.xuhao.didi.socket.client.sdk.client.ConnectionInfo;
import com.xuhao.didi.socket.client.sdk.client.OkSocketOptions;
import com.xuhao.didi.socket.client.sdk.client.action.SocketActionAdapter;
import com.xuhao.didi.socket.client.sdk.client.connection.IConnectionManager;
import com.xuhao.didi.socket.client.sdk.client.connection.NoneReconnect;

import java.nio.charset.Charset;
import java.util.Arrays;


public class ComplexDemoActivity extends AppCompatActivity {

    private ConnectionInfo mInfo;

    private Button mConnect;
    private Button mDisConnect;
    private IConnectionManager mManager;
    private EditText mIPET;
    private EditText mPortET;
    private Button mRedirect;
    private EditText mFrequencyET;
    private Button mSetFrequency;
    private Button mMenualPulse;
    private Button mClearLog;
    private SwitchCompat mReconnectSwitch;

    private RecyclerView mSendList;
    private RecyclerView mReceList;

    private LogAdapter mSendLogAdapter = new LogAdapter();
    private LogAdapter mReceLogAdapter = new LogAdapter();

    private SocketActionAdapter adapter = new SocketActionAdapter() {

        @Override
        public void onSocketConnectionSuccess(ConnectionInfo info, String action) {
            logRece("连接成功");
            mManager.send(new HandShake());
            mConnect.setText("DisConnect");
            initSwitch();
        }

        private void initSwitch() {
            OkSocketOptions okSocketOptions = mManager.getOption();
            mReconnectSwitch.setChecked(!(okSocketOptions.getReconnectionManager() instanceof NoneReconnect));
        }

        @Override
        public void onSocketDisconnection(ConnectionInfo info, String action, Exception e) {
            if (e != null) {
                if (e instanceof RedirectException) {
                    logSend("正在重定向连接...");
                    mManager.switchConnectionInfo(((RedirectException) e).redirectInfo);
                    mManager.connect();
                } else {
                    logSend("异常断开:" + e.getMessage());
                }
            } else {
                logSend("正常断开");
            }
            mConnect.setText("Connect");
        }

        @Override
        public void onSocketConnectionFailed(ConnectionInfo info, String action, Exception e) {
            logSend("连接失败");
            mConnect.setText("Connect");
        }

        @Override
        public void onSocketReadResponse(ConnectionInfo info, String action, OriginalData data) {
            String str = new String(data.getBodyBytes(), Charset.forName("utf-8"));
            JsonObject jsonObject = new JsonParser().parse(str).getAsJsonObject();
            int cmd = jsonObject.get("cmd").getAsInt();
            if (cmd == 54) {//登陆成功
                String handshake = jsonObject.get("handshake").getAsString();
                logRece("握手成功! 握手信息:" + handshake + ". 开始心跳..");
                mManager.getPulseManager().setPulseSendable(new PulseBean()).pulse();
            } else if (cmd == 57) {//切换,重定向.(暂时无法演示,如有疑问请咨询github)
                String ip = jsonObject.get("data").getAsString().split(":")[0];
                int port = Integer.parseInt(jsonObject.get("data").getAsString().split(":")[1]);
                ConnectionInfo redirectInfo = new ConnectionInfo(ip, port);
                redirectInfo.setBackupInfo(mInfo.getBackupInfo());
                mManager.getReconnectionManager().addIgnoreException(RedirectException.class);
                mManager.disconnect(new RedirectException(redirectInfo));
            } else if (cmd == 14) {//心跳
                logRece("收到心跳,喂狗成功");
                mManager.getPulseManager().feed();
            } else {
                logRece(str);
            }
        }

        @Override
        public void onSocketWriteResponse(ConnectionInfo info, String action, ISendable data) {
            byte[] bytes = data.parse();
            bytes = Arrays.copyOfRange(bytes, 4, bytes.length);
            String str = new String(bytes, Charset.forName("utf-8"));
            JsonObject jsonObject = new JsonParser().parse(str).getAsJsonObject();
            int cmd = jsonObject.get("cmd").getAsInt();
            switch (cmd) {
                case 54: {
                    String handshake = jsonObject.get("handshake").getAsString();
                    logSend("发送握手数据:" + handshake);
                    break;
                }
                default:
                    logSend(str);
            }
        }

        @Override
        public void onPulseSend(ConnectionInfo info, IPulseSendable data) {
            byte[] bytes = data.parse();
            bytes = Arrays.copyOfRange(bytes, 4, bytes.length);
            String str = new String(bytes, Charset.forName("utf-8"));
            JsonObject jsonObject = new JsonParser().parse(str).getAsJsonObject();
            int cmd = jsonObject.get("cmd").getAsInt();
            if (cmd == 14) {
                logSend("发送心跳包");
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_complex);
        findViews();
        initData();
        setListener();
    }

    private void findViews() {
        mSendList = findViewById(R.id.send_list);
        mDisConnect = findViewById(R.id.disconnect);
        mReceList = findViewById(R.id.rece_list);
        mClearLog = findViewById(R.id.clear_log);
        mSetFrequency = findViewById(R.id.set_pulse_frequency);
        mFrequencyET = findViewById(R.id.pulse_frequency);
        mConnect = findViewById(R.id.connect);
        mIPET = findViewById(R.id.ip);
        mPortET = findViewById(R.id.port);
        mRedirect = findViewById(R.id.redirect);
        mMenualPulse = findViewById(R.id.manual_pulse);
        mReconnectSwitch = findViewById(R.id.switch_reconnect);
    }

    private void initData() {
        LinearLayoutManager manager1 = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
        mSendList.setLayoutManager(manager1);
        mSendList.setAdapter(mSendLogAdapter);

        LinearLayoutManager manager2 = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
        mReceList.setLayoutManager(manager2);
        mReceList.setAdapter(mReceLogAdapter);

        mInfo = new ConnectionInfo("104.238.184.237", 8080);
        OkSocketOptions.Builder builder = new OkSocketOptions.Builder();
        mManager = OkSocket.open(mInfo).option(builder.build());
    }

    private void setListener() {
        mManager.registerReceiver(adapter);

        mReconnectSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (mManager != null && !mManager.isConnect()) {
                    buttonView.setChecked(!isChecked);
                    return;
                }
                if (buttonView.isChecked() == isChecked) {
                    return;
                }
                if (!isChecked) {
                    mManager.option(new OkSocketOptions.Builder(mManager.getOption()).setReconnectionManager(new NoneReconnect()).build());
                } else {
                    mManager.option(new OkSocketOptions.Builder(mManager.getOption()).setReconnectionManager(OkSocketOptions.getDefault().getReconnectionManager()).build());
                }
            }
        });

        mConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mManager == null) {
                    return;
                }
                if (!mManager.isConnect()) {
                    mManager.connect();
                } else {
                    mConnect.setText("DisConnecting");
                    mManager.disconnect();
                }
            }
        });

        mDisConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mManager == null) {
                    return;
                }
                mManager.disconnect();
            }
        });

        mClearLog.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mReceLogAdapter.getDataList().clear();
                mSendLogAdapter.getDataList().clear();
                mReceLogAdapter.notifyDataSetChanged();
                mSendLogAdapter.notifyDataSetChanged();
            }
        });

        mRedirect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mManager == null) {
                    return;
                }
                String ip = mIPET.getText().toString();
                String portStr = mPortET.getText().toString();
                JsonObject jsonObject = new JsonObject();
                jsonObject.addProperty("cmd", 57);
                jsonObject.addProperty("data", ip + ":" + portStr);
                DefaultSendBean bean = new DefaultSendBean();
                bean.setContent(new Gson().toJson(jsonObject));
                mManager.send(bean);
            }
        });

        mSetFrequency.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mManager == null) {
                    return;
                }
                String frequencyStr = mFrequencyET.getText().toString();
                long frequency = 0;
                try {
                    frequency = Long.parseLong(frequencyStr);
                    OkSocketOptions okOptions = new OkSocketOptions.Builder(mManager.getOption())
                            .setPulseFrequency(frequency)
                            .build();
                    mManager.option(okOptions);
                } catch (NumberFormatException e) {
                }
            }
        });

        mMenualPulse.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mManager == null) {
                    return;
                }
                mManager.getPulseManager().trigger();
            }
        });
    }

    private void logSend(String log) {
        LogBean logBean = new LogBean(System.currentTimeMillis(), log);
        mSendLogAdapter.getDataList().add(0, logBean);
        mSendLogAdapter.notifyDataSetChanged();
    }

    private void logRece(String log) {
        LogBean logBean = new LogBean(System.currentTimeMillis(), log);
        mReceLogAdapter.getDataList().add(0, logBean);
        mReceLogAdapter.notifyDataSetChanged();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mManager != null) {
            mManager.disconnect();
            mManager.unRegisterReceiver(adapter);
        }
    }

}
