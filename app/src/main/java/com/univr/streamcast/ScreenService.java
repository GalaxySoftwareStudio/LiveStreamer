package com.univr.streamcast;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Canvas;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.opengl.GLES20;
import android.os.Build;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Surface;
import android.view.WindowManager;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.univr.streamcast.gles.EglCore;
import com.univr.streamcast.gles.WindowSurface;

import java.io.IOException;

public class ScreenService extends Service implements EncoderThread.EncoderListener {
    public static final int STATE_STARTED = 0x00;
    public static final int STATE_STOPPED = 0x01;
    private static final String DISPLAY_NAME = "stream_display";
    private static final int ONGOING_NOTIFICATION_ID = 23;
    private static final int DFRAME_RATE = 30;
    private static final int RTSP_PORT = 8554;
    private static final int BIT_RATE = 5 * 1024;
    private static final int I_FRAME = 2;
    private static final int VIDEO_WIDTH = 3664;
    private static final int VIDEO_HEIGHT = 1920;
    private EglCore eglCore;
    private WindowSurface inputSurface;
    private MediaProjection projection;
    private VirtualDisplay display;
    private MediaCodec encoder;
    private Live555Native liveNative;
    private int dpi;

    @Override
    public void onCreate() {
        String channelID = "com.univr.streamcast";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel notificationChannel = new NotificationChannel(channelID, "RTSP Service", NotificationManager.IMPORTANCE_NONE);
            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            assert notificationManager != null;
            notificationManager.createNotificationChannel(notificationChannel);
        }

        Intent notificationIntent = new Intent(this, ScreenService.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
        Notification.Builder notificationBuilder = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationBuilder = new Notification.Builder(this, channelID);
        } else {
            notificationBuilder = new Notification.Builder(this);
        }

        Notification notification = notificationBuilder
                .setContentTitle("RTSP Server")
                .setContentText("Streaming is running...")
                .setContentIntent(pendingIntent)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setPriority(Notification.PRIORITY_MIN)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(ONGOING_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(ONGOING_NOTIFICATION_ID, notification);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        WindowManager wm = (WindowManager) this.getSystemService(WINDOW_SERVICE);
        wm.getDefaultDisplay().getRealMetrics(displayMetrics);
        dpi = displayMetrics.densityDpi;

        EGL14

        if (null != intent) {
            if (intent.getAction() != null && intent.getAction().equals("stop")) {
                if (encoder != null) {
                    projection.stop();
                    encoder.stop();
                    encoder.release();
                    display.release();
                    liveNative.stop();
                    liveNative.destroy();

                    if (eglCore != null) {
                        eglCore.release();
                        eglCore = null;
                        inputSurface.release();
                        inputSurface = null;
                    }

                    stopService(new Intent(this, ScreenService.class));
                    notifyStatusChange(STATE_STOPPED);
                }
            } else {
                try {
                    liveNative = new Live555Native();
                    if (liveNative.initialize(DFRAME_RATE, RTSP_PORT)) {
                        liveNative.doLoop();
                        createRecordSession(intent.getIntExtra("resultCode", 0),
                                intent.getParcelableExtra("data"));
                        notifyStatusChange(STATE_STARTED);
                    } else {
                        Log.e("ScreenService", "create transport error!");
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                    Log.e("ScreenService", "create transport error!");
                }
            }
        }
        return START_STICKY;
    }

    private void notifyStatusChange(int status) {
        Intent mIntent = new Intent();
        mIntent.putExtra("status", status);
        LocalBroadcastManager.getInstance(this).sendBroadcast(mIntent);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void createRecordSession(int resultCode, Intent data) throws IOException {
        MediaFormat mMediaFormat = MediaFormat.createVideoFormat("video/avc", VIDEO_WIDTH, VIDEO_HEIGHT);

        mMediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE * 1024);
        mMediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, DFRAME_RATE);

        mMediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        mMediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME);

        encoder = MediaCodec.createEncoderByType("video/avc");

        encoder.configure(mMediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

        Surface surface = encoder.createInputSurface();

        Canvas canvas = surface.lockHardwareCanvas();
        canvas.clipRect(500, 500, 500, 500);

        MediaProjectionManager projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        projection = projectionManager.getMediaProjection(resultCode, data);
        display = projection.createVirtualDisplay(DISPLAY_NAME,
                VIDEO_WIDTH,
                VIDEO_HEIGHT,
                dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                surface,
                null,
                null);

        EncoderThread encoderThread = new EncoderThread(encoder);
        encoderThread.setEncoderListener(this);
        encoder.start();
        encoderThread.start();
    }

    @Override
    public void beforeEncodeFrame() {
        if (inputSurface != null) {
            GLES20.glClearColor(1, 0, 0, 1);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            int program = GLES20.glCreateProgram();
            inputSurface.setPresentationTime(1666);
            inputSurface.swapBuffers();
        }
    }

    @Override
    public void onEncodeFrame(byte[] frame) {
        liveNative.feedH264Data(frame);
    }

    @Override
    public void afterEncodeFrame() {

    }
}
