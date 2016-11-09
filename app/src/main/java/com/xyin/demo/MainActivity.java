package com.xyin.demo;

import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;

import com.xyin.library.MRefreshLayout;
import com.xyin.library.RefreshLayout;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

//    MRefreshLayout refreshLayout;
    RecyclerView recycler;
//    SwipeRefreshLayout

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

//        refreshLayout = (TestSwipeRefreshLayout) findViewById(R.id.swipe_container);
//
//        refreshLayout.setProgressViewOffset(false, 0, (int) TypedValue
//                .applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24, getResources()
//                        .getDisplayMetrics()));
//
//        refreshLayout.setOnRefreshListener(new TestSwipeRefreshLayout.OnRefreshListener() {
//            @Override
//            public void onRefresh() {
//                handler.sendEmptyMessageDelayed(1, 2500);
//            }
//        });
//
        recycler = (RecyclerView) findViewById(R.id.recycler);
        LinearLayoutManager manager = new LinearLayoutManager(this);
        recycler.setLayoutManager(manager);
        recycler.setAdapter(new MyAdapter());
    }

    @Override
    public void onClick(View v) {
//        refreshLayout.setRefreshing(false);
    }

//    Handler handler = new Handler(new Handler.Callback() {
//        @Override
//        public boolean handleMessage(Message msg) {
//            refreshLayout.setRefreshing(false);
//            return true;
//        }
//    });

}
