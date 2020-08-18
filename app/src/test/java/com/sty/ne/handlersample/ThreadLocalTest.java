package com.sty.ne.handlersample;

import org.junit.Test;

import androidx.annotation.Nullable;

/**
 * @Author: tian
 * @UpdateDate: 2020-08-17 19:41
 */
public class ThreadLocalTest {

    @Test
    public void test() {
        //创建本地线程（主线程）
        final ThreadLocal<String> threadLocal = new ThreadLocal<String>() {
            @Nullable
            @Override
            protected String initialValue() {
                //重写初始化方法，默认返回null，如果ThreadLocalMap拿不到值再调用初始化方法
                return "天涯路";
            }
        };

        //从ThreadLocalMap中获取string值，key是主线程
        System.out.println("主线程threadLocal: " + threadLocal.get());

        //------------------------thread-0
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                //从ThreadLocalMap中获取key: thread-0的值？没有，拿不到值再调用初始化方法
                String value1 = threadLocal.get();
                System.out.println(Thread.currentThread().getName() + ": " + value1); //天涯路

                //ThreadLocalMap存入：key:thread-0  value:"走天涯"
                threadLocal.set("走天涯");
                String value2 = threadLocal.get();
                System.out.println(Thread.currentThread().getName() + " set >> : " + value2); //走天涯

                //使用完成建议remove()，避免大量无意义的内存占用
                threadLocal.remove();
            }
        });
        thread.start();

        //------------------------thread-1
        Thread thread1 = new Thread(new Runnable() {
            @Override
            public void run() {
                //从ThreadLocalMap中获取key: thread-1的值？没有，拿不到值再调用初始化方法
                String value1 = threadLocal.get();
                System.out.println(Thread.currentThread().getName() + ": " + value1); //天涯路

                //ThreadLocalMap存入：key:thread-1  value:"断肠人"
                threadLocal.set("断肠人");
                String value2 = threadLocal.get();
                System.out.println(Thread.currentThread().getName() + " set >> : " + value2); //断肠人

                //使用完成建议remove()，避免大量无意义的内存占用
                threadLocal.remove();
            }
        });
        thread1.start();
    }
}
