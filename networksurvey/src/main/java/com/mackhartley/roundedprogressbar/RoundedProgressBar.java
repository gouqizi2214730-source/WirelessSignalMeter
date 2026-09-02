package com.mackhartley.roundedprogressbar;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;

import com.craxiom.networksurvey.R;

/**
 * 本地替代实现：com.mackhartley:RoundedProgressBar（原库托管在 jitpack，本机构建环境无法解析）。
 * 提供与原库兼容的自定义属性与 setProgressPercentage / setProgressDrawableColor 等 API，
 * 使 fragment_network_details.xml 与 NetworkDetailsFragment 无需改动。
 */
public class RoundedProgressBar extends View {

    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();

    private float cornerRadius = 4f * getResources().getDisplayMetrics().density;
    private float progressPercent = 0f;
    private int animationLengthMs = 600;

    public RoundedProgressBar(Context context) {
        super(context);
        init(context, null);
    }

    public RoundedProgressBar(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public RoundedProgressBar(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, @Nullable AttributeSet attrs) {
        backgroundPaint.setColor(0xFFE0E0E0);
        progressPaint.setColor(0xFF2196F3);
        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.RoundedProgressBar);
            backgroundPaint.setColor(a.getColor(R.styleable.RoundedProgressBar_rpbBackgroundColor, backgroundPaint.getColor()));
            progressPaint.setColor(a.getColor(R.styleable.RoundedProgressBar_rpbProgressColor, progressPaint.getColor()));
            cornerRadius = a.getDimension(R.styleable.RoundedProgressBar_rpbCornerRadius, cornerRadius);
            animationLengthMs = a.getInt(R.styleable.RoundedProgressBar_rpbAnimationLength, animationLengthMs);
            a.recycle();
        }
    }

    public void setProgressPercentage(float percent, boolean animated) {
        float target = Math.max(0f, Math.min(100f, percent));
        if (animated && target != progressPercent) {
            ValueAnimator animator = ValueAnimator.ofFloat(progressPercent, target);
            animator.setDuration(animationLengthMs);
            animator.addUpdateListener(animation -> {
                progressPercent = (float) animation.getAnimatedValue();
                invalidate();
            });
            animator.start();
        } else {
            progressPercent = target;
            invalidate();
        }
    }

    public void setProgressDrawableColor(@ColorInt int color) {
        progressPaint.setColor(color);
        invalidate();
    }

    @Override
    public void setBackgroundColor(@ColorInt int color) {
        backgroundPaint.setColor(color);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;
        float radius = Math.min(cornerRadius, h / 2f);

        bounds.set(0, 0, w, h);
        canvas.drawRoundRect(bounds, radius, radius, backgroundPaint);

        if (progressPercent > 0f) {
            float progressW = w * progressPercent / 100f;
            bounds.set(0, 0, progressW, h);
            canvas.drawRoundRect(bounds, radius, radius, progressPaint);
        }
    }
}
