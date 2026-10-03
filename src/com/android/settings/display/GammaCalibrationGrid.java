//Vmax

package com.android.settings.display;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.GridLayout;
import android.widget.TextView;

import com.android.settings.R;

/** Near-black calibration patches with fixed RGB colors, independent of the Settings theme. */
public class GammaCalibrationGrid extends GridLayout {
    private static final int COLUMNS = 5;
    private static final int ROWS = 4;
    private static final int[] LEVELS = {
        1, 2, 3, 4, 5,
        6, 7, 8, 9, 10,
        11, 12, 13, 14, 15,
        20, 25, 30, 35, 40,
    };

    private final int mGap;

    public GammaCalibrationGrid(Context context) {
        this(context, null);
    }

    public GammaCalibrationGrid(Context context, AttributeSet attrs) {
        super(context, attrs);
        setColumnCount(COLUMNS);
        setRowCount(ROWS);
        setUseDefaultMargins(false);
        // Keep brightness values ascending from left to right in every locale.
        setLayoutDirection(LAYOUT_DIRECTION_LTR);
        setBackgroundColor(Color.BLACK);
        setForceDarkAllowed(false);
        mGap = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4,
                getResources().getDisplayMetrics()));

        for (int i = 0; i < LEVELS.length; i++) {
            int level = LEVELS[i];
            TextView square = new TextView(context);
            square.setBackgroundColor(Color.rgb(level, level, level));
            square.setTextColor(Color.rgb(255, 40, 40));
            square.setText(Integer.toString(level));
            square.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
            square.setGravity(Gravity.CENTER);
            square.setIncludeFontPadding(false);
            square.setForceDarkAllowed(false);
            square.setContentDescription(context.getString(
                    R.string.gamma_calibration_square_description, level));

            int row = i / COLUMNS;
            int column = i % COLUMNS;
            LayoutParams params = new LayoutParams(spec(row), spec(column));
            params.rightMargin = column < COLUMNS - 1 ? mGap : 0;
            params.bottomMargin = row < ROWS - 1 ? mGap : 0;
            addView(square, params);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int fallbackWidth = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                320, getResources().getDisplayMetrics()));
        int width = resolveSize(fallbackWidth, widthMeasureSpec);
        int availableWidth = width - getPaddingLeft() - getPaddingRight();
        int side = Math.max(0, (availableWidth - mGap * (COLUMNS - 1)) / COLUMNS);
        for (int i = 0; i < getChildCount(); i++) {
            LayoutParams params = (LayoutParams) getChildAt(i).getLayoutParams();
            params.width = side;
            params.height = side;
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
