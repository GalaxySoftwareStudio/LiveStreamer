package com.univr.streamcast;

import android.media.MediaCodec;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import androidx.annotation.NonNull;

import java.nio.ByteBuffer;

public class EncoderThread extends Thread {
    private static final int MSG_START_RECORDING = 41;
    private static final int MSG_STOP_RECORDING = 450;
    private static final int MSG_FRAME_AVAILABLE = 464;

    private static final int TIMEOUT = 60 * 1000;
    private MediaCodec mediaCodec = null;
    private EncoderListener mListener = null;
    public EncoderThread(MediaCodec codec) {
        mediaCodec = codec;
    }

    public void setEncoderListener(EncoderListener listener) {
        this.mListener = listener;
    }

    @Override
    public void run() {
        drainFrame();
    }

    public void drainFrame() {
        boolean finished = false;
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        while (!finished) {
            mListener.beforeEncodeFrame();
            int inputBufferId = mediaCodec.dequeueOutputBuffer(bufferInfo, TIMEOUT);

            if (inputBufferId == MediaCodec.INFO_TRY_AGAIN_LATER) {
                Log.d("EncoderThread", "INFO TRY AGAIN LATER");
            } else if (inputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                Log.d("EncoderThread", "INFO OUTPUT FORMAT CHANGED");
            } else {
                ByteBuffer outputBuffer = mediaCodec.getOutputBuffer(inputBufferId);

                outputBuffer.position(bufferInfo.offset);
                outputBuffer.limit(bufferInfo.offset + bufferInfo.size);

                byte[] srcBuffer = new byte[bufferInfo.size];
                outputBuffer.get(srcBuffer, 0, bufferInfo.size);
                mListener.onEncodeFrame(srcBuffer);

                finished = (bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                mediaCodec.releaseOutputBuffer(inputBufferId, false);
            }
        }
    }

    private static class EncoderHandler extends Handler {
        @Override
        public void handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                
            }
        }
    }

    public interface EncoderListener {
        void beforeEncodeFrame();
        void onEncodeFrame(byte[] frame);
        void afterEncodeFrame();
    }
}
