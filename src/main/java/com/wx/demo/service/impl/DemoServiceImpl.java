package com.wx.demo.service.impl;

import com.wx.demo.service.DemoService;
import com.wx.framework.annotation.MyService;

/**
 * Created by IntelliJ IDEA.
 * User: wangxiang
 * Date: 2019/8/6
 * To change this template use File | Settings | File Templates.
 */
@MyService
public class DemoServiceImpl implements DemoService {
    @Override
    public String query(String name) {
        return "I'm Iron Man:"+name;
    }
}
