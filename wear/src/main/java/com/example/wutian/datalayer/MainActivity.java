package com.example.wutian.datalayer;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.SpeechRecognizer;
import android.support.annotation.NonNull;
import android.util.Log;
import android.widget.Button;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v4.content.LocalBroadcastManager;
import android.support.wearable.activity.WearableActivity;
import android.widget.TextView;
import android.view.View;
import android.widget.Toast;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.Node;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class MainActivity extends WearableActivity {

    private TextView textView;
    private SensorManager gravityManager;
    private Sensor gravitySensor;
    private SensorEventListener gravityEventListener;
    private SensorManager acceleratorManager;
    private Sensor acceleratorSensor;
    private SensorEventListener acceleratorEventListener;
    Button talkButton;
    int receivedMessageNumber = 1;
    int sentMessageNumber = 1;
    private Button btnAudio;
    private TextView tvAudio;
    public static final int MY_PERMISSIONS_REQUEST_RECORD_AUDIO = 0x00000010;
    long duration = 0;
    int count = 1;
    long prev = 0, curr = 0;
    float maxAccZ = 0;
    ArrayList<Float> acc;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        textView =  findViewById(R.id.text);
        talkButton =  findViewById(R.id.talkClick);
        btnAudio = findViewById(R.id.btn_audio);
        tvAudio = findViewById(R.id.tv_audio);
        Integer[] dasf = null;
        btnAudio.setOnClickListener(v -> recognizeAudioWithPermissionRequest());

// Gravity Sensor Configuration
        gravityManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        gravitySensor = gravityManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
        gravityEventListener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                float gravity_y = event.values[1];

                if(gravity_y < -2.0f){
                    new SendMessage("/my_path", "Volume up " + Integer.toString((int)gravity_y)).start();
                    textView.setText("Volume up " + Integer.toString((int)gravity_y));
                }
                else if(gravity_y > 2.0f){
                    new SendMessage("/my_path", "Volume down " + Integer.toString((int)gravity_y)).start();
                    textView.setText("Volume down " + Integer.toString((int)gravity_y));
                }
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {

            }
        };

// Accelerator Sensor Configuration
        acceleratorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        acceleratorSensor = acceleratorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        acceleratorEventListener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                float accZ = event.values[2];

                if (Math.abs(accZ) < 0.1f) {
                    if (duration > 1250l) {
                        int judge = 0;
                        for (int i = 1; i <= count / 3; i++) {
                            float temp = acc.get(i);
                            maxAccZ = Math.abs(temp) > Math.abs(maxAccZ) ? temp : maxAccZ;
                            if (temp > 0) {
                                judge++;
                            } else {
                                judge--;
                            }
                        }
                        Log.d(" judge  ", Integer.toString(judge));
                        if(judge == 0){
                            judge = maxAccZ > 0 ? 1 : -1;
                        }
                        if (judge > 0) {
                            textView.setText(" Up: " + Long.toString(duration));
                            Log.d(" Up  ", Long.toString(duration));
                        } else {
                            textView.setText("Down: " + Long.toString(duration));
                            Log.d(" Down  ", Long.toString(duration));
                        }
                    }
                    duration = 0;
                    prev = System.currentTimeMillis();
                    count = 1;
                    maxAccZ = 0;
                } else {
                    curr = System.currentTimeMillis();
                    long difference = curr - prev;
                    duration += difference;
                    prev = curr;
                    if (count == 1) {
                        acc = new ArrayList<>();
                    }
                    if (Math.abs(accZ) < 2.0f) {
                        acc.add(accZ);
                        count++;
                    }
                }
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {

            }
        };

//Create an OnClickListener//

        talkButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String onClickMessage = "I just sent the handheld a message " + sentMessageNumber++;
                textView.setText(onClickMessage);

//Use the same path//

                String datapath = "/my_path";
                new SendMessage(datapath, onClickMessage).start();
            }
        });

