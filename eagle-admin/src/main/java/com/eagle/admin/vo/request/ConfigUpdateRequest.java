package com.eagle.admin.vo.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 编辑配置请求（允许修改配置类型，但值必须与新类型匹配）
 */
@Data
public class ConfigUpdateRequest {

    @NotBlank(message = "valueType不能为空")
    @Pattern(regexp = "^(string|number|boolean)$", message = "valueType仅支持 string / number / boolean")
    private String valueType;

    /** string类型允许空字符串，但不允许null */
    @NotNull(message = "configValue不能为空(string类型允许空字符串)")
    @Size(max = 10000, message = "配置值长度不能超过10000")
    private String configValue;

    /** 为null时保持原描述不变 */
    @Size(max = 512, message = "配置描述长度不能超过512")
    private String description;

    @Size(max = 64, message = "操作人长度不能超过64")
    private String operator;
}
