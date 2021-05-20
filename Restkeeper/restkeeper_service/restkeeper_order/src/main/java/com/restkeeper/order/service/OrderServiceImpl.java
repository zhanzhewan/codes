package com.restkeeper.order.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.google.common.collect.Lists;
import com.restkeeper.aop.TenantAnnotation;
import com.restkeeper.constants.OrderDetailType;
import com.restkeeper.constants.OrderPayType;
import com.restkeeper.constants.SystemCode;
import com.restkeeper.dto.*;
import com.restkeeper.entity.OrderDetailEntity;
import com.restkeeper.entity.OrderDetailMealEntity;
import com.restkeeper.entity.OrderEntity;
import com.restkeeper.exception.BussinessException;
import com.restkeeper.order.mapper.OrderMapper;
import com.restkeeper.service.IOrderDetailMealService;
import com.restkeeper.service.IOrderDetailService;
import com.restkeeper.service.IOrderService;
import com.restkeeper.store.entity.*;
import com.restkeeper.store.service.*;
import com.restkeeper.tenant.TenantContext;
import com.restkeeper.utils.SequenceUtils;
import io.seata.spring.annotation.GlobalTransactional;
import org.apache.commons.lang.StringUtils;
import org.apache.dubbo.config.annotation.Reference;
import org.apache.dubbo.config.annotation.Service;
import org.apache.dubbo.rpc.RpcContext;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@org.springframework.stereotype.Service("orderService")
@Service(version = "1.0.0",protocol = "dubbo")
public class OrderServiceImpl extends ServiceImpl<OrderMapper, OrderEntity> implements IOrderService {

    @Autowired
    @Qualifier("orderDetailService")
    private IOrderDetailService orderDetailService;

    @Reference(version = "1.0.0",check = false)
    private ISellCalculationService sellCalculationService;

    @Override
    //@Transactional
    @GlobalTransactional
    public String addOrder(OrderEntity orderEntity) {



        //生成订单流水号
        if (StringUtils.isEmpty(orderEntity.getOrderNumber())){
            String storeId = RpcContext.getContext().getAttachment("storeId");
            orderEntity.setOrderNumber(SequenceUtils.getSequence(storeId));
        }
        this.saveOrUpdate(orderEntity);

        //操作订单详情
        List<OrderDetailEntity> orderDetailEntities = orderEntity.getOrderDetails();
        orderDetailEntities.forEach(orderDetailEntity -> {

            orderDetailEntity.setOrderId(orderEntity.getOrderId());
            orderDetailEntity.setOrderNumber(SequenceUtils.getSequenceWithPrefix(orderEntity.getOrderNumber()));

            //沽清检查
            //手动的向TenantContext中设置相关的租户信息
            TenantContext.addAttachment("shopId",RpcContext.getContext().getAttachment("shopId"));
            TenantContext.addAttachment("storeId",RpcContext.getContext().getAttachment("storeId"));

            Integer remainder = sellCalculationService.getRemainderCount(orderDetailEntity.getDishId());
            if (remainder != -1){
                if (remainder < orderDetailEntity.getDishNumber()){
                    throw new BussinessException(orderDetailEntity.getDishName()+"超过沽清设置数目");
                }
            }

            //沽清扣减
            sellCalculationService.decrease(orderDetailEntity.getDishId(),orderDetailEntity.getDishNumber());

            //int i =1/0;
        });
        orderDetailService.saveBatch(orderDetailEntities);


        return orderEntity.getOrderId();
    }

