package com.alittlelost.soundaffect;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Created by seanashmore on 19/09/2017.
 */

public class SoundAffect extends View {

    private MediaService mediaService;
    private boolean isBound = false;
    private OnBindAttemptCompleteCallback onBindAttemptCompleteCallback;

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mediaService = ((MediaService.LocalBinder) service).getService();
            Toast.makeText(getContext(), "Service connected", Toast.LENGTH_SHORT).show();
            onBindAttemptCompleteCallback.onSuccess();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mediaService = null;
            Toast.makeText(getContext(), "Service disconnected", Toast.LENGTH_SHORT).show();
        }
    };

    private static final String TAG = "SoundAffect";
    private final String INDICATOR_DOT = "dot";
    private final String INDICATOR_NOTCH = "notch";

    private final int SEEK_AND_NOTCH_THICKNESS = 10;
    private final int SEEK_NOTCH_HEIGHT = 10;
    private final int TIMESTAMP_MARGIN_BOTTOM = 20;
    private final int NOTCH_TOUCH_THICKNESS = 100;
    private final int SEEK_BAR_TOUCH_THICKNESS = 100;
    private final float SEEK_NOTCH_DOT_RADIUS = 15.0f;

    private Context context;

    //Handler & thread to update current track position
    private HandlerThread currentPositionHandlerThread;
    private Handler currentPositionHandler;
    private Handler mainThreadHandler;

    //Paint to draw UI elements and debugging
    private Paint textPaint, playButtonPaint, prevButtonPaint, seekPaint, notchPaint, debugPaint;

    //Bitmaps for audio controls
    private Bitmap playButtonImage, pauseButtonImage, prevButtonImage;
    private Rect playPauseButtonRect, prevButtonRect;
    private Rect seekbarRect, seekbarTouchRect;
    private Rect notchRect, notchTouchRect, tapRect;

    private boolean isSeeking, wasPlayingBeforeSeek = false;

    //App attrs
    private int trackResourceId, positionIndicatorColor, seekBarColor, playButtonColor, prevButtonColor = -1;
    private boolean showPrevButton = false;
    private boolean showCurrentTime = true;
    private boolean showDuration = true;
    private String positionIndicatorShape = INDICATOR_NOTCH;

    private Runnable currentPositionRunnable = new Runnable() {
        @Override
        public void run() {
            updateNotchRect(getPercentageComplete());
            mainThreadHandler.post(new Runnable() {
                @Override
                public void run() {
                    invalidate();
                }
            });
            currentPositionHandler.postDelayed(this, 1000);
        }
    };

    //TODO: make normal constructor here that takes context and passes default set of attrs
    public SoundAffect(Context context) {
        super(context);
    }

    public SoundAffect(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        populateAttributes(context, attrs);
        setupPaints();
        setupUIElements();

        if (isInEditMode()) {
            return;
        }

        this.context = context;

        if (trackResourceId != -1) {
            loadResource(trackResourceId);
        }
    }

    public interface OnBindAttemptCompleteCallback {
        void onSuccess();

        void onFailure();
    }

    public void bindService(OnBindAttemptCompleteCallback callback) {
        ComponentName componentName = getContext().startService(new Intent(getContext(), MediaService.class));

        if (componentName != null) {

            this.onBindAttemptCompleteCallback = callback;

            isBound = getContext().bindService(new Intent(getContext(), MediaService.class),
                    serviceConnection,
                    Context.BIND_AUTO_CREATE);


            if (!isBound) {
                callback.onFailure();
            }
        }
    }

    public void unbindService() {
        if (isBound) {
            getContext().unbindService(serviceConnection);
            isBound = false;
        }
    }

    private void populateAttributes(Context context, AttributeSet attrs) {
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.SoundAffect);
        if (a != null) {
            try {
                trackResourceId = a.getResourceId(R.styleable.SoundAffect_trackResource, -1);
                showPrevButton = a.getBoolean(R.styleable.SoundAffect_showPrevButton, false);
                showCurrentTime = a.getBoolean(R.styleable.SoundAffect_showCurrentTime, true);
                showDuration = a.getBoolean(R.styleable.SoundAffect_showDuration, true);
                positionIndicatorShape = a.getString(R.styleable.SoundAffect_positionIndicatorShape);
                positionIndicatorColor = a.getColor(R.styleable.SoundAffect_positionIndicatorColor, -1);
                seekBarColor = a.getColor(R.styleable.SoundAffect_seekBarColor, -1);
                playButtonColor = a.getColor(R.styleable.SoundAffect_playButtonColor, -1);
                prevButtonColor = a.getColor(R.styleable.SoundAffect_prevButtonColor, -1);
            } finally {
                a.recycle();
            }
        }
    }

    private void setupPaints() {
        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.BLACK);
        textPaint.setTextSize(40.0f);

        playButtonPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        prevButtonPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        if (playButtonColor != -1) {
            playButtonPaint.setColorFilter(new PorterDuffColorFilter(playButtonColor, PorterDuff.Mode.SRC_IN));
        }

        if (prevButtonColor != -1) {
            prevButtonPaint.setColorFilter(new PorterDuffColorFilter(prevButtonColor, PorterDuff.Mode.SRC_IN));
        }

        seekPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        seekPaint.setColor(seekBarColor != -1 ? seekBarColor : Color.BLACK);
        seekPaint.setStyle(Paint.Style.FILL);

        notchPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        notchPaint.setColor(positionIndicatorColor != -1 ? positionIndicatorColor : Color.RED);
        notchPaint.setStyle(Paint.Style.FILL);

        debugPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        debugPaint.setColor(Color.RED);
        debugPaint.setStyle(Paint.Style.STROKE);
    }

    private void setupUIElements() {
        playButtonImage = BitmapFactory.decodeResource(getResources(), R.drawable.ic_play_circle_outline_black_24dp);
        pauseButtonImage = BitmapFactory.decodeResource(getResources(), R.drawable.ic_pause_circle_outline_black_24dp);
        prevButtonImage = BitmapFactory.decodeResource(getResources(), R.drawable.ic_skip_previous_black_24dp);

        if (isInEditMode()) {
            return;
        }

        //Runnables + touch detection
        currentPositionHandlerThread = new HandlerThread("current_position");
        currentPositionHandlerThread.start();
        currentPositionHandler = new Handler(currentPositionHandlerThread.getLooper());
        mainThreadHandler = new Handler(Looper.getMainLooper());
        tapRect = new Rect(0, 0, 5, 5);
    }

    private void setupDrawingPositions() {
        int seekLeft = getPaddingLeft();
        int seekTop = getHeightWithPadding() / 3;
        int seekRight = getWidth() - getPaddingRight();
        int seekBottom = seekTop + SEEK_AND_NOTCH_THICKNESS;

        int centerLeft = (getWidthWithPadding() / 2) - (playButtonImage.getWidth() / 2);
        int centerTop = getHeightWithPadding() / 2;
        seekbarRect = new Rect(seekLeft, seekTop, seekRight, seekBottom);
        seekbarTouchRect = new Rect(seekLeft, seekTop - SEEK_BAR_TOUCH_THICKNESS / 2,
                seekRight, seekBottom + SEEK_BAR_TOUCH_THICKNESS / 2);


        int notchLeft = seekLeft;
        int notchTop = seekTop - SEEK_NOTCH_HEIGHT;
        int notchRight = notchLeft + SEEK_AND_NOTCH_THICKNESS;
        int notchBottom = seekTop + SEEK_NOTCH_HEIGHT + seekbarRect.height();
        notchRect = new Rect(notchLeft, notchTop, notchRight, notchBottom);
        updateNotchTouchRect(notchRect);

        playPauseButtonRect = new Rect(centerLeft, centerTop,
                centerLeft + playButtonImage.getWidth(),
                centerTop + playButtonImage.getHeight());

        if (showPrevButton) {
            int left = (int) (centerLeft - Math.round(prevButtonImage.getWidth() * 1.5));
            prevButtonRect = new Rect(left, centerTop, left + playButtonImage.getWidth(),
                    centerTop + playButtonImage.getHeight());
        }
    }

    private int getWidthWithPadding() {
        return getWidth() + getPaddingLeft() - getPaddingRight();
    }

    private int getHeightWithPadding() {
        return getHeight() + getPaddingTop() - getPaddingBottom();
    }

    private void updateNotchTouchRect(Rect notchRect) {
        if (notchTouchRect == null) {
            notchTouchRect = new Rect();
        }

        notchTouchRect.left = notchRect.left - NOTCH_TOUCH_THICKNESS / 2;
        notchTouchRect.right = notchRect.right + NOTCH_TOUCH_THICKNESS / 2;
        notchTouchRect.top = notchRect.top - NOTCH_TOUCH_THICKNESS / 2;
        notchTouchRect.bottom = notchRect.bottom + NOTCH_TOUCH_THICKNESS / 2;
        Log.i(TAG, "Update touch rect: " + notchTouchRect);
    }

    private void moveNotchRect(float left) {
        if (left < seekbarRect.left || left > seekbarRect.right) {
            return;
        }

        notchRect.left = Math.round(left);
        notchRect.right = notchRect.left + SEEK_AND_NOTCH_THICKNESS;
        updateNotchTouchRect(notchRect);
        updateCurrentPosition();
        invalidate();
    }

    private void updateNotchRect(int percent) {
        int notchLeft, notchTop, notchRight, notchBottom;

        if (percent == 0) {
            notchLeft = seekbarRect.left;
        } else if (percent < 100) {
            notchLeft = seekbarRect.width() / 100 * percent + seekbarRect.left;
        } else {
            notchLeft = seekbarRect.right;
        }

        notchTop = seekbarRect.top - SEEK_NOTCH_HEIGHT;
        notchRight = notchLeft + SEEK_AND_NOTCH_THICKNESS;
        notchBottom = seekbarRect.top + SEEK_NOTCH_HEIGHT + seekbarRect.height();

        notchRect.left = notchLeft;
        notchRect.top = notchTop;
        notchRect.right = notchRight;
        notchRect.bottom = notchBottom;

        updateNotchTouchRect(notchRect);
    }

    public void loadUrl(String url) {
        mediaService.loadUrl(url);
    }

    private void loadResource(int resourceId) {
        mediaService.loadResource(resourceId);
    }

    public void togglePlayPause() {
        if (mediaService.isPlaying()) {
            pause();
        } else {
            play();
        }
        invalidate();
    }

    public void play() {
        if (mediaService.isPrepared()) {
            mediaService.play();
            currentPositionHandler.post(currentPositionRunnable);
        }
    }

    public void pause() {
        if (mediaService.isPlaying()) {
            mediaService.pause();
            currentPositionHandler.removeCallbacks(currentPositionRunnable);
        }
    }

    public void reset() {
        if (mediaService.isPlaying()) {
            pause();
            mediaService.reset();
            play();
        } else {
            mediaService.reset();
            updateNotchRect(getPercentageComplete());
            invalidate();
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        setupDrawingPositions();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        int desiredWidth = 500;
        int desiredHeight = 200;

        switch (widthMode) {
            case MeasureSpec.AT_MOST: {
                width = Math.min(desiredWidth, width);
                break;
            }
            case MeasureSpec.EXACTLY: {
                break;
            }
            case MeasureSpec.UNSPECIFIED: {
                width = desiredWidth;
                break;
            }
        }

        switch (heightMode) {
            case MeasureSpec.AT_MOST: {
                height = Math.min(desiredHeight, height);
                break;
            }
            case MeasureSpec.EXACTLY: {
                break;
            }
            case MeasureSpec.UNSPECIFIED: {
                height = desiredHeight;
                break;
            }
        }

        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (!isBound || mediaService != null) {
            return;
        }

        drawTimestamps(canvas);
        drawSeekBar(canvas);
        drawControls(canvas);
        //drawDebug(canvas);
    }

    private void drawControls(Canvas canvas) {
        if (isInEditMode()) {
            canvas.drawBitmap(playButtonImage, playPauseButtonRect.left, playPauseButtonRect.top, playButtonPaint);
        } else {
            if (mediaService.isPlaying()) {
                canvas.drawBitmap(pauseButtonImage, playPauseButtonRect.left, playPauseButtonRect.top, playButtonPaint);
            } else {
                canvas.drawBitmap(playButtonImage, playPauseButtonRect.left, playPauseButtonRect.top, playButtonPaint);
            }
        }

        if (showPrevButton) {
            canvas.drawBitmap(prevButtonImage, prevButtonRect.left, prevButtonRect.top, prevButtonPaint);
        }
    }

    private void drawSeekBar(Canvas canvas) {
        canvas.drawRect(seekbarRect, seekPaint);

        if (positionIndicatorShape != null && positionIndicatorShape.equals(INDICATOR_DOT)) {
            canvas.drawCircle(notchRect.left + notchRect.width() / 2,
                    notchRect.top + notchRect.height() / 2, SEEK_NOTCH_DOT_RADIUS, notchPaint);
        } else {
            canvas.drawRect(notchRect, notchPaint);
        }
    }

    private void drawTimestamps(Canvas canvas) {
        if (showDuration && mediaService.isPrepared()) {
            canvas.drawText(getFormattedDuration(), seekbarRect.right - textPaint.measureText(getFormattedDuration()),
                    seekbarRect.top - TIMESTAMP_MARGIN_BOTTOM,
                    textPaint);
        }

        if (showCurrentTime) {
            canvas.drawText(getCurrentTime(), seekbarRect.left,
                    seekbarRect.top - TIMESTAMP_MARGIN_BOTTOM, textPaint);
        }
    }

    private void drawDebug(Canvas canvas) {
        canvas.drawRect(notchTouchRect, debugPaint);
        canvas.drawRect(seekbarTouchRect, debugPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            int x = (int) event.getX();
            int y = (int) event.getY();

            tapRect.left = x;
            tapRect.top = y;
            tapRect.right = x + 5;
            tapRect.bottom = y + 5;

            if (playPauseButtonRect.contains(tapRect)) {
                Toast.makeText(context, "Touched play button", Toast.LENGTH_SHORT).show();
                togglePlayPause();
                return true;
            }

            if (showPrevButton) {
                if (prevButtonRect.contains(tapRect)) {
                    Toast.makeText(context, "Touched prev button", Toast.LENGTH_SHORT).show();
                    reset();
                    return true;
                }
            }

            if (notchTouchRect.contains(tapRect)) {
                wasPlayingBeforeSeek = mediaService.isPlaying();

                isSeeking = true;

                if (mediaService.isPlaying()) {
                    pause();
                }
            }
        }

        if (event.getAction() == MotionEvent.ACTION_UP) {
            if (isSeeking) {
                isSeeking = false;

                updateCurrentPosition();

                if (wasPlayingBeforeSeek) {
                    play();
                } else {
                    Log.i(TAG, "Not playing beforehand");
                }
            }
        }

        if (event.getAction() == MotionEvent.ACTION_MOVE) {
            if (isSeeking) {
                Log.i(TAG, "Move: " + event.getX() + ":" + event.getY());
                moveNotchRect(event.getX());
            }
            return true;
        }

        return true;
    }

    //Work out current track position based on the current position of the 'notch'
    //relative to the seekBarRect
    private void updateCurrentPosition() {
        if (mediaService.isPrepared()) {
            float currentNotchPos = notchRect.left;
            float seekEnd = seekbarRect.right;
            float percentage = (currentNotchPos / seekEnd) * 100.0f;
            float duration = mediaService.getDuration();
            float newPos = (duration / 100.0f) * percentage;

            //Update the mediaPlayer and start playback from that point
            //mediaManager.setCurrentPosition(Math.round(newPos));
            mediaService.setCurrentPosition(Math.round(newPos));
        } else {
            Log.i(TAG, "Not prepared!!");
        }
    }

    private String getFormattedDuration() {
        if (isInEditMode()) {
            return "01:00";
        }

        int duration = mediaService.getDuration();
        return String.format(Locale.getDefault(), "%02d:%02d",
                TimeUnit.MILLISECONDS.toMinutes(duration),
                TimeUnit.MILLISECONDS.toSeconds(duration) -
                        TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(duration)));
    }

    private String getCurrentTime() {
        if (isInEditMode()) {
            return "00:00";
        }

        int currentPosition = mediaService.getCurrentPosition();
        return String.format(Locale.getDefault(), "%02d:%02d",
                TimeUnit.MILLISECONDS.toMinutes(currentPosition),
                TimeUnit.MILLISECONDS.toSeconds(currentPosition) -
                        TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(currentPosition)));
    }

    private int getPercentageComplete() {
        float current = mediaService.getCurrentPosition();
        float duration = mediaService.getDuration();
        float percentage = (current / duration) * 100.0f;
        //Log.i(TAG, "Percentage: " + percentage + " Returning: " + Math.round(percentage));
        return Math.round(percentage);
    }
}
