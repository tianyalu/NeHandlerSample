# `Handler Message`源码分析及手写实现

[TOC]

## 一、与`Handler`相关的几个问题

什么是`Handler`?

`Handler`主要用于异步消息的处理：当发出一个消息之后，首先进入一个消息队列，发送消息的函数即刻返回，而另外一个部分在消息队列中逐一将消息取出，然后对消息进行处理。

###  1.1 `Handler`内存泄漏测试

执行如下代码，启动`Activity`后立马退出，销毁该`Activity`，如果没有`onDestroy()`方法中的处理，3秒后仍然能启动第二个`Activity`，引起内存泄漏。由此可见`onDestroy()`方法中的处理对避免内存泄漏是十分重要的！

```java
private Handler handler1 = new Handler(new Handler.Callback() {
  @Override
  public boolean handleMessage(@NonNull Message msg) {
    startActivity(new Intent(MainActivity.this, PersonalActivity.class));
    return false;
  }
});

private void test() {
  new Thread(new Runnable() {
    @Override
    public void run() {
      //1、Handler内存泄漏测试(假象)
      SystemClock.sleep(3000); //销毁Activity
      message.what = 3;
      if(handler1 != null) {
        handler1.sendMessage(message); //跳转到第二个界面
      }
      //handler1.sendMessageDelayed(message, 3000);
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
```

### 1.2 为什么不能在子线程创建`Handler`

从`Handler`源码中可以看出`Looper.myLooper();`方法拿不到子线程的`looper`，所以报异常。

```java
public static @Nullable Looper myLooper() {
  return sThreadLocal.get();
}

public Handler(@Nullable Callback callback, boolean async) {
  if (FIND_POTENTIAL_LEAKS) {
    final Class<? extends Handler> klass = getClass();
    if ((klass.isAnonymousClass() || klass.isMemberClass() || klass.isLocalClass()) &&
        (klass.getModifiers() & Modifier.STATIC) == 0) {
      Log.w(TAG, "The following Handler class should be static or leaks might occur: " +
            klass.getCanonicalName());
    }
  }

  //这里获得的looper为空
  mLooper = Looper.myLooper();
  if (mLooper == null) {
    throw new RuntimeException(
      "Can't create handler inside thread " + Thread.currentThread()
      + " that has not called Looper.prepare()");
  }
  mQueue = mLooper.mQueue;
  mCallback = callback;
  mAsynchronous = async;
}
```

不过可以通过如下两种方式在子线程中创建`Handler`:

```java
//方式一
new Thread(new Runnable() {
  @Override
  public void run() {
    Looper.prepare();
    Handler handler = new Handler() {
      @Override
      public void handleMessage(Message msg) {
        Toast.makeText(getApplicationContext(), "handler msg", Toast.LENGTH_LONG).show(); 
      }
    }
    handler.sendEmptyMessage(1);
    Looper.loop();
  }
}).start();

//方式二
new Thread(new Runnable() {
  @Override
  public void run() {
    Handler handler = new Handler(Looper.getMainLooper()) {
      @Override
      public void handleMessage(Message msg) {
        Toast.makeText(getApplicationContext(), "handler msg", Toast.LENGTH_LONG).show(); 
      }
    }
    handler.sendEmptyMessage(1);
  }
}).start();
```

### 1.3 `textView.setText()`只能在主线程执行?

这句话是错误的，`Veiw`的`setText()`方法虽然有线程检查的机制，但是当其运行地足够快，速度超过线程检查机制的执行时是可以在子线程执行成功的。

`TextView` --> `setText()` --> `checkForRelayout()` --> `requestLayout()` --> `mParent.requestLayout()` --> `ViewRootImpl.requestLayout()` --> `checkThread()` :

```java
void checkThread() {
  if (mThread != Thread.currentThread()) {
    throw new CalledFromWrongThreadException(
      "Only the original thread that created a view hierarchy can touch its views.");
  }
}
```

### 1.4 `new Handler()`两种写法有什么区别

两种`Handler`的写法如下：

```java
private Handler handler1 = new Handler(new Handler.Callback() {
  @Override
  public boolean handleMessage(@NonNull Message msg) {
    startActivity(new Intent(MainActivity.this, PersonalActivity.class));
    return false;
  }
});

//这是谷歌备胎的API，不推荐使用
private Handler handler2 = new Handler() {
  @Override
  public void handleMessage(@NonNull Message msg) {
    super.handleMessage(msg);
    textView.setText(msg.obj.toString());
  }
};
```

`ActivityThread` --> `main()` --> `Looper.loop()` --> `msg.target.dispatchMessage(msg)` --> `Handler.dispatchMessage()`: 

```java
public void dispatchMessage(@NonNull Message msg) {
  if (msg.callback != null) {
    handleCallback(msg);
  } else {
    if (mCallback != null) {
      if (mCallback.handleMessage(msg)) {
        return;
      }
    }
    handleMessage(msg);
  }
}
```

由上面源码可知优先使用`handler1`，实在没有办法了才使用`handler2`，`handler2`是谷歌备胎的`API`，不推荐使用。

### 1.5 `ThreadLocal`用法和原理

```java
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
```

## 二、`Handler`+`Message`原理分析

### 2.1 `Handler`、`Message`、`MessageQueue`以及`Looper`的关系

