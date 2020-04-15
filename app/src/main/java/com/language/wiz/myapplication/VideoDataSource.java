package com.language.wiz.myapplication;

import android.media.MediaDataSource;
import android.util.Log;

import com.audiostream.AudioStreamServiceGrpc;
import com.audiostream.Audiostream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

public class VideoDataSource extends MediaDataSource {

    public static String VIDEO_URL = "https://devimages.apple.com.edgekey.net/streaming/examples/bipbop_4x3/gear1/fileSequence0.ts";
    private volatile byte[] videoBuffer = new byte[4096];

    private volatile  boolean isDownloading;
    ManagedChannel managedChannel = ManagedChannelBuilder
            .forAddress("192.168.0.16", 19090)
            .usePlaintext()
            .build();


    Runnable downloadVideoRunnable = new Runnable() {
        @Override
        public void run() {
            try{
                Audiostream.StartStreamingRequest start = Audiostream.StartStreamingRequest.newBuilder()
                        .setStart(true).build();
                AudioStreamServiceGrpc
                        .newStub(managedChannel)
                        .receive(start, new StreamObserver<Audiostream.RawDataResponse>() {
                            @Override
                            public void onNext(Audiostream.RawDataResponse value) {

                                Log.d("______", value.getMessage().toString());
                                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                                int read = 0;
                                videoBuffer = value.getMessage().toByteArray();
                            }

                            @Override
                            public void onError(Throwable t) {

                                Log.e("______", "_FUCK" + t.getLocalizedMessage());
                            }

                            @Override
                            public void onCompleted() {

                                Log.e("______", "COMPLETE");
                                isDownloading = false;
                            }
                        });
            }catch (Exception e){
                e.printStackTrace();
            }finally {
                isDownloading = false;
            }
        }
    };

    public VideoDataSource(){
        isDownloading = false;
    }

    public void downloadVideo(){
        if(isDownloading)
            return;
        Thread downloadThread = new Thread(downloadVideoRunnable);
        downloadThread.start();
        isDownloading = true;
    }

    @Override
    public synchronized int readAt(long position, byte[] buffer, int offset, int size) throws IOException {
            int length = videoBuffer.length;
            if (position >= length) {
                return -1; // -1 indicates EOF
            }
            if (position + size > length) {
                size -= (position + size) - length;
            }
            System.arraycopy(videoBuffer, (int)position, buffer, offset, size);
            return size;
    }

    @Override
    public synchronized long getSize() throws IOException {
            return videoBuffer.length;
    }

    @Override
    public synchronized void close() throws IOException {

    }

}