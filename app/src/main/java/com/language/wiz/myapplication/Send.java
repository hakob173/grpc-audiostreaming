package com.language.wiz.myapplication;

import android.app.Activity;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;

import androidx.appcompat.widget.AppCompatButton;

import com.audiostream.AudioStreamServiceGrpc;
import com.audiostream.Audiostream;
import com.google.protobuf.ByteString;

import java.net.DatagramSocket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

public class Send extends Activity {

    public byte[] buffer;
    public static DatagramSocket socket;
    private int port = 50005;

    AudioRecord recorder;

    ManagedChannel managedChannel;

    private int sampleRate = 8000; // 44100 for music
    private int channelConfig = AudioFormat.CHANNEL_CONFIGURATION_MONO;
    private int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
    int minBufSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat);
    private boolean status = true;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        managedChannel = ManagedChannelBuilder
                .forAddress("87.241.143.91", 19090)
                .usePlaintext()
                .build();
        AppCompatButton startButton = (AppCompatButton) findViewById(R.id.start_button);
        AppCompatButton stopButton = (AppCompatButton) findViewById(R.id.stop_button);

        startButton.setOnClickListener(startListener);
        stopButton.setOnClickListener(stopListener);

    }

    private final OnClickListener stopListener = new OnClickListener() {

        @Override
        public void onClick(View arg0) {
            status = false;
            recorder.release();
            Log.d("VS", "Recorder released");
        }

    };

    private final OnClickListener startListener = new OnClickListener() {

        @Override
        public void onClick(View arg0) {
            status = true;
            startStreaming();
        }

    };

    public void startStreaming() {
        recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelConfig, audioFormat, minBufSize * 10);
        recorder.startRecording();

        AtomicReference<StreamObserver<Audiostream.RawDataRequest>> requestObserverRef = new AtomicReference<>();
        CountDownLatch finishedLatch = new CountDownLatch(1);
        byte[] buffer = new byte[4096];
        StreamObserver<Audiostream.RawDataRequest> observer = AudioStreamServiceGrpc
                .newStub(managedChannel)
                .send(new StreamObserver<Audiostream.RawDataResponse>() {
            @Override
            public void onNext(Audiostream.RawDataResponse value) {
                System.out.println("onNext from client");
                try {
                    Thread.sleep(10L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                minBufSize = recorder.read(buffer, 0, buffer.length);
                requestObserverRef.get().onNext(
                        Audiostream.RawDataRequest
                                .newBuilder()
                                .setSample(ByteString.copyFrom(buffer))
                                .build());
            }

            @Override
            public void onError(Throwable t) {
                System.out.println("on error");
                t.printStackTrace();
            }

            @Override
            public void onCompleted() {
                System.out.println("on completed");
                finishedLatch.countDown();
            }
        });
        requestObserverRef.set(observer);
        observer.onNext(Audiostream.RawDataRequest.getDefaultInstance());
        try {
            finishedLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        observer.onCompleted();
    }
}