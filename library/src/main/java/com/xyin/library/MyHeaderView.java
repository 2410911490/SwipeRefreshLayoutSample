package com.xyin.library;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * Created by Administrator on 2016/11/10.
 */

public class MyHeaderView extends FrameLayout implements MHeadView{

    private Animation rotate_up;
    private Animation rotate_down;
    private Animation rotate_infinite;
    private TextView textView;
    private View arrowIcon;
    private View successIcon;
    private View loadingIcon;

    public MyHeaderView(Context context) {
        this(context,null);
    }

    public MyHeaderView(Context context, AttributeSet attrs) {
        super(context, attrs);

        // 初始化动画
        rotate_up = AnimationUtils.loadAnimation(context , R.anim.rotate_up);
        rotate_down = AnimationUtils.loadAnimation(context , R.anim.rotate_down);
        rotate_infinite = AnimationUtils.loadAnimation(context , R.anim.rotate_infinite);

        inflate(context, R.layout.header_qq, this);

        textView = (TextView) findViewById(R.id.text);
        arrowIcon = findViewById(R.id.arrowIcon);
        successIcon = findViewById(R.id.successIcon);
        loadingIcon = findViewById(R.id.loadingIcon);
    }

//    @Override
//    public void reset() {
//        Log.e("MRefreshLayout", "MyHeaderView reset()");
//    }
//
//    @Override
//    public void pull() {
//        Log.e("MRefreshLayout", "pull()");
//    }
//
//    @Override
//    public void refreshing() {
//        Log.e("MRefreshLayout", "refreshing()");
//    }
//
//    @Override
//    public void onPositionChange(float currentTop, float offset, int state) {
////        Log.e("MRefreshLayout", "onPositionChange(), currentTop = " + currentTop + ", offset = " + offset + ",state = " + state);
//    }
//
//    @Override
//    public void complete() {
//        Log.e("MRefreshLayout", "complete()");
//    }

    @Override
    public void reset() {
        textView.setText(getResources().getText(R.string.qq_header_reset));
        successIcon.setVisibility(INVISIBLE);
        arrowIcon.setVisibility(VISIBLE);
        arrowIcon.clearAnimation();
        loadingIcon.setVisibility(INVISIBLE);
        loadingIcon.clearAnimation();
    }

    @Override
    public void pull() {

    }

    @Override
    public void refreshing() {
        arrowIcon.setVisibility(INVISIBLE);
        loadingIcon.setVisibility(VISIBLE);
        textView.setText(getResources().getText(R.string.qq_header_refreshing));
        arrowIcon.clearAnimation();
        loadingIcon.startAnimation(rotate_infinite);
    }

    @Override
    public void onPositionChange(float currentPos, float offset, int state) {
        // 往上拉
        if (offset < 0) {
            textView.setText(getResources().getText(R.string.qq_header_pull));
            arrowIcon.clearAnimation();
            arrowIcon.startAnimation(rotate_down);
            // 往下拉
        } else if (offset > 0) {
            textView.setText(getResources().getText(R.string.qq_header_pull_over));
            arrowIcon.clearAnimation();
            arrowIcon.startAnimation(rotate_up);
        }
    }

    @Override
    public void complete() {
        loadingIcon.setVisibility(INVISIBLE);
        loadingIcon.clearAnimation();
        successIcon.setVisibility(VISIBLE);
        textView.setText(getResources().getText(R.string.qq_header_completed));
    }
}
