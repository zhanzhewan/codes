package com.restkeeper.operator.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.google.common.collect.Maps;
import com.restkeeper.operator.entity.OperatorUser;
import com.restkeeper.operator.mapper.OperatorUserMapper;
import com.restkeeper.utils.JWTUtil;
import com.restkeeper.utils.MD5CryptUtil;
import com.restkeeper.utils.Result;
import com.restkeeper.utils.ResultCode;
import org.apache.commons.codec.digest.Md5Crypt;
import org.apache.commons.lang.StringUtils;
import org.apache.dubbo.config.annotation.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;

import java.util.Map;

//@Service("operatorUserService")
@Service(version = "1.0.0",protocol = "dubbo")
@RefreshScope
public class OperatorUserServiceImpl extends ServiceImpl<OperatorUserMapper, OperatorUser> implements IOperatorUserService{

    @Value("${gateway.secret}")
    private String secret;


    //根据name进行分页数据查询
    @Override
    public IPage<OperatorUser> queryPageByName(int pageNum, int pageSize, String name) {


        IPage<OperatorUser> page = new Page<>(pageNum,pageSize);

        QueryWrapper<OperatorUser> queryWrapper = new QueryWrapper<>();
        if (StringUtils.isNotEmpty(name)){
            queryWrapper.like("loginname",name);
        }
        return this.page(page,queryWrapper);
    }

    @Override
    public Result login(String loginName, String loginPass) {
        Result result = new Result();

        //参数校验
        if (StringUtils.isEmpty(loginName)){
            result.setStatus(ResultCode.error);
            result.setDesc("用户名不存在");
            return result;
        }
        if (StringUtils.isEmpty(loginPass)){
            result.setStatus(ResultCode.error);
            result.setDesc("密码不存在");
            return result;
        }

        //查询用户是否存在
        QueryWrapper<OperatorUser> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("loginname",loginName);
        OperatorUser operatorUser = this.getOne(queryWrapper);
        if (operatorUser == null){
            result.setStatus(ResultCode.error);
            result.setDesc("用户不存在");
            return result;
        }

        //密码校验
        String salts = MD5CryptUtil.getSalts(operatorUser.getLoginpass());
        if (!Md5Crypt.md5Crypt(loginPass.getBytes(),salts).equals(operatorUser.getLoginpass())){
            result.setStatus(ResultCode.error);
            result.setDesc("密码不正确");
            return result;
        }

        //jwt令牌生成
        Map<String,Object> tokenInfo = Maps.newHashMap();
        tokenInfo.put("loginName",operatorUser.getLoginname());
        tokenInfo.put("loginPass",operatorUser.getLoginpass());
        String token = null;
        try{
            //生成令牌
            token =JWTUtil.createJWTByObj(tokenInfo,secret);
        }catch (Exception e){
            e.printStackTrace();
            result.setStatus(ResultCode.error);
            result.setDesc("生成令牌失败");
            return result;
        }

        result.setStatus(ResultCode.success);
        result.setDesc("ok");
        result.setData(operatorUser);
        result.setToken(token);

        return result;
    }


}
