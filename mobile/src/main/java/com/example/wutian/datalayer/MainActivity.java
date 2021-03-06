package com.example.wutian.datalayer;

import android.media.AudioManager;
import android.support.v7.app.AppCompatActivity;
import android.content.BroadcastReceiver;
import android.util.Log;
import android.widget.Button;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v4.content.LocalBroadcastManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.widget.TextView;

import com.google.android.gms.wearable.Node;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.gms.wearable.Wearable;

import java.util.List;
import java.util.concurrent.ExecutionException;

public class MainActivity extends AppCompatActivity  {

    public static final String LOG_TAG_XSW = "XSW";
    Button talkbutton;
    TextView tvLogSW;
    Button btnVolumeUp;
    Button btnVolumeDown;
    Button clearText;
    Button sendCommand;
    protected Handler myHandler;
    int receivedMessageNumber = 1;
    private Button btnAudio;
    private TextView tvAudio;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        talkbutton = findViewById(R.id.talkButton);
        tvLogSW = findViewById(R.id.tv_log_from_smartwatch);
        btnVolumeUp = findViewById(R.id.btn_volume_up);
        btnVolumeDown = findViewById(R.id.btn_volume_down);
        clearText = findViewById(R.id.clearText);
        sendCommand = findViewById(R.id.sendCommand);
        btnAudio = findViewById(R.id.btn_audio);
        tvAudio = findViewById(R.id.tv_audio);
        btnVolumeUp.setOnClickListener(v -> adjustVolume(true));
        btnVolumeDown.setOnClickListener(v -> adjustVolume(false));
        clearText.setOnClickListener(v -> tvLogSW.setText(""));

        /*btnAudio.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                recognizeAudioWithPermissionRequest();
            }
        });*/
        //Create a message handler//

        myHandler = new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {
                Bundle stuff = msg.getData();
                messageText(stuff.getString("messageText"));
                return true;
            }
        });

//Register to receive local broadcasts, which we'll be creating in the next step//

        IntentFilter messageFilter = new IntentFilter(Intent.ACTION_SEND);
        Receiver messageReceiver = new Receiver();
        LocalBroadcastManager.getInstance(this).registerReceiver(messageReceiver, messageFilter);

        sendCommand.setOnClickListener(v -> {
            String message = "tv#chan#-1";
            new NewThread("/my_path", message).start();
        });
    }


    private void adjustVolume(boolean up) {
        AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        int curVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        Log.i(LOG_TAG_XSW, "curVolume" + curVolume);
        if(up)
            curVolume++;
        else
            curVolume--;
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, curVolume, AudioManager.FLAG_PLAY_SOUND);
    }

    @Override
    protected void onResume() {
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        super.onResume();
    }

    public void messageText(String newinfo) {
        if (newinfo.compareTo("") != 0) {
            tvLogSW.append("\n" + newinfo);
        }
    }

//Define a nested class that extends BroadcastReceiver//

    public class Receiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {

//Upon receiving each message from the wearable, display the following text//

            String message = intent.getStringExtra("message");
            Log.i(LOG_TAG_XSW, "onReceive message:" + message);
            tvLogSW.append("\n" + message);
            new NewThread("/my_path", message).start();
        }
    }

    public void talkClick(View v) {
        String message = "Sending message.... ";
        tvLogSW.setText(message);

//Sending a message can block the main UI thread, so use a new thread//

        new NewThread("/my_path", message).start();
    }

//Use a Bundle to encapsulate our message//

    public void sendmessage(String messageText) {
        Bundle bundle = new Bundle();
        bundle.putString("messageText", messageText);
        Message msg = myHandler.obtainMessage();
        msg.setData(bundle);
        myHandler.sendMessage(msg);
    }

    class NewThread extends Thread {
        String path;
        String message;

//Constructor for sending information to the Data Layer//

        NewThread(String p, String m) {
            path = p;
            message = m;
        }

        public void run() {

//Retrieve the connected devices, known as nodes//

            Task<List<Node>> wearableList = Wearable.getNodeClient(getApplicationContext()).getConnectedNodes();
            try {
                List<Node> nodes = Tasks.await(wearableList);
                for (Node node : nodes) {

//Send the message//

                    Task<Integer> sendMessageTask =
                            Wearable.getMessageClient(MainActivity.this).sendMessage(node.getId(), path, message.getBytes());
                    try {

//Block on a task and get the result synchronously//

                        Integer result = Tasks.await(sendMessageTask);
                        String devType = message.split("#")[0];
                        sendmessage("Just sent the message to devType:" + devType);
                    } catch (ExecutionException exception) {

                    } catch (InterruptedException exception) {

                    }
                }

            } catch (ExecutionException exception) {

            } catch (InterruptedException exception) {

            }
        }
    }
}