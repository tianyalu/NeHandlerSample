package com.sty.ne.handlersample.core;

import androidx.annotation.NonNull;

/**
 * @Author: tian
 * @UpdateDate: 2020-08-18 19:52
 */
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
