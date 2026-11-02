package com.eagle.admin.service.impl;

import com.eagle.admin.service.AlertNotifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 默认告警实现: ERROR级别日志输出
 */
@Slf4j
@Component
public class LogAlertNotifier implements AlertNotifier {

    @Override
    public void alert(String message) {
        log.error("[Eagle告警] {}", message);
    }
}
