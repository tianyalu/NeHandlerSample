package com.sty.ne.handlersample;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;
import android.widget.TextView;

/**
 * 1、Handler内存泄漏测试
 * 2、为什么不能在子线程创建Handler
 * 3、textView.setText()只能在主线程执行-->这句话是错误的
 * 4、new Handler()两种写法有什么区别
 * 5、ThreadLocal用法和原理
 */
public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();
    private TextView textView;

    //4、new Handler()两种写法有什么区别
    private Handler handler1 = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(@NonNull Message msg) {
            startActivity(new Intent(MainActivity.this, PersonalActivity.class));
            return false;
        }
    });

    private Handler handler2 = new Handler() {
        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            textView.setText(msg.obj.toString());
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initView();
        test();
    }

    private void initView() {
        textView = findViewById(R.id.tv_text);
    }

    private void test() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                //常规的写法
                Message message = new Message();
//                message.obj = "天涯路";
//                message.what = 123;
//                handler2.sendMessage(message);

                //1、Handler内存泄漏测试(假象)
                SystemClock.sleep(3000); //销毁Activity
                message.what = 3;
                if(handler1 != null) {
                    handler1.sendMessage(message); //跳转到第二个界面
                }
//                handler1.sendMessageDelayed(message, 3000);
            }
        }).start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.e(TAG, "onDestroy --> ");
        handler1.removeMessages(3);
        handler1 = null;
    }
}
