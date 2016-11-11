package com.xyin.library;

/**
 * Created by Administrator on 2016/11/11.
 */

public interface MHeadView {

    /**
     * 头部隐藏后会回调这个方法
     */
    void reset();

    /**
     * 下拉出头部的一瞬间调用
     */
    void pull();

    /**
     * 正在刷新的时候调用
     */
    void refreshing();

    /**
     * 当head view 位置发生改变时会回调该方法
     * @param currentTop 当前head view的top
     * @param offset 偏移量 offset > 0表示一个向下的手势,反之向上的手势
     * @param state RefreshLayout当前的状态,eg{@link MRefreshLayout#DRAGGING}
     */
    void onPositionChange(float currentTop, float offset,int state);

    /**
     * 刷新完成的时候调用
     */
    void complete();

}

