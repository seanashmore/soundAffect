package com.alittlelost.soundaffect;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
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
    private final String ATTR_PADDING = "padding";

    private Context context;

    //Handler & thread to update current track position
    private HandlerThread currentPositionHandlerThread;
    private Handler currentPositionHandler;
    private Handler mainThreadHandler;

    //Paint to draw UI elements and debugging
    private Paint textPaint, buttonPaint, seekPaint, notchPaint, debugPaint;
    private float textSize;

    //Bitmaps for audio controls
    private Bitmap playButtonImage, pauseButtonImage, prevButtonImage;
    private Rect playPauseButtonRect, prevButtonRect;
    private Rect seekbarRect, seekNotchRect;

    private Rect tapRect;

    private MediaManager mediaManager;

    private HashMap<String, Integer> attrNameToIndexMap = new HashMap<>();
    private AttributeSet attributeSet;

    //Android attrs
    private int padding;

    //App attrs
    private int trackResourceId;
    private boolean showPrevButton;

    private Runnable currentPositionRunnable = new Runnable() {
        @Override
        public void run() {
            Log.i(TAG, "Calling invalidate");
            updateNotchRect(getPercentageComplete());

            mainThreadHandler.post(new Runnable() {
                @Override
                public void run() {
                    invalidate();
                }
            });
            Log.i(TAG, "Runnable ended");

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
        this.attributeSet = attrs;
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
    }

    private void setupPaints() {
        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.BLACK);
        textPaint.setTextSize(50.0f);
        textSize = textPaint.getTextSize();

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
        playButtonImage = Bitmap.createScaledBitmap(playButtonImage, playButtonImage.getWidth() * 2, playButtonImage.getHeight() * 2, false);

        pauseButtonImage = BitmapFactory.decodeResource(getResources(), R.drawable.ic_pause_circle_outline_black_24dp);
        pauseButtonImage = Bitmap.createScaledBitmap(pauseButtonImage, pauseButtonImage.getWidth() * 2, pauseButtonImage.getHeight() * 2, false);

        //if (showPrevButton) {
            prevButtonImage = BitmapFactory.decodeResource(getResources(), R.drawable.ic_skip_previous_black_24dp);
            prevButtonImage = Bitmap.createScaledBitmap(prevButtonImage, prevButtonImage.getWidth() * 2, prevButtonImage.getHeight() * 2, false);
        //}

        if (attrNameToIndexMap.containsKey(ATTR_PADDING)) {
            int dp = attributeSet.getAttributeIntValue(attrNameToIndexMap.get(ATTR_PADDING), -1);
            if (dp != -1) {
                Resources r = getResources();
                int px = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, r.getDisplayMetrics());
                setPadding(px, px, px, px);
            }
        }

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
        int seekLeft = 20;
        int seekTop = (int) textSize * 2;
        int seekRight = getWidth() - 20;
        int seekBottom = seekTop + 5;

        int centerLeft = (getWidth() / 2) - (playButtonImage.getWidth() / 2);
        int centerTop = (int) textSize * 3;
        seekbarRect = new Rect(seekLeft, seekTop, seekRight, seekBottom);

        int notchLeft = seekLeft;
        int notchTop = seekTop - 30;
        int notchRight = notchLeft + 5;
        int notchBottom = seekTop + 30 + seekbarRect.height();
        seekNotchRect = new Rect(notchLeft, notchTop, notchRight, notchBottom);

        playPauseButtonRect = new Rect(centerLeft, centerTop, centerLeft + playButtonImage.getWidth(), centerTop + playButtonImage.getHeight());

        if (showPrevButton) {
            int left = centerLeft - prevButtonImage.getWidth();
            prevButtonRect = new Rect(left, centerTop, left + playButtonImage.getWidth(), centerTop + playButtonImage.getHeight());
        }
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

        notchTop = seekbarRect.top - 30;
        notchRight = notchLeft + 5;
        notchBottom = seekbarRect.top + 30 + seekbarRect.height();

        seekNotchRect.left = notchLeft;
        seekNotchRect.top = notchTop;
        seekNotchRect.right = notchRight;
        seekNotchRect.bottom = notchBottom;
    }

    private void loadUrl(String url) {
        mediaManager.loadUrl(url);
    }

    private void loadResource(int resourceId) {
        mediaManager.loadResource(resourceId);
    }

    public void setOnCompletionListener(MediaPlayer.OnCompletionListener onCompletionListener) {
        mediaManager.setOnCompletionListener(onCompletionListener);
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
        pause();
        mediaManager.reset();
        play();
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
        int desiredHeight = 300;

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

        if (isInEditMode()) {
            canvas.drawColor(Color.WHITE);
            canvas.drawText(getFormattedDuration(), canvas.getWidth() - textPaint.measureText(getFormattedDuration()), textSize, textPaint);
            canvas.drawText(getCurrentTime(), 0, textSize, textPaint);
            canvas.drawRect(seekbarRect, seekPaint);
            canvas.drawRect(seekNotchRect, notchPaint);
            canvas.drawBitmap(playButtonImage, playPauseButtonRect.left, playPauseButtonRect.top, buttonPaint);
            if (showPrevButton) {
                canvas.drawBitmap(prevButtonImage, prevButtonRect.left, prevButtonRect.top, buttonPaint);
            }
            return;
        }

        canvas.drawColor(Color.WHITE);

        //Total duration to right
        canvas.drawText(getFormattedDuration(), canvas.getWidth() - textPaint.measureText(getFormattedDuration()), textSize, textPaint);
        //Current position to left
        canvas.drawText(getCurrentTime(), 0, textSize, textPaint);

        canvas.drawRect(seekbarRect, seekPaint);
        canvas.drawRect(seekNotchRect, notchPaint);

        if (mediaManager.isPlaying()) {
            canvas.drawBitmap(pauseButtonImage, playPauseButtonRect.left, playPauseButtonRect.top, buttonPaint);
        } else {
            canvas.drawBitmap(playButtonImage, playPauseButtonRect.left, playPauseButtonRect.top, buttonPaint);
        }

        if (showPrevButton) {
            canvas.drawBitmap(prevButtonImage, prevButtonRect.left, prevButtonRect.top, buttonPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            int x = (int) event.getX();
            int y = (int) event.getY();

            Log.i(TAG, "X = " + x + " && Y = " + y);
            Log.i(TAG, "L = " + playPauseButtonRect.left + " T = " + playPauseButtonRect.top
                    + " R = " + (playPauseButtonRect.left + playPauseButtonRect.width())
                    + " B = " + (playPauseButtonRect.top + playPauseButtonRect.height()));

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
        }

        return false;
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
        Log.i(TAG, "Percentage: " + percentage + " Returning: " + Math.round(percentage));
        return Math.round(percentage);
    }
}
