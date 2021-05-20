package com.restkeeper.store.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.restkeeper.store.entity.Staff;
import com.restkeeper.utils.Result;

public interface IStaffService extends IService<Staff> {

    //新增员工信息
    boolean addStaff(Staff staff);

    //登录
    Result login(String shopId,String loginName,String password);
}
