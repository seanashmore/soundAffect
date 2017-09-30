package com.alittlelost.soundaffect;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Created by seanashmore on 19/09/2017.
 */

public class SoundAffect extends View {

    private static final String TAG = "SoundAffect";
    private final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
    private final String APP_NS = "http://schemas.android.com/apk/res-auto";
    private final String ATTR_TRACK_RESOURCE = "trackResource";
    private final String ATTR_SHOW_PREV_BUTTON = "showPrevButton";
    private final String ATTR_INDICATOR_SHAPE = "positionIndicatorShape";
    private final String INDICATOR_DOT = "dot";
    private final String INDICATOR_NOTCH = "notch";

    private final int SEEK_AND_NOTCH_THICKNESS = 5;
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
    private Paint textPaint, buttonPaint, seekPaint, notchPaint, debugPaint;

    //Bitmaps for audio controls
    private Bitmap playButtonImage, pauseButtonImage, prevButtonImage;
    private Rect playPauseButtonRect, prevButtonRect;
    private Rect seekbarRect, seekbarTouchRect;
    private Rect notchRect, notchTouchRect, tapRect;

    private MediaManager mediaManager;

    private HashMap<String, Integer> attrNameToIndexMap = new HashMap<>();

    private boolean isSeeking, wasPlayingBeforeSeek = false;

    //App attrs
    private int trackResourceId;
    private boolean showPrevButton;
    private String positionIndicatorShape;

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

        setupPaints();
        setupUIElements();
        populateAttributes(attrs);

        if (isInEditMode()) {
            return;
        }

        this.context = context;
        this.mediaManager = new MediaManager(context);

