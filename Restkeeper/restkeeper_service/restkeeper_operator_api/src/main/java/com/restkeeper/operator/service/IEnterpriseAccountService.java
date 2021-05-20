package com.restkeeper.operator.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.restkeeper.operator.entity.EnterpriseAccount;
import com.restkeeper.utils.Result;

public interface IEnterpriseAccountService extends IService<EnterpriseAccount> {

    //数据分页查询（按照企业名称进行模糊查询）
    IPage<EnterpriseAccount> queryPageByName(int pageNum,int pageSize,String enterpriseName);

    //新增帐号
    boolean add(EnterpriseAccount enterpriseAccount);

    //帐号还原
    boolean recovery(String id);

    //重置密码
    boolean restPwd(String id,String password);

    //登录
    Result login(String shopId,String phone,String loginPass);

}
