package com.sty.ne.handlersample.core;

/**
 * @Author: tian
 * @UpdateDate: 2020-08-18 19:52
 */
public class Looper {

    static final ThreadLocal<Looper> sThreadLocal = new ThreadLocal<>();
    public MessageQueue mQueue;

    private Looper() {
        mQueue = new MessageQueue();
    }

    public static void prepare() {
        //主线程只有唯一一个Looper对象
        if(sThreadLocal.get() != null) {
            throw new RuntimeException("Only one Looper may be created per thread");
        }

        //应用启动时，初始化赋值
        sThreadLocal.set(new Looper());

    }

    public static Looper myLooper() {
        return sThreadLocal.get();
    }

    //轮询，提取消息
    public static void loop() {
        //从全局ThreadLocalMap中获取唯一Looper对象
        Looper me = myLooper();
        //从Looper对象中获取全局唯一消息队列MessageQueue对象
        final MessageQueue queue = me.mQueue;

        Message resultMessage;
        //从消息队列中取消息
        while (true) {
            Message msg = queue.next();

            if(msg != null && msg.target != null) {
                msg.target.dispatchMessage(msg);
            }
        }
    }
}