    @Override
    @Transactional
    @TenantAnnotation
    public boolean returnDish(DetailDTO detailDTO) {

        //获取到原有的订单详情信息
        OrderDetailEntity detailEntity = orderDetailService.getById(detailDTO.getDetailId());

        Integer detailStatus = detailEntity.getDetailStatus();
        if (OrderDetailType.PLUS_DISH.getType() == detailStatus || OrderDetailType.NORMAL_DISH.getType() == detailStatus){

            if (detailEntity.getDishNumber() <= 0){
                throw new BussinessException(detailEntity.getDishName()+"已经被退完");
            }

            //产生新的订单详情记录（退菜）
            OrderDetailEntity return_detailEntity = new OrderDetailEntity();
            BeanUtils.copyProperties(detailEntity,return_detailEntity);
            //去掉多余copy字段
            return_detailEntity.setDetailId(null);
            return_detailEntity.setStoreId(null);
            return_detailEntity.setShopId(null);

            return_detailEntity.setOrderNumber(SequenceUtils.getSequenceWithPrefix(detailEntity.getOrderNumber()));
            return_detailEntity.setDetailStatus(OrderDetailType.RETURN_DISH.getType());
            return_detailEntity.setDishNumber(1);
            return_detailEntity.setReturnRemark(detailDTO.getRemarks().toString());
            orderDetailService.save(return_detailEntity);

            //修改原有订单详情中的信息
            detailEntity.setDishNumber(detailEntity.getDishNumber()-1);
            detailEntity.setDishAmount(detailEntity.getDishNumber()*detailEntity.getDishPrice());
            orderDetailService.updateById(detailEntity);

            //修改订单主表信息
            OrderEntity orderEntity = this.getById(detailEntity.getOrderId());
            orderEntity.setTotalAmount(orderEntity.getTotalAmount()-detailEntity.getDishPrice());
            this.updateById(orderEntity);

            //沽清  store
            /*TenantContext.addAttachment("shopId",RpcContext.getContext().getAttachment("shopId"));
            TenantContext.addAttachment("storeId",RpcContext.getContext().getAttachment("storeId"));*/

            Integer remainderCount = sellCalculationService.getRemainderCount(detailEntity.getDishId());
            if (remainderCount > 0){
                //沽清中有该菜品信息
                //沽清的剩余数量+1
                sellCalculationService.add(detailEntity.getDishId(),1);
            }

        }else {

            throw new BussinessException("不支持退菜操作");
        }

        return true;
    }

    @Reference(version = "1.0.0",check = false)
    private ITableService tableService;

    @Override
    @Transactional
    @TenantAnnotation
    public boolean pay(OrderEntity orderEntity) {

        //修改订单主表的信息
        this.updateById(orderEntity);

        //修改桌台的状态
        Table table = tableService.getById(orderEntity.getTableId());
        table.setStatus(SystemCode.TABLE_STATUS_FREE);
        tableService.updateById(table);

        saveOrderDetailMealInfo(orderEntity.getOrderId());

        return true;
    }

    @Reference(version = "1.0.0",check = false)
    private ICreditService creditService;

    @Reference(version = "1.0.0",check = false)
    private ICreditCompanyUserService creditCompanyUserService;

    @Reference(version = "1.0.0",check = false)
    private ICreditLogService creditLogService;

    @Override
    @Transactional
    @TenantAnnotation
    public boolean pay(OrderEntity orderEntity, CreditDTO creditDTO) {

        this.updateById(orderEntity);

        //设置挂账信息
        if (orderEntity.getPayType() == OrderPayType.CREDIT.getType()){

            String creditId = creditDTO.getCreditId();
            Credit credit = creditService.getById(creditId);

            //个人用户
            if (credit.getCreditType() == SystemCode.CREDIT_TYPE_USER){

                //判断挂账人信息是否正确
                if (!credit.getUserName().equals(creditDTO.getCreditUserName())){
                    throw new BussinessException("挂账人信息不同，不允许挂账");
                }

                credit.setCreditAmount(credit.getCreditAmount() + creditDTO.getCreditAmount());
                creditService.saveOrUpdate(credit);

            }

            //公司用户
            List<CreditCompanyUser> companyUsers = null;
            if (credit.getCreditType() == SystemCode.CREDIT_TYPE_COMPANY){

                /*QueryWrapper<CreditCompanyUser> queryWrapper = new QueryWrapper<>();
                queryWrapper.lambda().eq(CreditCompanyUser::getCreditId,creditId);
                List<CreditCompanyUser> list = creditCompanyUserService.list(queryWrapper);*/

                List<CreditCompanyUser> companyUserList = creditCompanyUserService.getInfoList(creditId);

                //判断当前挂账人在集合中是否存在
                Optional<CreditCompanyUser> resultInfo = companyUserList.stream().filter(user -> user.getUserName().equals(creditDTO.getCreditUserName())).findFirst();
                if (!resultInfo.isPresent()){
                    //不存在，不允许挂账
                    throw new BussinessException("当前用户不在该公司中，请联系管家端进行设置");
                }
                companyUsers=companyUserList;

                credit.setCreditAmount(credit.getCreditAmount() + creditDTO.getCreditAmount());
                creditService.saveOrUpdate(credit);
            }

            //挂账明细信息
            CreditLogs creditLogs = new CreditLogs();
            creditLogs.setCreditId(creditId);
            creditLogs.setOrderId(orderEntity.getOrderId());
            creditLogs.setType(credit.getCreditType());
            creditLogs.setCreditAmount(creditDTO.getCreditAmount());
            creditLogs.setOrderAmount(orderEntity.getTotalAmount());
            creditLogs.setReceivedAmount(orderEntity.getTotalAmount());
            creditLogs.setCreditAmount(creditDTO.getCreditAmount());

            if (credit.getCreditType() == SystemCode.CREDIT_TYPE_COMPANY){

                creditLogs.setUserName(creditDTO.getCreditUserName());
                creditLogs.setCompanyName(credit.getCompanyName());
                Optional<CreditCompanyUser> optional = companyUsers.stream().filter(user -> user.getUserName().equals(creditDTO.getCreditUserName())).findFirst();
                String phone = optional.get().getPhone();
                creditLogs.setPhone(phone);

            }else if (credit.getCreditType() == SystemCode.CREDIT_TYPE_USER){

                creditLogs.setUserName(creditDTO.getCreditUserName());
                creditLogs.setPhone(credit.getPhone());
            }
            creditLogService.save(creditLogs);

            //修改桌台状态为空闲
            Table table = tableService.getById(orderEntity.getTableId());
            table.setStatus(SystemCode.TABLE_STATUS_FREE);
            tableService.updateById(table);

        }

        saveOrderDetailMealInfo(orderEntity.getOrderId());

        return true;
    }

