package com.springboot.catdemo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * <p>
 *
 * </p>
 *
 * @author CAN
 * @since 2022-06-15
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("sys_exclude_inter_url")
@ApiModel(value = "ExcludeInterUrl对象", description = "排除拦截的URL")
public class ExcludeInterUrl implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty("主键")
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @ApiModelProperty("放行的url名称")
    @TableField("exclude_inter_name")
    private String excludeInterName;

    @ApiModelProperty("放行的url")
    @TableField("exclude_inter_url")
    private String excludeInterUrl;

    @ApiModelProperty("url类型")
    @TableField("exclude_inter_type")
    private Integer excludeInterType;


}
