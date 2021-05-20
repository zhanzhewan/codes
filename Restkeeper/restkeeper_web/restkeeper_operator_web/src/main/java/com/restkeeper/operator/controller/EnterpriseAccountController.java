package com.restkeeper.operator.controller;

import com.restkeeper.operator.entity.EnterpriseAccount;
import com.restkeeper.operator.service.IEnterpriseAccountService;
import com.restkeeper.operator.vo.AddEnterpriseAccountVO;
import com.restkeeper.operator.vo.ResetPwdVO;
import com.restkeeper.operator.vo.UpdateEnterpriseAccountVO;
import com.restkeeper.response.vo.PageVO;
import com.restkeeper.utils.AccountStatus;
import com.restkeeper.utils.Result;
import com.restkeeper.utils.ResultCode;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.dubbo.config.annotation.Reference;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@Api(tags = {"企业帐号管理"})
@RestController
@RequestMapping("/enterprise")
public class EnterpriseAccountController {

    @Reference(version = "1.0.0",check = false)
    private IEnterpriseAccountService enterpriseAccountService;

    @ApiOperation("查询企业帐号列表")
    @GetMapping("/pageList/{page}/{pageSize}")
    public PageVO<EnterpriseAccount> findListByPage(@PathVariable("page") int page,
                                                    @PathVariable("pageSize") int pageSize,
                                                    @RequestParam(value = "enterpriseName",required = false) String enterpriseName){
        return new PageVO<EnterpriseAccount>(enterpriseAccountService.queryPageByName(page, pageSize, enterpriseName));
    }

    @ApiOperation("新增帐号")
    @PostMapping("/add")
    public boolean add(@RequestBody AddEnterpriseAccountVO addEnterpriseAccountVO){

        //bean拷贝
        EnterpriseAccount enterpriseAccount = new EnterpriseAccount();
        BeanUtils.copyProperties(addEnterpriseAccountVO,enterpriseAccount);

        //设置时间
        LocalDateTime localDateTime = LocalDateTime.now();
        enterpriseAccount.setApplicationTime(localDateTime);

        //设置过期时间
        LocalDateTime expireTime = null;
        //试用期默认为7天
        if (addEnterpriseAccountVO.getStatus() == 0){
            //试用帐号
            expireTime = localDateTime.plusDays(7);
        }
        if (addEnterpriseAccountVO.getStatus() == 1){
            //设置到期时间
            expireTime = localDateTime.plusDays(addEnterpriseAccountVO.getValidityDay());
        }

        if (expireTime != null){
            enterpriseAccount.setExpireTime(expireTime);
        }else{
            throw new RuntimeException("帐号类型信息设置有误");
        }

        return enterpriseAccountService.add(enterpriseAccount);
    }

    //根据id查询帐号信息
    @ApiOperation("帐号查询")
    @GetMapping("/getById/{id}")
    public EnterpriseAccount getById(@PathVariable("id") String id){
        return enterpriseAccountService.getById(id);
    }

    //帐号编辑
    @ApiOperation("帐号编辑")
    @PutMapping("/update")
    public Result update(@RequestBody UpdateEnterpriseAccountVO updateEnterpriseAccountVO){

        Result result = new Result();

        //查询原有企业账户信息
        EnterpriseAccount enterpriseAccount = enterpriseAccountService.getById(updateEnterpriseAccountVO.getEnterpriseId());
        if (enterpriseAccount == null){
            result.setStatus(ResultCode.error);
            result.setDesc("修改账户不存在");
            return result;
        }

        //修改状态信息
        if (updateEnterpriseAccountVO.getStatus() != null){

            //正式期不能修改为试用期
            if (updateEnterpriseAccountVO.getStatus() ==0 && enterpriseAccount.getStatus() ==1){
                result.setStatus(ResultCode.error);
                result.setDesc("不能将正式帐号修改为试用帐号");
                return result;
            }

            //试用改为正式
            if (updateEnterpriseAccountVO.getStatus() == 1 && enterpriseAccount.getStatus() ==0){
                //到期时间
                LocalDateTime localDateTime = LocalDateTime.now();
                //到期时间
                LocalDateTime expireTime = localDateTime.plusDays(updateEnterpriseAccountVO.getValidityDay());
                enterpriseAccount.setApplicationTime(localDateTime);
                enterpriseAccount.setExpireTime(expireTime);
            }

            //正式添加延期
            if (updateEnterpriseAccountVO.getStatus() ==1 && enterpriseAccount.getStatus() == 1){
                LocalDateTime now = LocalDateTime.now();
                //设置到期时间
                LocalDateTime expireTime = now.plusDays(updateEnterpriseAccountVO.getValidityDay());
                enterpriseAccount.setExpireTime(expireTime);
            }
        }

        //其他字段，bean拷贝
        BeanUtils.copyProperties(updateEnterpriseAccountVO,enterpriseAccount);

        //执行修改
        boolean flag = enterpriseAccountService.updateById(enterpriseAccount);
        if (flag){
            //修改成功
            result.setStatus(ResultCode.success);
            result.setDesc("修改成功");
            return result;
        }else{
            //修改失败
            result.setStatus(ResultCode.error);
            result.setDesc("修改失败");
            return result;
        }
    }

    //帐号删除
    @ApiOperation("帐号删除")
    @DeleteMapping("/deleteById/{id}")
    public boolean deleteById(@PathVariable("id") String id){
        return enterpriseAccountService.removeById(id);
    }

    //帐号还原
    @ApiOperation("帐号还原")
    @PutMapping("/recovery/{id}")
    public boolean recovery(@PathVariable("id") String id){
        return enterpriseAccountService.recovery(id);
    }

    //帐号禁用
    @ApiOperation("帐号禁用")
    @PutMapping("/forbidden/{id}")
    public boolean forbidden(@PathVariable("id") String id){

        //查询原有账户信息
        EnterpriseAccount enterpriseAccount = enterpriseAccountService.getById(id);
        enterpriseAccount.setStatus(AccountStatus.Forbidden.getStatus());
        return enterpriseAccountService.updateById(enterpriseAccount);
    }

    //重置密码
    @ApiOperation("重置密码")
    @PutMapping("/resetPwd")
    public boolean resetPwd(@RequestBody ResetPwdVO resetPwdVO){
        return enterpriseAccountService.restPwd(resetPwdVO.getId(),resetPwdVO.getPwd());
    }

}