    @Reference(version = "1.0.0",check = false)
    private ITableLogService tableLogService;

    @Override
    @Transactional
    @TenantAnnotation
    public boolean changeTable(String orderId, String targetTableId) {

        String loginUserName = RpcContext.getContext().getAttachment("loginUserName");

        //获取目标桌台信息
        Table targetTable = tableService.getById(targetTableId);
        if (targetTable == null){
            throw new BussinessException("桌台不存在");
        }
        if (targetTable.getStatus() != SystemCode.TABLE_STATUS_FREE){
            throw new BussinessException("桌台处于非空闲状态，不能换桌");
        }

        //获取订单对象
        OrderEntity orderEntity = this.getById(orderId);

        //获取原有的桌台对象
        Table sourceTable = tableService.getById(orderEntity.getTableId());

        //将原有的桌台对象状态设置为空闲
        sourceTable.setStatus(SystemCode.TABLE_STATUS_FREE);
        tableService.updateById(sourceTable);

        //将目标桌台的状态设置为开桌
        targetTable.setStatus(SystemCode.TABLE_STATUS_OPENED);
        tableService.updateById(targetTable);

        //新增桌台日志信息
        TableLog tableLog =new TableLog();
        tableLog.setTableStatus(SystemCode.TABLE_STATUS_OPENED);
        tableLog.setCreateTime(LocalDateTime.now());
        tableLog.setTableId(targetTableId);
        tableLog.setUserNumbers(orderEntity.getPersonNumbers());
        tableLog.setUserId(loginUserName);
        tableLogService.save(tableLog);
        int i=1/0;

        //重新设置订单与桌台的关联
        orderEntity.setTableId(targetTableId);

        return this.updateById(orderEntity);
    }

