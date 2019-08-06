package com.wx.demo.controller;

import com.wx.demo.service.DemoService;
import com.wx.framework.annotation.MyAutowired;
import com.wx.framework.annotation.MyController;
import com.wx.framework.annotation.MyRequestMapping;
import com.wx.framework.annotation.MyRequestParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Created by IntelliJ IDEA.
 * User: wangxiang
 * Date: 2019/8/6
 * To change this template use File | Settings | File Templates.
 */
@MyController
@MyRequestMapping("/demo")
public class TestController {
    private static final Logger logger = LoggerFactory.getLogger(TestController.class);

    @MyAutowired
    private DemoService demoService;


    @MyRequestMapping("/query")
    public void query(HttpServletRequest request, HttpServletResponse response,
                      @MyRequestParam("name") String name) {
        String result = demoService.query(name);
        try {
            response.getWriter().write(result);
        } catch (IOException e) {
            logger.error("查询出错{}", e);
        }
    }

    @MyRequestMapping("/add")
    public void add(HttpServletRequest request, HttpServletResponse response) {
      /*  String result = demoService.query(name);
        try {
            response.getWriter().write(result);
        } catch (IOException e) {
            logger.error("查询出错{}", e);
        }*/
    }
}
