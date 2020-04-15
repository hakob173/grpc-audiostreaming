package com.language.wiz.myapplication;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;

import com.audiostream.AudioStreamServiceGrpc;
import com.audiostream.Audiostream;
import com.google.protobuf.ByteString;
import com.tbruyelle.rxpermissions2.RxPermissions;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramSocket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import io.reactivex.disposables.Disposable;

public class Send extends AppCompatActivity {

    public byte[] buffer = new byte[4096];
    public static DatagramSocket socket;
    private int port = 50005;

    AudioRecord recorder;

    ManagedChannel managedChannel;

    private int sampleRate = 22050; // 44100 for music
    private int channelConfig = AudioFormat.CHANNEL_CONFIGURATION_MONO;
    private int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
    int minBufSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat);
    private boolean status = true;
    MediaPlayer mediaPlayer;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        managedChannel = ManagedChannelBuilder
                .forAddress("192.168.0.16", 19090)
                .usePlaintext()
                .build();
        AppCompatButton startButton = (AppCompatButton) findViewById(R.id.start_button);
        AppCompatButton stopButton = (AppCompatButton) findViewById(R.id.stop_button);

        startButton.setOnClickListener(startListener);
        stopButton.setOnClickListener(stopListener);

        Disposable rxPermissions = new RxPermissions(this)
                .requestEach(Manifest.permission.INTERNET,
                        Manifest.permission.RECORD_AUDIO,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                ).subscribe(permission -> {
                    if (!permission.granted) {
                        finish();
                    }
                });

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
            startListening();
//            startStreaming();
        }

    };

    private void startListening() {

        AudioTrack audioTrack = new AudioTrack(
                AudioManager.STREAM_MUSIC,
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                buffer.length * 2,    //buffer length in bytes
                AudioTrack.MODE_STREAM);
        Audiostream.StartStreamingRequest start = Audiostream.StartStreamingRequest.newBuilder()
                .setStart(true).build();
        AudioStreamServiceGrpc
                .newStub(managedChannel)
                .receive(start, new StreamObserver<Audiostream.RawDataResponse>() {
                    @Override
                    public void onNext(Audiostream.RawDataResponse value) {

                        audioTrack.write(value.getMessage().toByteArray(), 0, buffer.length);
                        audioTrack.play();
                        Log.d("______", value.getMessage().toString());

                    }

                    @Override
                    public void onError(Throwable t) {

                        Log.e("______", "_FUCK" + t.getLocalizedMessage());
                    }

                    @Override
                    public void onCompleted() {

                        Log.e("______", "COMPLETE");
                    }
                });


    }

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