![image](https://github.com/tianyalu/NeHandlerSample/raw/master/show/handler_messagequeue_looper_relation.png)

### 2.2 `sendMessage`发送消息调用关系

![image](https://github.com/tianyalu/NeHandlerSample/raw/master/show/handler_send_message_invoke_relation.png)

```java
public final boolean sendEmptyMessage(int what);
public final boolean sendEmptyMessageDelayed(int what, long delayMillis);
public final boolean sendEmptyMessageAtTime(int what, long uptimeMillis);
public final boolean sendMessageDelayed(Message msg, long uptimeMillis);
public boolean sendMessageAtTime(Message msg, long uptimeMillis);
public final boolean sendMessageAtFrontOfQueue(Message msg);
//                       ↓
private boolean enqueueMessage(MessageQueue queue, Message msg, long uptimeMillis) {
  msg.target = this;
  if (mAsynchronous) {
    msg.setAsynchronous(true);
  }
  return queue.enqueueMessage(msg, uptimeMillis);
}
```

### 2.3 消息的分发与处理

`Looper.loop()` -->`handler.dispatchMessage()`

```java
public void dispatchMessage(@NonNull Message msg) {
  if (msg.callback != null) {
    handleCallback(msg);
  } else {
    if (mCallback != null) {
      if (mCallback.handleMessage(msg)) {
        return;
      }
    }
    handleMessage(msg);
  }
}

private static void handleCallback(Message message) {
  message.callback.run();
}
```

### 2.4 `Handler`原理分析

> 1. 每个`Thead`对应一个`Looper`；
> 2. 每个`Looper`只对应一个`MessageQueue`；
> 3. 每个`MessageQueue`中有`N`个`Message`；
> 4. 每个`Message`最多指定一个`Handler`来处理事件，一个线程可以有多个`handler`，但一个`handler`只能绑定一个线程；
> 5. `Looper`是属于某一个线程的，一个`Looper`只对应一个`MessageQueue`，判断这个`handleMessage()`方法在哪个线程上运行，就看这个`Handler`的`Looper`对象是在哪个线程，就在对应的线程上执行。

![image](https://github.com/tianyalu/NeHandlerSample/raw/master/show/handler_theory.png)

`Handler`流程形象示意图:

![image](https://github.com/tianyalu/NeHandlerSample/raw/master/show/handler_theory_process.png)

### 2.5 源码分析后的几个问题

#### 2.5.1 主线程里面的`Looper.prepare()/Looper.loop()`是一直在无限循环里面的吗？

是的

#### 2.5.2 为什么主线程用`Looper`死循环不会引发`ANR`异常？

因为在`Looper.next()`开启死循环的时候，一旦需要等待时或还没有执行到任务的时候，会调用`NDK`里面的`JNI`方法，释放当前时间片，这样就不会引发`ANR`异常了。

具体来说是利用`Linux`的`epoll`+`pipe`机制，使得主线程在阻塞的时候，让出`CPU`资源，同时等待新的消息。当我们对系统进行操作（包括滑动和点击）的时候，系统就会给主线程发送消息，这时候就会唤醒主线程（执行`onCreate`，`onResume`等方法），当处理完这个消息，就会再次进入阻塞状态，这样系统就能做到随时响应用户的操作。

#### 2.5.3 为什么`Handler`构造方法里面的`Looper`不是直接`new`的？

如果在`Handler`构造方法里面`new Looper`，就无法保证`Looper`唯一，只有用`Looper.prepare()`才能保证唯一性，具体可以看`prepare()`方法。

#### 2.5.4 `MessageQueue`为什么要放在`Looper`私有构造方法初始化？

因为一个线程只有绑定一个`Looper`，所以在`Looper`构造方法里面初始化就可以保证`mQueue`也是唯一的`Thread`对应一个`Looper`对应一个`mQueue`。

## 三、手写实现

### 3.1 `ActivityThread`

在`Java`测试单元下

```java
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
```

### 3.2 `Message`

```java
//消息对象
public class Message {

    //标识
    public int what;
    //消息内容
    public Object obj;
    //Handler对象
    public Handler target;

    public Message() {
    }

    public Message(Object obj) {
        this.obj = obj;
    }

    //模拟
    @NonNull
    @Override
    public String toString() {
        return obj.toString();
    }
}
```

### 3.3 `MessageQueue`

```java
//消息队列
public class MessageQueue {
    //阻塞队列
    BlockingQueue<Message> blockingQueue = new ArrayBlockingQueue<>(50);

    //将Message消息对象存入阻塞队列中
    public void enqueueMessage(Message message) {
        try {
            blockingQueue.put(message);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    //从消息队列中取出消息
    public Message next() {
        try {
            return blockingQueue.take();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return null;
    }
}
```

### 3.4 `Looper`

```java
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
```

### 3.5 `Handler`

```java
public class Handler {
    private Looper mLooper;
    private MessageQueue mQueue;

    public Handler() {
        mLooper = Looper.myLooper();
        if(mLooper == null) {
            throw new RuntimeException("Can't create handler inside thread " + Thread.currentThread()
                + " that has not called Looper.prepare()");
        }
        mQueue = mLooper.mQueue;
    }

    // 给开发者提供的开放API，用于重写和回调监听
    public void handleMessage(Message msg) {

    }

    public void sendMessage(Message message) {
        //将消息放入消息队列中
        enqueueMessage(message);

    }

    private void enqueueMessage(Message message) {
        //赋值当前handler
        message.target = this;

        //使用mQueue，将消息放入
        mQueue.enqueueMessage(message);
    }

    public void dispatchMessage(Message msg) {
        handleMessage(msg);
    }
}
```

