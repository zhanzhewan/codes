package com.restkeeper.store.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.restkeeper.constants.SystemCode;
import com.restkeeper.operator.entity.SysDictionary;
import com.restkeeper.operator.service.ISysDictService;
import com.restkeeper.store.entity.Remark;
import com.restkeeper.store.mapper.RemarkMapper;
import org.apache.dubbo.config.annotation.Reference;
import org.apache.dubbo.config.annotation.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@org.springframework.stereotype.Service("remarkService")
@Service(version = "1.0.0",protocol = "dubbo")
public class RemarkServiceImpl extends ServiceImpl<RemarkMapper, Remark> implements IRemarkService {

    @Reference(version = "1.0.0",check = false)
    private ISysDictService sysDictService;

    @Override
    public List<Remark> getRemarks() {

        //获取备注列表
        List<Remark> remarks = this.list();

        //如果集合中没有数据的话，查询字典表获取系统通用的备注信息
        if (remarks == null || remarks.isEmpty()){

            remarks = new ArrayList<>();

            List<SysDictionary> list = sysDictService.getDictionaryList(SystemCode.DICTIONARY_REMARK);

            //将SysDictionary转换为Remark
            for (SysDictionary dictionary : list) {
                Remark remark = new Remark();

                remark.setRemarkName(dictionary.getDictName());
                remark.setRemarkValue(dictionary.getDictData());

                remarks.add(remark);
            }
        }

        return remarks;
    }

    @Override
    @Transactional
    public boolean updateRemarks(List<Remark> remarks) {

        //删除以前的门店备注信息
        QueryWrapper<Remark> wrapper = new QueryWrapper<>();
        this.remove(wrapper);

        //批量插入新的备注信息
        return this.saveBatch(remarks);
    }
}
