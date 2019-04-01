package com.example.dependentwatchapp;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.wearable.activity.WearableActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.gms.wearable.CapabilityClient;
import com.google.android.gms.wearable.CapabilityInfo;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;

import java.util.Set;
import java.util.concurrent.ExecutionException;

public class WatchActivity extends WearableActivity{

    private TextView mTextView;
    private Button btnRefresh;
    private Button btnSend;
    private final static String LOG_TAG_WEAR = "Wear端事件";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_watch);

        mTextView = (TextView) findViewById(R.id.text);
        btnRefresh = (Button) findViewById(R.id.refresh_target);
        btnRefresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.w(LOG_TAG_WEAR,"Refresh 按钮点击");
                try {
                    setupVoiceTranscription();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        btnSend = (Button) findViewById(R.id.send_msg);
        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                byte[] data = new byte[]{1,2,3};
                requestTranscription(data);
                Log.w(LOG_TAG_WEAR,"Send 按钮点击");
            }
        });
        // Enables Always-on
        setAmbientEnabled();
    }

    private static final String VOICE_TRANSCRIPTION_CAPABILITY_NAME = "voice_transcription";
    private void setupVoiceTranscription() throws ExecutionException, InterruptedException {
        CapabilityInfo capabilityInfo = Tasks.await(
                Wearable.getCapabilityClient(getApplicationContext()).getCapability(
                        VOICE_TRANSCRIPTION_CAPABILITY_NAME, CapabilityClient.FILTER_REACHABLE));
        // capabilityInfo has the reachable nodes with the transcription capability
        updateTranscriptionCapability(capabilityInfo);
    }

    private String transcriptionNodeId = null;
    private void updateTranscriptionCapability(CapabilityInfo capabilityInfo) {
        Set<Node> connectedNodes = capabilityInfo.getNodes();
        transcriptionNodeId = pickBestNodeId(connectedNodes);
    }
    private String pickBestNodeId(Set<Node> nodes) {
        String bestNodeId = null;
        // Find a nearby node or pick one arbitrarily
        for (Node node : nodes) {
            if (node.isNearby()) {
                return node.getId();
            }
            bestNodeId = node.getId();
        }
        return bestNodeId;
    }

    public static final String VOICE_TRANSCRIPTION_MESSAGE_PATH = "/voice_transcription";
    private void requestTranscription(byte[] voiceData) {
        if (transcriptionNodeId != null) {
            Task<Integer> sendTask =
                    Wearable.getMessageClient(getApplicationContext()).sendMessage(
                            transcriptionNodeId, VOICE_TRANSCRIPTION_MESSAGE_PATH, voiceData);
            // You can add success and/or failure listeners,
            // Or you can call Tasks.await() and catch ExecutionException
            sendTask.addOnSuccessListener(new OnSuccessListener<Integer>() {
                @Override
                public void onSuccess(Integer integer) {
                    Toast.makeText(getApplicationContext(), "消息发送成功", Toast.LENGTH_SHORT).show();
                }
            });
            sendTask.addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    Toast.makeText(getApplicationContext(), "消息发送失败", Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            // Unable to retrieve node with transcription capability
            Toast.makeText(getApplicationContext(), "未连接有效节点", Toast.LENGTH_SHORT).show();
        }
    }
}
