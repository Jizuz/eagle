package com.eagle.admin.service;

/**
 * 告警通知接口: ZK同步失败等需要人工介入的场景
 * <p>默认实现为日志告警(LogAlertNotifier)，可扩展为邮件/短信/钉钉等</p>
 */
public interface AlertNotifier {

    /**
     * 发送告警
     */
    void alert(String message);
}