    @Override
    public CurrentAmountCollectDTO getCurrentAmount(LocalDate start, LocalDate end) {
        CurrentAmountCollectDTO result  = new CurrentAmountCollectDTO();

        //查询并设置已付款总金额
        QueryWrapper<OrderEntity> totalPayQueryWrapper = new QueryWrapper<>();
        totalPayQueryWrapper.select("SUM(pay_amount) as total_amount").lambda()
                .ge(OrderEntity::getLastUpdateTime,start)
                .lt(OrderEntity::getLastUpdateTime,end)
                .eq(OrderEntity::getPayStatus,SystemCode.ORDER_STATUS_PAYED);
        OrderEntity totalPayAmount = this.getOne(totalPayQueryWrapper);
        result.setPayTotal(totalPayAmount!=null?totalPayAmount.getTotalAmount():0);

        //查询并设置已付款总单数
        QueryWrapper<OrderEntity> totalPayCountWrapper = new QueryWrapper<>();
        totalPayCountWrapper.lambda()
                .ge(OrderEntity::getLastUpdateTime,start)
                .lt(OrderEntity::getLastUpdateTime,end)
                .eq(OrderEntity::getPayStatus,SystemCode.ORDER_STATUS_PAYED);
        int totalPayCount = this.count(totalPayCountWrapper);
        result.setPayTotalCount(totalPayCount);

        //未付款总金额
        QueryWrapper<OrderEntity> noPayQueryWrapper = new QueryWrapper<>();
        noPayQueryWrapper.select("SUM(total_amount) as total_amount").lambda()
                .ge(OrderEntity::getLastUpdateTime,start)
                .lt(OrderEntity::getLastUpdateTime,end)
                .eq(OrderEntity::getPayStatus,SystemCode.ORDER_STATUS_NOTPAY);
        OrderEntity noPayTotalAmount = this.getOne(noPayQueryWrapper);
        result.setNoPayTotal(noPayTotalAmount!=null?noPayTotalAmount.getTotalAmount():0);


        //未付款总单数
        QueryWrapper<OrderEntity> noPayCountWrapper = new QueryWrapper<>();
        noPayCountWrapper.lambda()
                .ge(OrderEntity::getLastUpdateTime,start)
                .lt(OrderEntity::getLastUpdateTime,end)
                .eq(OrderEntity::getPayStatus,SystemCode.ORDER_STATUS_NOTPAY);
        int noPayTotalCount = this.count(noPayCountWrapper);
        result.setNoPayTotalCount(noPayTotalCount);

        //当日已结账就餐总人数
        QueryWrapper<OrderEntity> payedTotalPersionWrapper = new QueryWrapper<>();
        payedTotalPersionWrapper.select("SUM(person_numbers) as person_numbers").lambda()
                .ge(OrderEntity::getLastUpdateTime,start)
                .lt(OrderEntity::getLastUpdateTime,end)
                .eq(OrderEntity::getPayStatus,SystemCode.ORDER_STATUS_PAYED);
        OrderEntity payedTotalPersion = this.getOne(payedTotalPersionWrapper);
        result.setTotalPerson(payedTotalPersion!=null?payedTotalPersion.getPersonNumbers():0);


        //未结账就餐总人数
        QueryWrapper<OrderEntity> noPayedTotalPersionWrapper = new QueryWrapper<>();
        noPayedTotalPersionWrapper.select("SUM(person_numbers) as person_numbers").lambda()
                .ge(OrderEntity::getLastUpdateTime,start)
                .lt(OrderEntity::getLastUpdateTime,end)
                .eq(OrderEntity::getPayStatus,SystemCode.ORDER_STATUS_NOTPAY);
        OrderEntity noPayedTotalPersion = this.getOne(noPayedTotalPersionWrapper);
        result.setCurrentPerson(noPayedTotalPersion!=null?noPayedTotalPersion.getPersonNumbers():0);


        return result;
    }

    @Override
    public List<CurrentHourCollectDTO> getCurrentHourCollect(LocalDate start, LocalDate end, Integer type) {

        QueryWrapper<OrderEntity> wrapper = new QueryWrapper<>();

        if (type == 1){
            //针对销售额求和
            wrapper.select("SUM(total_amount) as total_amount","HOUR(last_update_time) as current_date_hour")
                    .lambda().ge(OrderEntity::getLastUpdateTime,start)
                    .lt(OrderEntity::getLastUpdateTime,end);
        }

        if (type ==2){
            //针对销售量（单数）求和
            wrapper.select("COUNT(total_amount) as total_amount","HOUR(last_update_time) as current_date_hour")
                    .lambda().ge(OrderEntity::getLastUpdateTime,start)
                    .lt(OrderEntity::getLastUpdateTime,end);

        }

        //针对时间分组汇总
        wrapper.groupBy("current_date_hour").orderByAsc("current_date_hour");
        List<CurrentHourCollectDTO> result = Lists.newArrayList();

        this.getBaseMapper().selectList(wrapper).forEach(o->{

            CurrentHourCollectDTO item = new CurrentHourCollectDTO();
            item.setTotalAmount(o.getTotalAmount());
            item.setCurrentDateHour(o.getCurrentDateHour());
            result.add(item);
        });

        //当时间为null，设置值为0
        for(int i=0;i<=23;i++){

            int hour =i;

            if (!result.stream().anyMatch(r->r.getCurrentDateHour() == hour)){

                CurrentHourCollectDTO item = new CurrentHourCollectDTO();
                item.setTotalAmount(0);
                item.setCurrentDateHour(hour);
                result.add(item);
            }
        }

        //对result根据小时从小到大排序
        result.sort((a,b)->Integer.compare(a.getCurrentDateHour(),b.getCurrentDateHour()));

        return result;
    }

