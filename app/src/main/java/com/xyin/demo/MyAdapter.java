package com.xyin.demo;

import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/**
 * Created by xyin on 2016/11/4.
 */

public class MyAdapter extends RecyclerView.Adapter {

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item, parent, false);
        return new ViewHoder(view);
    }

    @Override
    public void onBindViewHolder(final RecyclerView.ViewHolder holder,int position) {
        ViewHoder mHoder = (ViewHoder) holder;
        mHoder.tv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.e("RefreshLayout", "点击了位置" + holder.getAdapterPosition());
            }
        });

        mHoder.tv.setText("这个位置index = " + position);

    }

    @Override
    public int getItemCount() {
        return 50;
    }

    private class ViewHoder extends RecyclerView.ViewHolder {
        TextView tv;
        public ViewHoder(View itemView) {
            super(itemView);
            tv = (TextView) itemView.findViewById(R.id.tv);
        }

    }


}
