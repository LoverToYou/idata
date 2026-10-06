package com.idata.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 前端路由（SPA）深链回退控制器。
 *
 * <p>本应用的前端静态资源与后端接口同处 {@code /api} 上下文下，像 {@code /datasource/create}
 * 这类前端路由会与后端 {@code POST /datasource/create} 接口路径冲突：GET 请求按路径匹配到
 * 控制器后被判为「方法不支持」，无法回落到静态资源。这里为前端深链显式注册 GET 转发，
 * 直接返回 SPA 入口 index.html，保证刷新/直接访问不会 500。
 */
@Controller
public class SpaRouteController {

    @GetMapping({
            // 数据集成
            "/datasource/create",
            "/datasource/{id:\\d+}/edit",
            "/datasource/{id:\\d+}/hive",
            "/datax-task/create",
            "/datax-task/{id:\\d+}/edit",
            "/files",
            // 任务开发
            "/sql-task",
            "/python-script",
            "/udf",
            "/parameter",
            // 调度运维
            "/workflow/create",
            "/workflow/{id:\\d+}/edit",
            "/schedule",
            "/monitor",
            // 数据应用
            "/report/create",
            "/report/{id:\\d+}/edit",
            "/report/{id:\\d+}/view",
            "/dashboards/create",
            "/dashboards/{id:\\d+}/edit",
            "/dashboards/{id:\\d+}/view",
            // 数据安全
            "/masking-rule"
    })
    public String forwardToIndex() {
        return "forward:/index.html";
    }
}
