package com.restkeeper.store.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.restkeeper.constants.SystemCode;
import com.restkeeper.exception.BussinessException;
import com.restkeeper.store.entity.Credit;
import com.restkeeper.store.entity.CreditCompanyUser;
import com.restkeeper.store.mapper.CreditMapper;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.dubbo.config.annotation.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@org.springframework.stereotype.Service("creditService")
@Service(version = "1.0.0",protocol = "dubbo")
public class CreditServiceImpl extends ServiceImpl<CreditMapper, Credit> implements ICreditService {

    @Autowired
    @Qualifier("creditCompanyUserService")
    private ICreditCompanyUserService creditCompanyUserService;

    @Override
    @Transactional
    public boolean add(Credit credit, List<CreditCompanyUser> users) {

        //保存挂账的基础信息
        this.save(credit);

        if (users!=null && !users.isEmpty()){

            //获取用户名列表
            List<String> userNameList = users.stream().map(d -> d.getUserName()).collect(Collectors.toList());

            //用户名去重
            long count = userNameList.stream().distinct().count();
            if (userNameList.size() != count){
                throw new BussinessException("用户名重复");
            }

            //设置关联
            users.forEach(d->{
                d.setCreditId(credit.getCreditId());
            });

            return creditCompanyUserService.saveBatch(users);


        }

        return true;
    }

    @Override
    public IPage<Credit> queryPage(int pageNum, int pageSize, String userName) {

        IPage<Credit> page = new Page<>(pageNum,pageSize);

        QueryWrapper<Credit> creditQueryWrapper = new QueryWrapper<>();

        if (StringUtils.isNotEmpty(userName)){

            creditQueryWrapper.lambda().like(Credit::getUserName,userName)
                    .or()
                    .inSql(Credit::getCreditId,"select credit_id from t_credit_company_user where user_name like '%"+ StringEscapeUtils.escapeSql(userName) +"%'");
        }
        page = this.page(page,creditQueryWrapper);

        List<Credit> creditList = page.getRecords();

        //如果类型是公司，还需要设置相关的挂账人信息
        creditList.forEach(d->{
            if (d.getCreditType() == SystemCode.CREDIT_TYPE_COMPANY){

                QueryWrapper<CreditCompanyUser> queryWrapper = new QueryWrapper<>();

                queryWrapper.lambda().eq(CreditCompanyUser::getCreditId,d.getCreditId());

                d.setUsers(creditCompanyUserService.list(queryWrapper));
            }
        });

        return page;
    }

    @Override
    public Credit queryById(String id) {

        Credit credit = this.getById(id);
        if (credit == null){
            throw new BussinessException("不存在该挂账信息");
        }

        //企业挂账信息
        if (credit.getCreditType() == SystemCode.CREDIT_TYPE_COMPANY){

            QueryWrapper<CreditCompanyUser> queryWrapper = new QueryWrapper<>();

            queryWrapper.lambda().eq(CreditCompanyUser::getCreditId,credit.getCreditId());

            credit.setUsers(creditCompanyUserService.list(queryWrapper));
        }

        return credit;
    }

    @Override
    @Transactional
    public boolean updateInfo(Credit credit, List<CreditCompanyUser> users) {

        if (credit.getCreditType() == SystemCode.CREDIT_TYPE_COMPANY){
            //删除原有关联关系
            List<CreditCompanyUser> userList = credit.getUsers();
            if (userList!=null && !userList.isEmpty()){

                List<String> idList = userList.stream().map(d -> d.getId()).collect(Collectors.toList());

                creditCompanyUserService.removeByIds(idList);
            }
        }

        //重新建立关联关系
        if(users != null&&!users.isEmpty()){

            //获取用户名列表
            List<String> userNameList= users.stream().map(d->d.getUserName()).collect(Collectors.toList());

            //去重判断
            long count = userNameList.stream().distinct().count();
            if(userNameList.size()!=count){
                throw new BussinessException("用户名重复");
            }
            //设置关联
            users.forEach(d->{
                d.setCreditId(credit.getCreditId());
            });
            return creditCompanyUserService.saveBatch(users);
        }

        //credit类型是个人
        return this.saveOrUpdate(credit);
    }


}
