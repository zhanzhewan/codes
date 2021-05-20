package com.restkeeper.job;

import com.restkeeper.service.OrderHistoryService;
import com.restkeeper.service.ReportDishService;
import com.restkeeper.service.ReportPayService;
import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@Slf4j
public class ReportDataGenerateJob {

    @Autowired
    private ReportPayService reportPayService;

    @Autowired
    private ReportDishService reportDishService;

    @Autowired
    private OrderHistoryService orderHistoryService;

    @XxlJob("generateReportHandler")//必须的，注解参数需要和在xxl-job-admin里创建的jobHandler的名称一致
    public ReturnT<String> jobHandler(String param){

        //统计历史的营收概况
        reportPayService.generateData();
        System.out.println(LocalDateTime.now()+"**************历史汇总成功**************");

        //统计历史菜品销量
        reportDishService.generateData();
        System.out.println(LocalDateTime.now()+"**************历史菜品销量汇总成功**************");

        //订单数据迁移
        orderHistoryService.exportToHistory();
        System.out.println(LocalDateTime.now()+"**************job执行成功**************");

        return ReturnT.SUCCESS;



    }
}