//Register to receive local broadcasts, which we'll be creating in the next step//

        IntentFilter newFilter = new IntentFilter(Intent.ACTION_SEND);
        Receiver messageReceiver = new Receiver();
        LocalBroadcastManager.getInstance(this).registerReceiver(messageReceiver, newFilter);
    }

    private void recognizeAudioWithPermissionRequest(){
        Log.i("XSW","recognizeAudioWithPermissionRequest");
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
            recognizeAudio();
        else{
            if(shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO))
                Toast.makeText(this, "Audio Recording is required", Toast.LENGTH_SHORT).show();
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, MY_PERMISSIONS_REQUEST_RECORD_AUDIO);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        Log.i("XSW","onRequestPermissionsResult" + "  requestCode:"+requestCode +"  grantResults:"+grantResults[0]);
        if(requestCode == MY_PERMISSIONS_REQUEST_RECORD_AUDIO){
            if(grantResults[0] == PackageManager.PERMISSION_GRANTED)
                recognizeAudio();
            else
                Toast.makeText(this, "Audio Recording Permission was not granted", Toast.LENGTH_SHORT).show();
        }else
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private void recognizeAudio() {
        Log.i("XSW","recognizeAudio");
        if(!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Recognizer Unavailable", Toast.LENGTH_SHORT).show();
            return;
        }
        SpeechRecognizer speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                // TODO: 2019/5/5
                Log.i("XSW","onReadyForSpeech");
            }

            @Override
            public void onBeginningOfSpeech() {
                Log.i("XSW","onBeginningOfSpeech");
            }

            @Override
            public void onRmsChanged(float rmsdB) {
//                Log.i("XSW","onRmsChanged");
            }

            @Override
            public void onBufferReceived(byte[] buffer) {
                Log.i("XSW","onBufferReceived");
            }

            @Override
            public void onEndOfSpeech() {
                // TODO: 2019/5/5
                Log.i("XSW","onEndOfSpeech");
            }

            @Override
            public void onError(int error) {
                Log.i("XSW","onError(recognizeAudio):" + error);
            }

            @Override
            public void onResults(Bundle results) {
                ArrayList<String> recResults = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                StringBuilder sb = new StringBuilder();
                for (String str : recResults) {
                    sb.append("\n");
                    sb.append(str);
                }
                Log.i("XSW","onResults:" + sb.toString());
                tvAudio.setText(sb.toString());
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
                Log.i("XSW","onPartialResults");
            }

            @Override
            public void onEvent(int eventType, Bundle params) {
                Log.i("XSW","onEvent");
            }
        });
        Intent recognizerIntent = new Intent();
        speechRecognizer.startListening(recognizerIntent);
    }

    public class Receiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {

//Display the following when a new message is received//

            String onMessageReceived = "I just received a message from the handheld " + receivedMessageNumber++;
            textView.setText(onMessageReceived);
        }
    }

    class SendMessage extends Thread {
        String path;
        String message;

//Constructor for sending information to the Data Layer//

        SendMessage(String p, String m) {
            path = p;
            message = m;
        }

        public void run() {

//Retrieve the connected devices//

            Task<List<Node>> nodeListTask = Wearable.getNodeClient(getApplicationContext()).getConnectedNodes();
            try {

//Block on a task and get the result synchronously//

                List<Node> nodes = Tasks.await(nodeListTask);
                for (Node node : nodes) {

//Send the message///

                    Task<Integer> sendMessageTask =
                            Wearable.getMessageClient(MainActivity.this).sendMessage(node.getId(), path, message.getBytes());
                    try {
                        Integer result = Tasks.await(sendMessageTask);
//Handle the errors//

                    } catch (ExecutionException exception) {

                    } catch (InterruptedException exception) {

                    }
                }
            } catch (ExecutionException exception) {

            } catch (InterruptedException exception) {

            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        gravityManager.unregisterListener(gravityEventListener);
        acceleratorManager.unregisterListener(acceleratorEventListener);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // register this class as a listener for the orientation and
        // gravity sensors
        gravityManager.registerListener(gravityEventListener, gravitySensor, SensorManager.SENSOR_DELAY_NORMAL);
        acceleratorManager.registerListener(acceleratorEventListener, acceleratorSensor, SensorManager.SENSOR_DELAY_NORMAL);
    }
}
