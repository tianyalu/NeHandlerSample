package com.sty.ne.handlersample;

import com.sty.ne.handlersample.core.Handler;
import com.sty.ne.handlersample.core.Looper;
import com.sty.ne.handlersample.core.Message;

import org.junit.Test;

/**
 * @Author: tian
 * @UpdateDate: 2020-08-18 19:51
 */
public class ActivityThread {
    @Test
    public void main() {
        //创建全局唯一的，主线程Looper对象，以及MessageQueue消息队列对象
        Looper.prepare();

        //模拟Activity中创建Handler对象
        final Handler handler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                System.out.println(msg.obj.toString( ));
            }
        };
        //消费消息，回调方法（接口方法）

        //子线程发送消息
        new Thread(new Runnable() {
            @Override
            public void run() {
                Message message = new Message();
                message.obj = "hello world";
                handler.sendMessage(message);
            }
        }).start();

        //轮询，取出消息
        Looper.loop();
    }
}
