package com.eagle.admin.vo.response;

import lombok.Data;

/**
 * 发布预览视图: 即将写入ZK的节点路径与JSON内容
 */
@Data
public class PreviewVO {

    /** ZK节点路径: /config/{env}/{group}/{key} */
    private String zkPath;

    /** 即将写入ZK的标准JSON: {"type":"...","value":"..."} */
    private String content;
}