        if (trackResourceId != -1) {
            loadResource(trackResourceId);
        }
    }

    private void populateAttributes(AttributeSet attrs) {
        //Populate the map
        if (attrs != null) {
            for (int i = 0; i < attrs.getAttributeCount(); i++) {
                attrNameToIndexMap.put(attrs.getAttributeName(i), i);
            }
        }

        trackResourceId = attrs.getAttributeResourceValue(APP_NS, ATTR_TRACK_RESOURCE, -1);
        showPrevButton = attrs.getAttributeBooleanValue(APP_NS, ATTR_SHOW_PREV_BUTTON, false);

        if (attrNameToIndexMap.containsKey(ATTR_INDICATOR_SHAPE)) {
            positionIndicatorShape = attrs.getAttributeValue(attrNameToIndexMap.get(ATTR_INDICATOR_SHAPE));
        }
    }

    private void setupPaints() {
        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.BLACK);
        textPaint.setTextSize(50.0f);

        buttonPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        buttonPaint.setColor(Color.WHITE);

        seekPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        seekPaint.setColor(Color.BLACK);
        seekPaint.setStyle(Paint.Style.FILL);

        notchPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        notchPaint.setColor(Color.RED);
        notchPaint.setStyle(Paint.Style.FILL);

        debugPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        debugPaint.setColor(Color.RED);
        debugPaint.setStyle(Paint.Style.STROKE);
    }

    private void setupUIElements() {
        playButtonImage = BitmapFactory.decodeResource(getResources(), R.drawable.ic_play_circle_outline_black_24dp);
        //playButtonImage = Bitmap.createScaledBitmap(playButtonImage, playButtonImage.getWidth() * 2, playButtonImage.getHeight() * 2, false);

        pauseButtonImage = BitmapFactory.decodeResource(getResources(), R.drawable.ic_pause_circle_outline_black_24dp);
        //pauseButtonImage = Bitmap.createScaledBitmap(pauseButtonImage, pauseButtonImage.getWidth() * 2, pauseButtonImage.getHeight() * 2, false);

        prevButtonImage = BitmapFactory.decodeResource(getResources(), R.drawable.ic_skip_previous_black_24dp);
        //prevButtonImage = Bitmap.createScaledBitmap(prevButtonImage, prevButtonImage.getWidth() * 2, prevButtonImage.getHeight() * 2, false);

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
        int seekRight = getWidthWithPadding();
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
        if (left < seekbarRect.left) {
            return;
        }

        if (left > seekbarRect.right) {
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

    private void loadUrl(String url) {
        mediaManager.loadUrl(url);
    }

    private void loadResource(int resourceId) {
        mediaManager.loadResource(resourceId);
    }

    public void togglePlayPause() {
        if (mediaManager.isPlaying()) {
            pause();
        } else {
            play();
        }
        invalidate();
    }

    public void play() {
        mediaManager.play();
        currentPositionHandler.post(currentPositionRunnable);
    }

    public void pause() {
        mediaManager.pause();
        currentPositionHandler.removeCallbacks(currentPositionRunnable);
    }

    public void reset() {
        if (mediaManager.isPlaying()) {
            pause();
            mediaManager.reset();
            play();
        } else {
            mediaManager.reset();
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

        canvas.drawColor(Color.WHITE);
        drawTimestamps(canvas);
        drawSeekBar(canvas);
        drawControls(canvas);
        //drawDebug(canvas);
    }

    private void drawControls(Canvas canvas) {
        if (isInEditMode()) {
            canvas.drawBitmap(playButtonImage, playPauseButtonRect.left, playPauseButtonRect.top, buttonPaint);
        } else {
            if (mediaManager.isPlaying()) {
                canvas.drawBitmap(pauseButtonImage, playPauseButtonRect.left, playPauseButtonRect.top, buttonPaint);
            } else {
                canvas.drawBitmap(playButtonImage, playPauseButtonRect.left, playPauseButtonRect.top, buttonPaint);
            }
        }

        if (showPrevButton) {
            canvas.drawBitmap(prevButtonImage, prevButtonRect.left, prevButtonRect.top, buttonPaint);
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
        canvas.drawText(getFormattedDuration(), seekbarRect.right - textPaint.measureText(getFormattedDuration()),
                seekbarRect.top - TIMESTAMP_MARGIN_BOTTOM,
                textPaint);

        canvas.drawText(getCurrentTime(), seekbarRect.left,
                seekbarRect.top - TIMESTAMP_MARGIN_BOTTOM, textPaint);
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
                if (mediaManager.isPlaying()) {
                    wasPlayingBeforeSeek = true;
                } else {
                    wasPlayingBeforeSeek = false;
                }

                isSeeking = true;

                if (mediaManager.isPlaying()) {
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
        if (mediaManager.isPrepared()) {

            float currentNotchPos = notchRect.left;
            float seekEnd = seekbarRect.right;
            float percentage = (currentNotchPos / seekEnd) * 100.0f;
            float duration = mediaManager.getDuration();
            float newPos = (duration / 100.0f) * percentage;

            //Update the mediaPlayer and start playback from that point
            mediaManager.setCurrentPosition(Math.round(newPos));
        } else {
            Log.i(TAG, "Not prepared!!");
        }
    }

    private String getFormattedDuration() {
        if (isInEditMode()) {
            return "01:00";
        }

        return String.format(Locale.getDefault(), "%02d:%02d",
                TimeUnit.MILLISECONDS.toMinutes(mediaManager.getDuration()),
                TimeUnit.MILLISECONDS.toSeconds(mediaManager.getDuration()) -
                        TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(mediaManager.getDuration()))
        );
    }

    private String getCurrentTime() {
        if (isInEditMode()) {
            return "00:00";
        }

        return String.format(Locale.getDefault(), "%02d:%02d",
                TimeUnit.MILLISECONDS.toMinutes(mediaManager.getCurrentPosition()),
                TimeUnit.MILLISECONDS.toSeconds(mediaManager.getCurrentPosition()) -
                        TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(mediaManager.getCurrentPosition()))
        );
    }

    private int getPercentageComplete() {
        float current = mediaManager.getCurrentPosition();
        float duration = mediaManager.getDuration();
        float percentage = (current / duration) * 100.0f;
        //Log.i(TAG, "Percentage: " + percentage + " Returning: " + Math.round(percentage));
        return Math.round(percentage);
    }
}
