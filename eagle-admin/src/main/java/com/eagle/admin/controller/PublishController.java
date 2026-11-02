package com.eagle.admin.controller;

import com.eagle.admin.service.PublishService;
import com.eagle.admin.vo.request.PublishQueryRequest;
import com.eagle.admin.vo.response.PageResult;
import com.eagle.admin.vo.response.PublishVO;
import com.eagle.common.result.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 发布记录REST API
 */
@RestController
@RequestMapping("/api/publish")
public class PublishController {

    private final PublishService publishService;

    public PublishController(PublishService publishService) {
        this.publishService = publishService;
    }

    /**
     * 发布记录分页查询（可按状态筛选失败记录）
     */
    @GetMapping("/page")
    public Result<PageResult<PublishVO>> page(PublishQueryRequest req) {
        return Result.ok(publishService.page(req));
    }

    /**
     * 发布失败手动重试
     */
    @PostMapping("/{id}/retry")
    public Result<PublishVO> retry(@PathVariable("id") Long id) {
        return Result.ok(publishService.retry(id));
    }
}
