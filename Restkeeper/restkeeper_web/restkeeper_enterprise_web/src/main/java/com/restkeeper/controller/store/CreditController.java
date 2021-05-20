package com.restkeeper.controller.store;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.google.common.collect.Lists;
import com.restkeeper.constants.SystemCode;
import com.restkeeper.exception.BussinessException;
import com.restkeeper.response.vo.PageVO;
import com.restkeeper.store.entity.Credit;
import com.restkeeper.store.entity.CreditCompanyUser;
import com.restkeeper.store.entity.CreditLogs;
import com.restkeeper.store.entity.CreditRepayment;
import com.restkeeper.store.service.ICreditLogService;
import com.restkeeper.store.service.ICreditRepaymentService;
import com.restkeeper.store.service.ICreditService;
import com.restkeeper.utils.BeanListUtils;
import com.restkeeper.vo.store.CreditLogExcelVO;
import com.restkeeper.vo.store.CreditRepaymentVO;
import com.restkeeper.vo.store.CreditVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.Reference;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.net.URLEncoder;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Api(tags = { "挂账管理" })
@RestController
@RequestMapping("/credit")
public class CreditController {

    @Reference(version = "1.0.0",check = false)
    private ICreditService creditService;

    @Reference(version = "1.0.0",check = false)
    private ICreditLogService creditLogService;

    @ApiOperation("新增挂账")
    @PostMapping("/add")
    public boolean add(@RequestBody CreditVO creditVO){

        //creditvo -> credit
        Credit credit = new Credit();
        BeanUtils.copyProperties(creditVO,credit,"users");

        if (creditVO.getUsers()!=null && !creditVO.getUsers().isEmpty()){
            // type = 公司
            //List<CreditCompanyUserVO> users -> List<CreditCompanyUser> users;
            List<CreditCompanyUser> companyUsers = Lists.newArrayList();
            creditVO.getUsers().forEach(d->{

                CreditCompanyUser creditCompanyUser = new CreditCompanyUser();

                BeanUtils.copyProperties(d,creditCompanyUser);

                companyUsers.add(creditCompanyUser);
            });
            return creditService.add(credit,companyUsers);

        }

        //type = 个人
        return creditService.add(credit,null);

    }

    @ApiOperation("挂账管理列表")
    @GetMapping("/pageList/{page}/{pageSize}")
    public PageVO<CreditVO> pageList(@PathVariable int page,@PathVariable int pageSize,@RequestParam(value = "name",defaultValue = "",required = false) String name){

        IPage<Credit> creditIPage = creditService.queryPage(page, pageSize, name);

        List<CreditVO> voList = Lists.newArrayList();

        try {
            voList = BeanListUtils.copy(creditIPage.getRecords(),CreditVO.class);
        } catch (Exception e) {
            throw new BussinessException("集合转换出错");
        }

        return  new PageVO<CreditVO>(creditIPage,voList);
    }

    @ApiOperation("根据id查询挂账信息")
    @GetMapping("/{id}")
    public CreditVO getCredit(@PathVariable String id){
        CreditVO creditVO = new CreditVO();

        Credit credit = creditService.queryById(id);
        BeanUtils.copyProperties(credit,creditVO);

        return creditVO;
    }

    @ApiOperation(value = "修改挂账")
    @PutMapping("/update/{id}")
    public boolean updateCredit(@PathVariable String id,@RequestBody CreditVO creditvo) {
        //CreditVO->转化成 Credit
        Credit credit = creditService.queryById(id);
        BeanUtils.copyProperties(creditvo,credit,"users");

        if (creditvo.getUsers()!=null && !creditvo.getUsers().isEmpty()){

            // List<CreditCompanyUserVO> 转换成 List<CreditCompanyUser>
            List<CreditCompanyUser> companyUsers = Lists.newArrayList();
            creditvo.getUsers().forEach(d->{

                CreditCompanyUser creditCompany = new CreditCompanyUser();
                BeanUtils.copyProperties(d,creditCompany);
                companyUsers.add(creditCompany);
            });
            return creditService.updateInfo(credit,companyUsers);
        }

        return creditService.updateInfo(credit,null);
    }

    /**
     * 挂账明细列表
     * @param creditId
     * @param page
     * @param pageSize
     * @return
     */
    @ApiOperation(value = "挂账订单明细列表")
    @GetMapping("/creditLog/{page}/{pageSize}/{creditId}")
    public PageVO<CreditLogs> getCreditLogPageList(@RequestParam(value = "creditId") String creditId, @PathVariable int page, @PathVariable int pageSize){
        return new PageVO<CreditLogs>(creditLogService.queryPage(page,pageSize,creditId));
    }

    //excel文件的生成与导出
    @GetMapping("/export/creditId/{creditId}/start/{start}/end/{end}")
    public void export(HttpServletResponse response, @PathVariable String creditId, @PathVariable String start, @PathVariable String end) throws Exception{

        //时间格式的转换
        LocalDateTime startTime = LocalDateTime.parse(start);
        LocalDateTime endTime = LocalDateTime.parse(end);

        if (endTime.isBefore(startTime)){
            throw new BussinessException("结束时间不能比开始时间小");
        }

        //设置具体的数据信息
        List<CreditLogExcelVO> data = creditLogService.list(creditId, startTime, endTime)
                .stream()
                .map(c -> {

                    //实体类信息的转换
                    CreditLogExcelVO creditLogExcelVO = new CreditLogExcelVO();
                    creditLogExcelVO.setCreditAmount(c.getCreditAmount());
                    creditLogExcelVO.setDateTime(Date.from(c.getLastUpdateTime().atZone(ZoneId.systemDefault()).toInstant()));
                    creditLogExcelVO.setOrderAmount(c.getOrderAmount());
                    creditLogExcelVO.setRevenueAmount(c.getReceivedAmount());
                    creditLogExcelVO.setUserName(c.getUserName());
                    creditLogExcelVO.setOrderId(c.getOrderId());
                    if (c.getType() == SystemCode.CREDIT_TYPE_COMPANY) {
                        creditLogExcelVO.setCreditType("企业");
                    } else {
                        creditLogExcelVO.setCreditType("个人");
                    }
                    return creditLogExcelVO;
                }).collect(Collectors.toList());

        //设置相关的头信息，最终完成下载的操作
        response.setContentType("application/vnd.ms-excel");
        response.setCharacterEncoding("utf-8");
        String fileName = URLEncoder.encode("demo","UTF-8");
        response.setHeader("Content-disposition","attachment;filename="+fileName+".xlsx");
        //向excel文件中来写入相关数据
        EasyExcel.write(response.getOutputStream(),CreditLogExcelVO.class).sheet("模版").doWrite(data);

    }

    @Reference(version = "1.0.0", check=false)
    private ICreditRepaymentService creditRepaymentService;

    @ApiOperation(value = "还款")
    @PostMapping("/repayment")
    public boolean repayment(@RequestBody CreditRepaymentVO creditRepaymentVo){
        CreditRepayment creditRepayment =new CreditRepayment();
        BeanUtils.copyProperties(creditRepaymentVo,creditRepayment);
        return creditRepaymentService.repayment(creditRepayment);
    }


}
