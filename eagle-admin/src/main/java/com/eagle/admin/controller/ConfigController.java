package com.eagle.admin.controller;

import com.eagle.admin.service.ConfigService;
import com.eagle.admin.service.PublishService;
import com.eagle.admin.vo.request.ConfigQueryRequest;
import com.eagle.admin.vo.request.ConfigSaveRequest;
import com.eagle.admin.vo.request.ConfigUpdateRequest;
import com.eagle.admin.vo.response.ConfigVO;
import com.eagle.admin.vo.response.HistoryVO;
import com.eagle.admin.vo.response.PageResult;
import com.eagle.admin.vo.response.PreviewVO;
import com.eagle.common.result.Result;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 配置管理REST API
 */
@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private final ConfigService configService;
    private final PublishService publishService;

    public ConfigController(ConfigService configService, PublishService publishService) {
        this.configService = configService;
        this.publishService = publishService;
    }

    /**
     * 新增配置（校验类型 → DB事务 → ZK推送，见文档 4.4）
     */
    @PostMapping
    public Result<ConfigVO> save(@Validated @RequestBody ConfigSaveRequest req) {
        return Result.ok(publishService.saveAndPublish(req));
    }

    /**
     * 编辑配置（允许修改类型，值强制匹配新类型）
     */
    @PutMapping("/{id}")
    public Result<ConfigVO> update(@PathVariable("id") Long id,
                                   @Validated @RequestBody ConfigUpdateRequest req) {
        return Result.ok(publishService.updateAndPublish(id, req));
    }

    /**
     * 删除配置（软删 + DELETE版本快照 + ZK节点删除）
     */
    @DeleteMapping("/{id}")
    public Result<ConfigVO> delete(@PathVariable("id") Long id,
                                   @RequestParam(value = "operator", defaultValue = "admin") String operator) {
        return Result.ok(publishService.deleteAndPublish(id, operator));
    }

    /**
     * 回滚到指定历史版本
     */
    @PostMapping("/{id}/rollback/{historyId}")
    public Result<ConfigVO> rollback(@PathVariable("id") Long id,
                                     @PathVariable("historyId") Long historyId,
                                     @RequestParam(value = "operator", defaultValue = "admin") String operator) {
        return Result.ok(publishService.rollback(id, historyId, operator));
    }

    /**
     * 配置分页查询
     */
    @GetMapping("/page")
    public Result<PageResult<ConfigVO>> page(ConfigQueryRequest req) {
        return Result.ok(configService.page(req));
    }

    /**
     * 配置详情
     */
    @GetMapping("/{id}")
    public Result<ConfigVO> detail(@PathVariable("id") Long id) {
        return Result.ok(configService.detail(id));
    }

    /**
     * 发布预览（ZK路径 + JSON内容）
     */
    @GetMapping("/{id}/preview")
    public Result<PreviewVO> preview(@PathVariable("id") Long id) {
        return Result.ok(configService.preview(id));
    }

    /**
     * 版本历史列表
     */
    @GetMapping("/{id}/histories")
    public Result<List<HistoryVO>> histories(@PathVariable("id") Long id) {
        return Result.ok(configService.histories(id));
    }
}
