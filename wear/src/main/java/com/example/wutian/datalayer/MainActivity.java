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
import android.view.WindowManager;
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
    private SensorManager acceleratorManager;
    private Sensor acceleratorSensor;
    private SensorEventListener acceleratorEventListener;
    private SensorManager gyroscopeManager;
    private Sensor gyroscopeSensor;
    private SensorEventListener gyroscopeEventListener;
    Button talkButton;
    int receivedMessageNumber = 1;
    int sentMessageNumber = 1;
    private Button btnAudio;
    private TextView tvAudio;
    public static final int MY_PERMISSIONS_REQUEST_RECORD_AUDIO = 0x00000010;
    long prevAcc = 0, currAcc = 0;
    int stateGyro = 0;
    int stateAcc = 0;
    int count = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        textView =  findViewById(R.id.tv_log);
        talkButton =  findViewById(R.id.talkClick);
        btnAudio = findViewById(R.id.btn_audio);
        tvAudio = findViewById(R.id.tv_audio);
        Integer[] dasf = null;
        instantiateObjects();
        btnAudio.setOnClickListener(v -> recognizeAudioWithPermissionRequest());

// Accelerator Sensor Configuration
        acceleratorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        acceleratorSensor = acceleratorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        acceleratorEventListener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                float accZ = event.values[2];
                //Log.d("state", Integer.toString(stateAcc));

                if(curDev != DEV_CODE_DEFAULT ){
                    if(stateAcc == 0){
                        if(accZ < 1.0f && accZ > 0.2f){
                            prevAcc = System.currentTimeMillis();
                            stateAcc = 1;
                        }
                        else if(accZ > -1.0f && accZ < -0.2f){
                            prevAcc = System.currentTimeMillis();
                            stateAcc = -1;
                        }
                    }
                    else if(stateAcc == 1){
                        if(accZ < 0 && accZ > -1.0f){
                            stateAcc = 2;
                        }
                        currAcc = System.currentTimeMillis();
                        stateAcc = currAcc - prevAcc > 1600l ? 0 : stateAcc;
                    }
                    else if(stateAcc == 2){
                        if(Math.abs(accZ) < 0.075f){
                            Log.d("time", Long.toString(System.currentTimeMillis() - prevAcc));
                            stateAcc = 0;
                            textView.setText("Hand Up");
                            prevAcc = System.currentTimeMillis();
                            operateSelectedDevice("up");
                        }
                    }
                    else if(stateAcc == -1){
                        if(accZ > 0 && accZ < 1.0f){
                            stateAcc = -2;
                        }
                        currAcc = System.currentTimeMillis();
                        stateAcc = currAcc - prevAcc > 1600l ? 0 : stateAcc;
                    }
                    else if(stateAcc == -2){
                        if(Math.abs(accZ) < 0.075f){
                            Log.d("time", Long.toString(System.currentTimeMillis() - prevAcc));
                            stateAcc = 0;
                            textView.setText("Hand Down");
                            prevAcc = System.currentTimeMillis();
                            operateSelectedDevice("down");
                        }
                    }
                }
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {

            }
        };

// Gyroscope Sensor Configuration
        gyroscopeManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        gyroscopeSensor = gyroscopeManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
        gyroscopeEventListener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                int gyroX = (int) event.values[0];
                int gyroY = (int) event.values[1];
                int gyroZ = (int) event.values[2];
                //Log.d(" gyro ", Integer.toString(gyroX) + "   " + Integer.toString(gyroY) + "   " + Integer.toString(gyroZ) + "  " + Integer.toString(stateGyro) + "\n");

                if(stateGyro == 0){
                    if(gyroY < -2){
                        stateGyro = 1;
                    }
                }
                else if(stateGyro == 1){
                    if(gyroY == -9){
                        recognizeAudioWithPermissionRequest();
                        stateGyro = 2;
                    }
                }
                else if(stateGyro == 2){
                    if(gyroY > -2 && gyroY < 2){
                        stateGyro = 0;
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

            //String onMessageReceived = "I just received a message from the handheld " + receivedMessageNumber++;
            String onMessageReceived = intent.getStringExtra("message");
            String[] sets = onMessageReceived.split("#");
            String display = "";
            for(int i = 0; i < sets.length; i++){
                if(i == 0){
                    display += "Target device: ";
                    display += sets[i];
                }
                else if(i == 1){
                    display += "Attribute: ";
                    display += sets[i];
                }
                else if(i == 2){
                    display += "Operation: ";
                    if(sets[i].length() > 1){
                        display += "Turn down.";
                        Log.d("evaluation",  Integer.toString(count) + "   Turn down   " + Long.toString(System.currentTimeMillis() - prevAcc));
                    }
                    else{
                        display += "Turn up.";
                        Log.d("evaluation", Integer.toString(count) + "   Turn up   " + Long.toString(System.currentTimeMillis() - prevAcc));
                    }
                }
                display += "\n";
            }
            textView.setText(display);
            count++;
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
        acceleratorManager.unregisterListener(acceleratorEventListener);
        gyroscopeManager.unregisterListener(gyroscopeEventListener);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // register this class as a listener for the orientation and
        acceleratorManager.registerListener(acceleratorEventListener, acceleratorSensor, SensorManager.SENSOR_DELAY_NORMAL);
        gyroscopeManager.registerListener(gyroscopeEventListener, gyroscopeSensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    public boolean talk2DeviceSim(String string){
        try{
            new SendMessage("/my_path", string).start();
            return true;
        }catch (Exception e) {
            return false;
        }
    }

    public class AC{

        public boolean changeTemperature(int tem) {
            if (tem == 0)
                return true;
            tem = tem > 0 ? 1 : -1;
            return talk2DeviceSim("AC" + "#" + "temp" + "#" + tem);
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
