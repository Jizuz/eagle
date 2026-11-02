package com.eagle.demo.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * @RefreshScope + @Value 演示: 配置变更后Eagle自动触发RefreshScope重建，注入值实时更新（文档 6.4）
 */
@Component
@RefreshScope
public class RefreshDemoBean {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    @Value("${switch:false}")
    private Boolean switchFlag;

    @Value("${timeout:0}")
    private Integer timeout;

    @Value("${serviceName:default}")
    private String serviceName;

    public String snapshot() {
        return String.format("switch=%s, timeout=%s, serviceName=%s (Bean重建于 %s)",
                switchFlag, timeout, serviceName, LocalDateTime.now().format(FMT));
    }
}
