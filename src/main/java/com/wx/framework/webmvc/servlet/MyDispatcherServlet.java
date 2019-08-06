package com.wx.framework.webmvc.servlet;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.wx.demo.controller.TestController;
import com.wx.framework.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.URL;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: wangxiang
 * Date: 2019/8/6
 * To change this template use File | Settings | File Templates.
 */
public class MyDispatcherServlet extends HttpServlet {


    private static final Logger logger = LoggerFactory.getLogger(MyDispatcherServlet.class);
    private static Properties contextConfig = new Properties();
    private static List<String> classNames = new ArrayList();
    private static Map<String, Object> ioc = new HashMap();
    private static Map<String, Method> handlerMapper = new HashMap();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        /**6.等待请求*/
        try {
            doDispatch(req, resp);
        } catch (Exception e) {
            e.printStackTrace();
            resp.getWriter().write("500");
        }
    }

    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        String url = req.getRequestURI();
        String contextPath = req.getContextPath();
        url = url.replace(contextPath, "").replaceAll("/+", "/");
        if (!handlerMapper.containsKey(url)) {
            resp.getWriter().write("404");
            return;
        }
        Method method = handlerMapper.get(url);
        System.out.println(method.getDeclaringClass().getSimpleName());
        String controllerClassName = lowerFirstCase(method.getDeclaringClass().getSimpleName());
        Object[] paramValues = getMethodParamAndValue(req, resp, method);
        Object o = ioc.get(controllerClassName);
        Object object = method.invoke(ioc.get(controllerClassName), paramValues);
        /*object = JSONObject.toJSONString(object, SerializerFeature.WriteMapNullValue);*/

    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        /**启动阶段*/
        /**1.加载配置文件*/
        doLoadConfig(config.getInitParameter("contextConfigLocation"));
        /**2.扫描所有相关的类*/
        doScanner(contextConfig.getProperty("scanPackage"));
        /**3.初始化所有相关的类*/
        doInstance();

        /**4.自动注入*/
        doAutowried();
        logger.info("Spring 的核心初始化完成");
        /**5.初始化HandlerMapping*/
        initHandlerMapper();
        logger.info("Spring init......");
        /**6.等待请求*/
    }

    private void initHandlerMapper() {
        if (ioc.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            //扫描Controller中所有请求的方法
            Class<?> clazz = entry.getValue().getClass();
            if (!clazz.isAnnotationPresent(MyController.class)) {
                continue;
            }
            //如果类上有@RequestMapping注解
            String baseUrl = "";
            if (clazz.isAnnotationPresent(MyRequestMapping.class)) {
                MyRequestMapping requestMapping = clazz.getAnnotation(MyRequestMapping.class);
                baseUrl = requestMapping.value();
            }
            //扫描所有的公共方法
            for (Method method : clazz.getMethods()) {
                if (!method.isAnnotationPresent(MyRequestMapping.class)) {
                    continue;
                }
                MyRequestMapping requestMapping = method.getAnnotation(MyRequestMapping.class);
                String methodUrl = (baseUrl + requestMapping.value().replaceAll("/+", "/"));
                System.out.println(methodUrl);
                handlerMapper.put(methodUrl, method);
                logger.info("Mapping:" + methodUrl + ":" + method);
            }

        }
    }

    private void doAutowried() {
        if (ioc.isEmpty()) {
            return;
        }
        /**循环IOC中所有的类，对需要自动进行赋值的字段进行赋值*/
        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            //取出IOC容器中所有类的字段，
            Field[] fields = entry.getValue().getClass().getDeclaredFields();
            for (Field field : fields) {
                if (!field.isAnnotationPresent(MyAutowired.class)) {
                    continue;
                }
                //拿到字段上MyAutowired的注解
                MyAutowired autowired = field.getAnnotation(MyAutowired.class);
                //拿到这个用户自定义的名字
                String beanName = autowired.value().trim();
                if ("".equals(beanName)) {
                    beanName = field.getType().getName();
                }
                //暴力访问
                field.setAccessible(true);
                //然后就是将实例化的类赋值给属性的字段
                try {
                    field.set(entry.getValue(), ioc.get(beanName));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                    continue;
                }
            }
        }

    }

    /**
     * 实例化刚刚扫描到的类
     */
    private void doInstance() {
        if (classNames.isEmpty()) {
            return;
        }
        for (String className : classNames) {
            try {
                Class<?> clazz = Class.forName(className);
                /**不是所有的类都要实例化，只有加了Controller和Service注解的才会被实例化
                 * 所以这里需要判断类上有没有这样的注解
                 * */
                if (clazz.isAnnotationPresent(MyController.class)) {
                    /**key默认是类名首字母小写,如果自定义了名字，优先使用自定义的名字*/
                    String beanName = clazz.getName();
                    String[] split = beanName.split("\\.");
                    String s = split[split.length - 1];

                    String key = lowerFirstCase(s);
                    ioc.put(key, clazz.newInstance());
                } else if (clazz.isAnnotationPresent(MyService.class)) {
                    /**自动注入Service的接口的时候，接口不能被实例化
                     * 所以需要实例化实现类，然后赋值给接口
                     * */
                    //第二种情况使用自定义的值
                    Annotation myService = clazz.getAnnotation(MyService.class);
                    String beanName = ((MyService) myService).value();
                    //第一种情况，使用默认的首字母小写
                    if ("".equals(beanName.trim())) {
                        //用户没有设置默认名，那就默认是类名首字母小写
                        String[] split = clazz.getName().split("\\.");
                        String s = split[split.length - 1];
                        beanName = lowerFirstCase(s);
                    }
                    Object instance = clazz.newInstance();
                    ioc.put(beanName, instance);
                    //第三种情况，根据接口的类型来赋值
                    for (Class<?> i : clazz.getInterfaces()) {
                        ioc.put(i.getName(), instance);
                    }

                } else {
                    continue;
                }

            } catch (Exception e) {
                logger.error("实例化出错{}", e);
            }

        }

    }

    private void doScanner(String scanPackage) {
        URL url = this.getClass().getClassLoader()
                .getResource("/" + scanPackage.replaceAll("\\.", "/"));
        File classDir = new File(url.getFile());
        for (File file : classDir.listFiles()) {
            if (file.isDirectory()) {
                doScanner(scanPackage + "." + file.getName());
            } else {
                //完成的类名
                String className = scanPackage + "." + file.getName().replaceAll(".class", "");
                classNames.add(className);
            }
        }

    }

    private void doLoadConfig(String contextConfigLocation) {
        InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream(contextConfigLocation);
        try {
            contextConfig.load(inputStream);

        } catch (IOException e) {
            logger.error("加载配置异常{}", e);
            e.printStackTrace();
        } finally {
            if (null != inputStream) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    logger.error("关闭资源异常异常{}", e);
                    e.printStackTrace();
                }
            }
        }
    }

    private String lowerFirstCase(String str) {
        char[] chars = str.toCharArray();
        System.out.println(str);
        chars[0] = +32;
        System.out.println(chars[0]);
        return String.valueOf(chars);
    }

    /**
     * 参数解析
     *
     * @param request
     * @param response
     * @param method
     * @return
     */
    private Object[] getMethodParamAndValue(HttpServletRequest request, HttpServletResponse response, Method method) {
        Parameter[] parameters = method.getParameters();
        Object[] paramValues = new Object[parameters.length];
        for (int i = 0; i < parameters.length; i++) {

            if (ServletRequest.class.isAssignableFrom(parameters[i].getType())) {
                paramValues[i] = request;
            } else if (ServletResponse.class.isAssignableFrom(parameters[i].getType())) {
                paramValues[i] = response;
            } else {
                String bindingValue = parameters[i].getName();
                if (parameters[i].isAnnotationPresent(MyRequestParam.class)) {
                    bindingValue = parameters[i].getAnnotation(MyRequestParam.class).value();
                }
                String paramValue = request.getParameter(bindingValue);
                paramValues[i] = paramValue;
                if (paramValue != null) {
                    if (Integer.class.isAssignableFrom(parameters[i].getType())) {
                        paramValues[i] = Integer.parseInt(paramValue);
                    } else if (Float.class.isAssignableFrom(parameters[i].getType())) {
                        paramValues[i] = Float.parseFloat(paramValue);
                    } else if (Double.class.isAssignableFrom(parameters[i].getType())) {
                        paramValues[i] = Double.parseDouble(paramValue);
                    } else if (Long.class.isAssignableFrom(parameters[i].getType())) {
                        paramValues[i] = Long.parseLong(paramValue);
                    } else if (Boolean.class.isAssignableFrom(parameters[i].getType())) {
                        paramValues[i] = Boolean.parseBoolean(paramValue);
                    }
                }
            }
        }
        return paramValues;
    }

}
