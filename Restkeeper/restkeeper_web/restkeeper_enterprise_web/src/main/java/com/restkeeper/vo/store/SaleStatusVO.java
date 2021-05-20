package com.restkeeper.vo.store;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

@Data
public class SaleStatusVO{

    @ApiModelProperty(value = "售卖状态")
    private Integer status; // 0 停售 1 启售

    @ApiModelProperty(value = "菜品id列表")
    private List<String> ids;
}
