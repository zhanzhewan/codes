package com.restkeeper.vo.shop;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

@Data
public class StoreManagerVO {

    @ApiModelProperty(value = "门店管理员id")
    private String storeManagerId;

    @ApiModelProperty(value = "名称")
    private String name;

    @ApiModelProperty(value = "手机号")
    private String phone;

    @ApiModelProperty(value = "门店id列表")
    private List<String> storeIds;

}
