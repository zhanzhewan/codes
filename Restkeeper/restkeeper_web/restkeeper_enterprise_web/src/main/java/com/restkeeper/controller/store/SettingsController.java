package com.restkeeper.controller.store;

import com.restkeeper.store.entity.Remark;
import com.restkeeper.store.service.IRemarkService;
import com.restkeeper.vo.store.RemarkVO;
import com.restkeeper.vo.store.SettingsVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.apache.dubbo.config.annotation.Reference;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Api(tags = { "门店备注管理" })
@RestController
@RequestMapping("/settings")
public class SettingsController {

    @Reference(version = "1.0.0",check = false)
    private IRemarkService remarkService;

    @ApiOperation(value = "获取门店设置信息")
    @GetMapping("/getSysSettings")
    public SettingsVO getSysSettings(){

        List<Remark> remarks = remarkService.getRemarks();

        List<RemarkVO> remarkVOS = new ArrayList<>();

        remarks.forEach(d->{

            RemarkVO remarkVO = new RemarkVO();

            remarkVO.setRemarkName(d.getRemarkName());

            //value
            //remarkValue : "[店庆，节假日]"
            String remarkValue = d.getRemarkValue();
            String remarkValue_substring = remarkValue.substring(remarkValue.indexOf("[") + 1, remarkValue.indexOf("]"));

            //remarkValue_substring : 店庆，节假日
            if (StringUtils.isNotEmpty(remarkValue_substring)){
                String[] remark_array = remarkValue_substring.split(",");
                remarkVO.setRemarkValue(Arrays.asList(remark_array));
            }

            remarkVOS.add(remarkVO);
        });

        SettingsVO settingsVO = new SettingsVO();
        settingsVO.setRemarks(remarkVOS);

        return settingsVO;
    }

    @ApiOperation("修改门店备注")
    @PutMapping("/update")
    public boolean update(@RequestBody SettingsVO settingsVO){

        List<Remark> remarks = new ArrayList<>();
        List<RemarkVO> remarkVOList = settingsVO.getRemarks();

        remarkVOList.forEach(remarkVO -> {

            Remark remark = new Remark();

            remark.setRemarkName(remarkVO.getRemarkName());
            remark.setRemarkValue(remarkVO.getRemarkValue().toString());

            remarks.add(remark);
        });

        return remarkService.updateRemarks(remarks);
    }
}
