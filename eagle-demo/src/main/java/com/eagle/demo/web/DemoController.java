package com.eagle.demo.web;

import com.eagle.client.core.EagleConfigClient;
import com.eagle.common.dto.ConfigChangeEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 演示接口:
 * <ul>
 *     <li>GET /demo/config  — SDK泛型取值 + 降级状态（文档 6.4 使用示例）</li>
 *     <li>GET /demo/value   — @Value + @RefreshScope 注入值实时刷新</li>
 *     <li>GET /demo/changes — 最近收到的变更事件</li>
 * </ul>
 */
@Slf4j
@RestController
public class DemoController {

    private final EagleConfigClient configClient;
    private final RefreshDemoBean refreshDemoBean;
    private final List<String> recentChanges = new CopyOnWriteArrayList<>();

    public DemoController(EagleConfigClient configClient, RefreshDemoBean refreshDemoBean) {
        this.configClient = configClient;
        this.refreshDemoBean = refreshDemoBean;
        configClient.addChangeListener(this::onChange);
    }

    private void onChange(ConfigChangeEvent event) {
        String record = String.format("[%s] %s : %s -> %s",
                event.getChangeType(), event.getKey(), event.getOldNode(), event.getNewNode());
        recentChanges.add(record);
        if (recentChanges.size() > 50) {
            recentChanges.remove(0);
        }
        log.info("[Demo] 收到配置变更事件: {}", record);
    }

    /**
     * SDK泛型取值演示（文档 6.4）
     */
    @GetMapping("/demo/config")
    public Map<String, Object> config() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("degraded", configClient.isDegraded());
        result.put("switch(Boolean)", configClient.getConfig("switch", Boolean.class, null));
        result.put("timeout(Integer)", configClient.getConfig("timeout", Integer.class, null));
        result.put("serviceName(String)", configClient.getConfig("serviceName", String.class, null));
        result.put("allConfigs", configClient.getAllConfigs());
        return result;
    }

    /**
     * @Value + @RefreshScope 注入值演示
     */
    @GetMapping("/demo/value")
    public Map<String, Object> value() {
        return Map.of("message", refreshDemoBean.snapshot());
    }

    /**
     * 最近收到的变更事件（保留最近50条）
     */
    @GetMapping("/demo/changes")
    public List<String> changes() {
        return List.copyOf(recentChanges);
    }
}
