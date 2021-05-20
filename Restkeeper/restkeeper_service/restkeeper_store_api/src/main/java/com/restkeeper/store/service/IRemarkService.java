package com.restkeeper.store.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.restkeeper.store.entity.Remark;

import java.util.List;

public interface IRemarkService extends IService<Remark> {

    //查询备注列表
    List<Remark> getRemarks();

    //修改备注信息
    boolean updateRemarks(List<Remark> remarks);
}
