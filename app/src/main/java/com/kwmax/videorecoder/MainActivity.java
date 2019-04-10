package com.kwmax.videorecoder;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, Camera.PreviewCallback{

    private static final String TAG = MainActivity.class.getName();
    TextView beginRecode;
    TextView stopRecode;
    TextView beginPlay;
    TextView stopPlay;
    SeekBar seekBar;

    Camera camera;
    SurfaceView recordSurface;
    SurfaceHolder recordHolder;
    SurfaceView playSurface;
    SurfaceHolder playdHolder;

    private MediaPlayer mediaPlayer;
    private int currentPosition = 0;
    private boolean isPlaying;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //申请权限
        checkPermission();
        initView();

    }

    private void checkPermission(){
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "申请权限", Toast.LENGTH_SHORT).show();
            // 申请 相机 麦克风权限
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE}, 100);

        }
    }

    private void initView(){
        recordSurface = (SurfaceView) findViewById(R.id.record_surface);
        playSurface = (SurfaceView) findViewById(R.id.play_surface);
        beginRecode = (TextView) findViewById(R.id.begin_recode);
        stopRecode = (TextView) findViewById(R.id.stop_recode);
        beginPlay = (TextView) findViewById(R.id.begin_play);
        stopPlay = (TextView) findViewById(R.id.stop_play);
        seekBar = (SeekBar) findViewById(R.id.seekBar);

        recordHolder = recordSurface.getHolder();
        recordHolder.addCallback(recordCallback);

        playdHolder = playSurface.getHolder();
        playdHolder.addCallback(playCallback);

        seekBar.setOnSeekBarChangeListener(change);

        beginRecode.setOnClickListener(this);
        stopRecode.setOnClickListener(this);
        beginPlay.setOnClickListener(this);
        stopPlay.setOnClickListener(this);
    }

    @Override
    public void onPreviewFrame(byte[] bytes, Camera camera) {
        MediaMuxerThread.addVideoFrameData(bytes);
    }

    //----------------------- 录制回调  -----------------------------------------

    private SurfaceHolder.Callback recordCallback = new SurfaceHolder.Callback() {
        @Override
        public void surfaceCreated(SurfaceHolder surfaceHolder) {
            recordHolder = surfaceHolder;
        }

        @Override
        public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {

        }

        @Override
        public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
            MediaMuxerThread.stopMuxer();
            stopCamera();
        }
    };

    //----------------------- 播放相关  -----------------------------------------

    private SurfaceHolder.Callback playCallback = new SurfaceHolder.Callback() {
        @Override
        public void surfaceCreated(SurfaceHolder surfaceHolder) {
            playdHolder = surfaceHolder;
            Log.i(TAG, "SurfaceHolder 被创建");
            if (currentPosition > 0) {
                // 创建SurfaceHolder的时候，如果存在上次播放的位置，则按照上次播放位置进行播放
                play(currentPosition);
                currentPosition = 0;
            }
        }

        @Override
        public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {
            Log.i(TAG, "SurfaceHolder 大小被改变");
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
            Log.i(TAG, "SurfaceHolder 被销毁");
            // 销毁SurfaceHolder的时候记录当前的播放位置并停止播放
            if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                currentPosition = mediaPlayer.getCurrentPosition();
                mediaPlayer.stop();
            }
        }
    };

    private SeekBar.OnSeekBarChangeListener change = new SeekBar.OnSeekBarChangeListener() {

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            // 当进度条停止修改的时候触发
            // 取得当前进度条的刻度
            int progress = seekBar.getProgress();
            if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                // 设置当前播放的位置
                mediaPlayer.seekTo(progress);
            }
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {

        }

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress,
                                      boolean fromUser) {

        }
    };

    /**
     * 开始播放
     *
     * @param msec 播放初始位置
     */
    protected void play(final int msec) {
        // 获取视频文件地址
//        String path = Environment.getExternalStorageDirectory().getPath() + "/android_records"
//                + "/video/" + "";
        String path = FileUtils.nextFileName;
        File file = new File(path);
        if (!file.exists()) {
            Toast.makeText(this, "视频文件路径错误", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            // 设置播放的视频源
            mediaPlayer.setDataSource(file.getAbsolutePath());
            // 设置显示视频的SurfaceHolder
            mediaPlayer.setDisplay(playSurface.getHolder());
            Log.i(TAG, "开始装载");
            mediaPlayer.prepareAsync();
            mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {

                @Override
                public void onPrepared(MediaPlayer mp) {
                    Log.i(TAG, "装载完成");
                    mediaPlayer.start();
                    // 按照初始位置播放
                    mediaPlayer.seekTo(msec);
                    // 设置进度条的最大进度为视频流的最大播放时长
                    seekBar.setMax(mediaPlayer.getDuration());
                    // 开始线程，更新进度条的刻度
                    new Thread() {

                        @Override
                        public void run() {
                            try {
                                isPlaying = true;
                                while (isPlaying) {
                                    int current = mediaPlayer
                                            .getCurrentPosition();
                                    seekBar.setProgress(current);

                                    sleep(500);
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }.start();

                    beginPlay.setEnabled(false);
                }
            });
            mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {

                @Override
                public void onCompletion(MediaPlayer mp) {
                    // 在播放完毕被回调
                    beginPlay.setEnabled(true);
                }
            });

            mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {

                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    // 发生错误重新播放
                    play(0);
                    isPlaying = false;
                    return false;
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    /**
     * 停止播放
     */
    protected void stop() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
            beginPlay.setEnabled(true);
            isPlaying = false;
        }
    }

    //----------------------- 摄像头操作相关 --------------------------------------

    /**
     * 打开摄像头
     */
    private void startCamera() {
        camera = Camera.open(Camera.CameraInfo.CAMERA_FACING_FRONT);
        camera.setDisplayOrientation(90);
        Camera.Parameters parameters = camera.getParameters();
        parameters.setPreviewFormat(ImageFormat.NV21);

        // 这个宽高的设置必须和后面编解码的设置一样，否则不能正常处理
        parameters.setPreviewSize(1920, 1080);

        try {
            camera.setParameters(parameters);
            camera.setPreviewDisplay(recordHolder);
            camera.setPreviewCallback(MainActivity.this);
            camera.startPreview();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 关闭摄像头
     */
    private void stopCamera() {
        // 停止预览并释放资源
        if (camera != null) {
            camera.setPreviewCallback(null);
            camera.stopPreview();
            camera = null;
        }
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.begin_recode){
            playSurface.setVisibility(View.GONE);
            recordSurface.setVisibility(View.VISIBLE);
            startCamera();
            Toast.makeText(MainActivity.this,"开始录制",Toast.LENGTH_SHORT).show();
            MediaMuxerThread.startMuxer();
        }else if (id == R.id.stop_recode){
            playSurface.setVisibility(View.GONE);
            recordSurface.setVisibility(View.VISIBLE);
            MediaMuxerThread.stopMuxer();
            stopCamera();
            Toast.makeText(MainActivity.this,"结束录制",Toast.LENGTH_SHORT).show();
        }else if (id == R.id.begin_play){
            playSurface.setVisibility(View.VISIBLE);
            recordSurface.setVisibility(View.GONE);
            Toast.makeText(MainActivity.this,"开始播放",Toast.LENGTH_SHORT).show();
            play(0);
        }else if (id == R.id.stop_play){
            playSurface.setVisibility(View.VISIBLE);
            recordSurface.setVisibility(View.GONE);

            Toast.makeText(MainActivity.this,"停止播放",Toast.LENGTH_SHORT).show();
            stop();
        }
    }
}