    @Override
    public List<PayTypeCollectDTO> getPayTypeCollect(LocalDate start, LocalDate end) {

        List<PayTypeCollectDTO> result = Lists.newArrayList();

        QueryWrapper<OrderEntity> wrapper = new QueryWrapper<>();

        wrapper.select("pay_type","SUM(pay_amount) as total_amount")
                .lambda().ge(OrderEntity::getLastUpdateTime,start)
                .lt(OrderEntity::getLastUpdateTime,end)
                .eq(OrderEntity::getPayStatus,SystemCode.ORDER_STATUS_PAYED)
                .groupBy(OrderEntity::getPayType);

        List<OrderEntity> orderEntityList = this.getBaseMapper().selectList(wrapper);

        orderEntityList.forEach(orderEntity -> {

            PayTypeCollectDTO dto = new PayTypeCollectDTO();

            dto.setPayType(orderEntity.getPayType());
            dto.setPayName(PayType.getName(orderEntity.getPayType()));
            dto.setTotalCount(orderEntity.getTotalAmount());
            result.add(dto);
        });

        return result;
    }

    @Override
    public PrivilegeDTO getPrivilegeCollect(LocalDate start, LocalDate end) {

        PrivilegeDTO dto = new PrivilegeDTO();

        QueryWrapper<OrderEntity> wrapper = new QueryWrapper<>();

        wrapper.select("SUM(present_amount) as present_amount","SUM(free_amount) as free_amount","SUM(small_amount) AS small_amount")
                .lambda().ge(OrderEntity::getLastUpdateTime,start)
                .lt(OrderEntity::getLastUpdateTime,end)
                .eq(OrderEntity::getPayStatus,SystemCode.ORDER_STATUS_PAYED);

        OrderEntity orderEntity = this.getBaseMapper().selectOne(wrapper);

        dto.setPresentAmount(orderEntity.getPresentAmount());
        dto.setSmallAmount(orderEntity.getSmallAmount());
        dto.setFreeAmount(orderEntity.getFreeAmount());

        return dto;
    }

    @Reference(version = "1.0.0",check = false)
    private ISetMealDishService setMealDishService;

    @Autowired
    @Qualifier("orderDetailMealService")
    private IOrderDetailMealService orderDetailMealService;

    //向t_order_detail_meal中添加数据
    @TenantAnnotation
    private void saveOrderDetailMealInfo(String orderId){

        //获取订单明细表中的套餐信息
        QueryWrapper<OrderDetailEntity> wrapper = new QueryWrapper<>();
        wrapper.lambda().eq(OrderDetailEntity::getOrderId,orderId).eq(OrderDetailEntity::getDishType,2);
        List<OrderDetailEntity> orderDetailSetMealList = orderDetailService.list(wrapper);

        //获取套餐下的相关菜品信息  orderSetMeal->套餐
        orderDetailSetMealList.forEach(orderSetMeal->{

            //通过套餐id获取该套餐下的菜品信息
            List<Dish> dishList = setMealDishService.getAllDishBySetMealId(orderSetMeal.getDishId());

            //插入到orderdetailmeal中（套餐下的每一个菜品信息）
            OrderDetailMealEntity orderDetailMealEntity = new OrderDetailMealEntity();

            //复制共有信息
            BeanUtils.copyProperties(orderSetMeal,orderDetailMealEntity);


            //当前套餐的优惠比率 = 套餐支付金额 / 套餐中所有菜品的原价总和
            float allDishPriceInSetMeal = dishList.stream().map(d->d.getPrice()*setMealDishService.getDishCopiesInSetMeal(d.getId(),orderSetMeal.getDishId()))
                    .reduce(Integer::sum).get() * orderSetMeal.getDishNumber();
            float rate = orderSetMeal.getDishAmount() / allDishPriceInSetMeal;


            //循环补充其他信息
            dishList.forEach(d->{
                //去除相关重复的信息
                orderDetailMealEntity.setDetailId(null);
                orderDetailMealEntity.setShopId(null);
                orderDetailMealEntity.setStoreId(null);

                orderDetailMealEntity.setDishId(d.getId());
                orderDetailMealEntity.setDishName(d.getName());
                orderDetailMealEntity.setDishPrice(d.getPrice());
                orderDetailMealEntity.setDishType(1);
                orderDetailMealEntity.setDishCategoryName(d.getDishCategory().getName());
                //获取套餐中这个菜品的数量
                Integer dishCopies = setMealDishService.getDishCopiesInSetMeal(d.getId(),orderSetMeal.getDishId());

                //菜品数量：订单中套餐的数量 * 套餐中这个菜品的数量
                orderDetailMealEntity.setDishNumber(orderSetMeal.getDishNumber() * dishCopies);

                orderDetailMealEntity.setDishAmount((int)(d.getPrice()*dishCopies*orderSetMeal.getDishNumber()*rate));

                orderDetailMealService.save(orderDetailMealEntity);
            });

        });
    }
}
