package com.eagle.admin.vo.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新增配置请求
 */
@Data
public class ConfigSaveRequest {

    @NotBlank(message = "env不能为空")
    @Pattern(regexp = "^(dev|test|prod)$", message = "env仅支持 dev / test / prod")
    private String env;

    @NotBlank(message = "groupId不能为空")
    @Pattern(regexp = "^[A-Za-z0-9._-]{1,64}$", message = "groupId仅允许字母、数字、点、下划线、中划线，长度1-64")
    private String groupId;

    @NotBlank(message = "configKey不能为空")
    @Pattern(regexp = "^[A-Za-z0-9._-]{1,128}$", message = "configKey仅允许字母、数字、点、下划线、中划线，长度1-128")
    private String configKey;

    @NotBlank(message = "valueType不能为空")
    @Pattern(regexp = "^(string|number|boolean)$", message = "valueType仅支持 string / number / boolean")
    private String valueType;

    /** string类型允许空字符串，但不允许null */
    @NotNull(message = "configValue不能为空(string类型允许空字符串)")
    @Size(max = 10000, message = "配置值长度不能超过10000")
    private String configValue;

    @Size(max = 512, message = "配置描述长度不能超过512")
    private String description;

    @Size(max = 64, message = "操作人长度不能超过64")
    private String operator;
}
