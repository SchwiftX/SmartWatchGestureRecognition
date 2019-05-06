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
import java.util.List;
import java.util.concurrent.ExecutionException;

public class MainActivity extends WearableActivity {

    public static final String LOG_TAG_XSW = "XSW";
    public static final int DEV_CODE_DEFAULT = 0x00000000;
    public static final int DEV_CODE_AC = 0x00000001;
    public static final int DEV_CODE_TV = 0x00000002;
    public static final int DEV_CODE_SPEAKER = 0x00000003;
    private int curDev = DEV_CODE_DEFAULT;
    private AC myAC = null;
    private TV myTV = null;
    private Speaker mySpeaker = null;

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
    int state = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        textView =  findViewById(R.id.text);
        talkButton =  findViewById(R.id.talkClick);
        btnAudio = findViewById(R.id.btn_audio);
        tvAudio = findViewById(R.id.tv_audio);
        Integer[] dasf = null;
        instantiateObjects();
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
                    //textView.setText("Volume up " + Integer.toString((int)gravity_y));
                }
                else if(gravity_y > 2.0f){
                    new SendMessage("/my_path", "Volume down " + Integer.toString((int)gravity_y)).start();
                    //textView.setText("Volume down " + Integer.toString((int)gravity_y));
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
                float accY = event.values[1];

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

                if(state == 0){
                    if(accY < 2.0f && accZ > 0.4f){
                        state = 1;
                    }
                }
                else if(state < 3){
                    if(accY < 2.0f && accZ > 0){
                        state++;
                    }
                    else{
                        state = 1;
                    }
                }
                else if(state == 3){
                    state = 0;
                    recognizeAudioWithPermissionRequest();
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

    private void instantiateObjects() {
        if(myAC == null)
            myAC = new AC();
        if(myTV == null)
            myTV = new TV();
        if(mySpeaker == null)
            mySpeaker = new Speaker();
    }

    private void recognizeAudioWithPermissionRequest(){
        Log.i(LOG_TAG_XSW,"recognizeAudioWithPermissionRequest");
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
        Log.i(LOG_TAG_XSW,"onRequestPermissionsResult" + "  requestCode:"+requestCode +"  grantResults:"+grantResults[0]);
        if(requestCode == MY_PERMISSIONS_REQUEST_RECORD_AUDIO){
            if(grantResults[0] == PackageManager.PERMISSION_GRANTED)
                recognizeAudio();
            else
                Toast.makeText(this, "Audio Recording Permission was not granted", Toast.LENGTH_SHORT).show();
        }else
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private void recognizeAudio() {
        Log.i(LOG_TAG_XSW,"recognizeAudio");
        if(!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Recognizer Unavailable", Toast.LENGTH_SHORT).show();
            return;
        }
        SpeechRecognizer speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                // TODO: 2019/5/5
                Log.i(LOG_TAG_XSW,"onReadyForSpeech");
            }

            @Override
            public void onBeginningOfSpeech() {
                Log.i(LOG_TAG_XSW,"onBeginningOfSpeech");
            }

            @Override
            public void onRmsChanged(float rmsdB) {
//                Log.i(LOG_TAG_XSW,"onRmsChanged");
            }

            @Override
            public void onBufferReceived(byte[] buffer) {
                Log.i(LOG_TAG_XSW,"onBufferReceived");
            }

            @Override
            public void onEndOfSpeech() {
                // TODO: 2019/5/5
                Log.i(LOG_TAG_XSW,"onEndOfSpeech");
            }

            @Override
            public void onError(int error) {
                Log.i(LOG_TAG_XSW,"onError(recognizeAudio):" + error);
            }

            @Override
            public void onResults(Bundle results) {
                ArrayList<String> recResults = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                StringBuilder sb = new StringBuilder();
                for (String str : recResults) {
                    sb.append("\n");
                    sb.append(str);
                }
                Log.i(LOG_TAG_XSW,"onResults:" + sb.toString());
                tvAudio.setText(sb.toString());
                processAudioResult(recResults.get(0));
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
                Log.i(LOG_TAG_XSW,"onPartialResults");
            }

            @Override
            public void onEvent(int eventType, Bundle params) {
                Log.i(LOG_TAG_XSW,"onEvent");
            }
        });
        Intent recognizerIntent = new Intent();
        speechRecognizer.startListening(recognizerIntent);
    }

    private void processAudioResult(String str) {
        str = str.toLowerCase();
        if(str.contains("ac") || str.contains("air conditioner"))
            curDev = DEV_CODE_AC;
        else if(str.contains("tv") || str.contains("television"))
            curDev = DEV_CODE_TV;
        else if(str.contains("speaker") || str.contains("music"))
            curDev = DEV_CODE_SPEAKER;
        operateSelectedDevice(str);
    }

    private void operateSelectedDevice(String str) {
        if(curDev == DEV_CODE_DEFAULT){
            Toast.makeText(this, "Target Device Unspecified", Toast.LENGTH_SHORT).show();
            return;
        }
        if(str.contains("up")){
            switch (curDev){
                case DEV_CODE_AC:
                    myAC.changeTemperature(1);
                    break;
                case DEV_CODE_TV:
                    myTV.changeVolume(1);
                    break;
                case DEV_CODE_SPEAKER:
                    mySpeaker.changeVolume(1);
                    break;
            }
        }else if(str.contains("down")){
            switch (curDev){
                case DEV_CODE_AC:
                    myAC.changeTemperature(-1);
                    break;
                case DEV_CODE_TV:
                    myTV.changeVolume(-1);
                    break;
                case DEV_CODE_SPEAKER:
                    mySpeaker.changeVolume(-1);
                    break;
            }
        }else if(str.contains("next")){
            switch (curDev){
                case DEV_CODE_TV:
                    myTV.changeChannel(1);
                    break;
                case DEV_CODE_SPEAKER:
                    mySpeaker.changeChannel(1);
                    break;
                default:
                    Toast.makeText(this, "Operation unsupported with current device", Toast.LENGTH_SHORT).show();
                    break;
            }
        }else if(str.contains("previous") || str.contains("last")){
            switch (curDev){
                case DEV_CODE_TV:
                    myTV.changeChannel(-1);
                    break;
                case DEV_CODE_SPEAKER:
                    mySpeaker.changeChannel(-1);
                    break;
                default:
                    Toast.makeText(this, "Operation unsupported with current device", Toast.LENGTH_SHORT).show();
                    break;
            }
        }
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

    public boolean talk2DeviceSim(String string){
        // TODO: 2019/5/5 @wutian simulation for interaction with other devices
        return false; // succ or fail
    }

    public class AC{

        public boolean changeTemperature(int tem) {
            if (tem == 0)
                return true;
            tem = tem > 0 ? 1 : -1;
            return talk2DeviceSim("AC" + "#" + tem);
        }
    }
    public class TV{

        public boolean changeVolume(int vol) {
            if (vol == 0)
                return true;
            vol = vol > 0 ? 1 : -1;
            return talk2DeviceSim("TV" + "#" + "vol" + "#" + vol);
        }

        public boolean changeChannel(int chan) {
            if (chan == 0)
                return true;
            chan = chan > 0 ? 1 : -1;
            return talk2DeviceSim("TV" + "#" + "chan" + "#" + chan);
        }
    }
    public class Speaker {

        public boolean changeVolume(int vol) {
            if (vol == 0)
                return true;
            vol = vol > 0 ? 1 : -1;
            return talk2DeviceSim("SP" + "#" + "vol" + "#" + vol);
        }

        public boolean changeChannel(int chan) {
            if (chan == 0)
                return true;
            chan = chan > 0 ? 1 : -1;
            return talk2DeviceSim("SP" + "#" + "chan" + "#" + chan);
        }
    }
}
