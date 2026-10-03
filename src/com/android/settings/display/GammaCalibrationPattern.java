//Vmax

package com.android.settings.display;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/** Fixed RGB 0/1 checkerboard; the daemon previews correction on the actual panel. */
public class GammaCalibrationPattern extends View {
    private final Paint mPaint = new Paint();

    public GammaCalibrationPattern(Context context, AttributeSet attrs) {
        super(context, attrs);
        setForceDarkAllowed(false);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        setMeasuredDimension(width, Math.round(width * 2f / 5));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float side = getWidth() / 5f;
        mPaint.setShader(null);
        for (int i = 0; i < 10; i++) {
            mPaint.setColor(i % 2 == 0 ? Color.BLACK : Color.rgb(1, 1, 1));
            int column = i % 5;
            int row = i / 5;
            canvas.drawRect(column * side, row * side,
                    (column + 1) * side, (row + 1) * side, mPaint);
        }
    }
}
