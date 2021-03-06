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



        //?????????????????????
        if (StringUtils.isEmpty(orderEntity.getOrderNumber())){
            String storeId = RpcContext.getContext().getAttachment("storeId");
            orderEntity.setOrderNumber(SequenceUtils.getSequence(storeId));
        }
        this.saveOrUpdate(orderEntity);

        //??????????????????
        List<OrderDetailEntity> orderDetailEntities = orderEntity.getOrderDetails();
        orderDetailEntities.forEach(orderDetailEntity -> {

            orderDetailEntity.setOrderId(orderEntity.getOrderId());
            orderDetailEntity.setOrderNumber(SequenceUtils.getSequenceWithPrefix(orderEntity.getOrderNumber()));

            //????????????
            //????????????TenantContext??????????????????????????????
            TenantContext.addAttachment("shopId",RpcContext.getContext().getAttachment("shopId"));
            TenantContext.addAttachment("storeId",RpcContext.getContext().getAttachment("storeId"));

            Integer remainder = sellCalculationService.getRemainderCount(orderDetailEntity.getDishId());
            if (remainder != -1){
                if (remainder < orderDetailEntity.getDishNumber()){
                    throw new BussinessException(orderDetailEntity.getDishName()+"????????????????????????");
                }
            }

            //????????????
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

        //????????????????????????????????????
        OrderDetailEntity detailEntity = orderDetailService.getById(detailDTO.getDetailId());

        Integer detailStatus = detailEntity.getDetailStatus();
        if (OrderDetailType.PLUS_DISH.getType() == detailStatus || OrderDetailType.NORMAL_DISH.getType() == detailStatus){

            if (detailEntity.getDishNumber() <= 0){
                throw new BussinessException(detailEntity.getDishName()+"???????????????");
            }

            //??????????????????????????????????????????
            OrderDetailEntity return_detailEntity = new OrderDetailEntity();
            BeanUtils.copyProperties(detailEntity,return_detailEntity);
            //????????????copy??????
            return_detailEntity.setDetailId(null);
            return_detailEntity.setStoreId(null);
            return_detailEntity.setShopId(null);

            return_detailEntity.setOrderNumber(SequenceUtils.getSequenceWithPrefix(detailEntity.getOrderNumber()));
            return_detailEntity.setDetailStatus(OrderDetailType.RETURN_DISH.getType());
            return_detailEntity.setDishNumber(1);
            return_detailEntity.setReturnRemark(detailDTO.getRemarks().toString());
            orderDetailService.save(return_detailEntity);

            //????????????????????????????????????
            detailEntity.setDishNumber(detailEntity.getDishNumber()-1);
            detailEntity.setDishAmount(detailEntity.getDishNumber()*detailEntity.getDishPrice());
            orderDetailService.updateById(detailEntity);

            //????????????????????????
            OrderEntity orderEntity = this.getById(detailEntity.getOrderId());
            orderEntity.setTotalAmount(orderEntity.getTotalAmount()-detailEntity.getDishPrice());
            this.updateById(orderEntity);

            //??????  store
            /*TenantContext.addAttachment("shopId",RpcContext.getContext().getAttachment("shopId"));
            TenantContext.addAttachment("storeId",RpcContext.getContext().getAttachment("storeId"));*/

            Integer remainderCount = sellCalculationService.getRemainderCount(detailEntity.getDishId());
            if (remainderCount > 0){
                //???????????????????????????
                //?????????????????????+1
                sellCalculationService.add(detailEntity.getDishId(),1);
            }

        }else {

            throw new BussinessException("?????????????????????");
        }

        return true;
    }

    @Reference(version = "1.0.0",check = false)
    private ITableService tableService;

    @Override
    @Transactional
    @TenantAnnotation
    public boolean pay(OrderEntity orderEntity) {

        //???????????????????????????
        this.updateById(orderEntity);

        //?????????????????????
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

        //??????????????????
        if (orderEntity.getPayType() == OrderPayType.CREDIT.getType()){

            String creditId = creditDTO.getCreditId();
            Credit credit = creditService.getById(creditId);

            //????????????
            if (credit.getCreditType() == SystemCode.CREDIT_TYPE_USER){

                //?????????????????????????????????
                if (!credit.getUserName().equals(creditDTO.getCreditUserName())){
                    throw new BussinessException("???????????????????????????????????????");
                }

                credit.setCreditAmount(credit.getCreditAmount() + creditDTO.getCreditAmount());
                creditService.saveOrUpdate(credit);

            }

            //????????????
            List<CreditCompanyUser> companyUsers = null;
            if (credit.getCreditType() == SystemCode.CREDIT_TYPE_COMPANY){

                /*QueryWrapper<CreditCompanyUser> queryWrapper = new QueryWrapper<>();
                queryWrapper.lambda().eq(CreditCompanyUser::getCreditId,creditId);
                List<CreditCompanyUser> list = creditCompanyUserService.list(queryWrapper);*/

                List<CreditCompanyUser> companyUserList = creditCompanyUserService.getInfoList(creditId);

                //?????????????????????????????????????????????
                Optional<CreditCompanyUser> resultInfo = companyUserList.stream().filter(user -> user.getUserName().equals(creditDTO.getCreditUserName())).findFirst();
                if (!resultInfo.isPresent()){
                    //???????????????????????????
                    throw new BussinessException("???????????????????????????????????????????????????????????????");
                }
                companyUsers=companyUserList;

                credit.setCreditAmount(credit.getCreditAmount() + creditDTO.getCreditAmount());
                creditService.saveOrUpdate(credit);
            }

            //??????????????????
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

            //???????????????????????????
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

        //????????????????????????
        Table targetTable = tableService.getById(targetTableId);
        if (targetTable == null){
            throw new BussinessException("???????????????");
        }
        if (targetTable.getStatus() != SystemCode.TABLE_STATUS_FREE){
            throw new BussinessException("??????????????????????????????????????????");
        }

        //??????????????????
        OrderEntity orderEntity = this.getById(orderId);

        //???????????????????????????
        Table sourceTable = tableService.getById(orderEntity.getTableId());

        //?????????????????????????????????????????????
        sourceTable.setStatus(SystemCode.TABLE_STATUS_FREE);
        tableService.updateById(sourceTable);

        //???????????????????????????????????????
        targetTable.setStatus(SystemCode.TABLE_STATUS_OPENED);
        tableService.updateById(targetTable);

        //????????????????????????
        TableLog tableLog =new TableLog();
        tableLog.setTableStatus(SystemCode.TABLE_STATUS_OPENED);
        tableLog.setCreateTime(LocalDateTime.now());
        tableLog.setTableId(targetTableId);
        tableLog.setUserNumbers(orderEntity.getPersonNumbers());
        tableLog.setUserId(loginUserName);
        tableLogService.save(tableLog);
        int i=1/0;

        //????????????????????????????????????
        orderEntity.setTableId(targetTableId);

        return this.updateById(orderEntity);
    }

    @Override
    public CurrentAmountCollectDTO getCurrentAmount(LocalDate start, LocalDate end) {
        CurrentAmountCollectDTO result  = new CurrentAmountCollectDTO();

        //?????????????????????????????????
        QueryWrapper<OrderEntity> totalPayQueryWrapper = new QueryWrapper<>();
        totalPayQueryWrapper.select("SUM(pay_amount) as total_amount").lambda()
                .ge(OrderEntity::getLastUpdateTime,start)
                .lt(OrderEntity::getLastUpdateTime,end)
                .eq(OrderEntity::getPayStatus,SystemCode.ORDER_STATUS_PAYED);
        OrderEntity totalPayAmount = this.getOne(totalPayQueryWrapper);
        result.setPayTotal(totalPayAmount!=null?totalPayAmount.getTotalAmount():0);

        //?????????????????????????????????
        QueryWrapper<OrderEntity> totalPayCountWrapper = new QueryWrapper<>();
        totalPayCountWrapper.lambda()
                .ge(OrderEntity::getLastUpdateTime,start)
                .lt(OrderEntity::getLastUpdateTime,end)
                .eq(OrderEntity::getPayStatus,SystemCode.ORDER_STATUS_PAYED);
        int totalPayCount = this.count(totalPayCountWrapper);
        result.setPayTotalCount(totalPayCount);

        //??????????????????
        QueryWrapper<OrderEntity> noPayQueryWrapper = new QueryWrapper<>();
        noPayQueryWrapper.select("SUM(total_amount) as total_amount").lambda()
                .ge(OrderEntity::getLastUpdateTime,start)
                .lt(OrderEntity::getLastUpdateTime,end)
                .eq(OrderEntity::getPayStatus,SystemCode.ORDER_STATUS_NOTPAY);
        OrderEntity noPayTotalAmount = this.getOne(noPayQueryWrapper);
        result.setNoPayTotal(noPayTotalAmount!=null?noPayTotalAmount.getTotalAmount():0);


        //??????????????????
        QueryWrapper<OrderEntity> noPayCountWrapper = new QueryWrapper<>();
        noPayCountWrapper.lambda()
                .ge(OrderEntity::getLastUpdateTime,start)
                .lt(OrderEntity::getLastUpdateTime,end)
                .eq(OrderEntity::getPayStatus,SystemCode.ORDER_STATUS_NOTPAY);
        int noPayTotalCount = this.count(noPayCountWrapper);
        result.setNoPayTotalCount(noPayTotalCount);

        //??????????????????????????????
        QueryWrapper<OrderEntity> payedTotalPersionWrapper = new QueryWrapper<>();
        payedTotalPersionWrapper.select("SUM(person_numbers) as person_numbers").lambda()
                .ge(OrderEntity::getLastUpdateTime,start)
                .lt(OrderEntity::getLastUpdateTime,end)
                .eq(OrderEntity::getPayStatus,SystemCode.ORDER_STATUS_PAYED);
        OrderEntity payedTotalPersion = this.getOne(payedTotalPersionWrapper);
        result.setTotalPerson(payedTotalPersion!=null?payedTotalPersion.getPersonNumbers():0);


        //????????????????????????
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
            //?????????????????????
            wrapper.select("SUM(total_amount) as total_amount","HOUR(last_update_time) as current_date_hour")
                    .lambda().ge(OrderEntity::getLastUpdateTime,start)
                    .lt(OrderEntity::getLastUpdateTime,end);
        }

        if (type ==2){
            //?????????????????????????????????
            wrapper.select("COUNT(total_amount) as total_amount","HOUR(last_update_time) as current_date_hour")
                    .lambda().ge(OrderEntity::getLastUpdateTime,start)
                    .lt(OrderEntity::getLastUpdateTime,end);

        }

        //????????????????????????
        wrapper.groupBy("current_date_hour").orderByAsc("current_date_hour");
        List<CurrentHourCollectDTO> result = Lists.newArrayList();

        this.getBaseMapper().selectList(wrapper).forEach(o->{

            CurrentHourCollectDTO item = new CurrentHourCollectDTO();
            item.setTotalAmount(o.getTotalAmount());
            item.setCurrentDateHour(o.getCurrentDateHour());
            result.add(item);
        });

        //????????????null???????????????0
        for(int i=0;i<=23;i++){

            int hour =i;

            if (!result.stream().anyMatch(r->r.getCurrentDateHour() == hour)){

                CurrentHourCollectDTO item = new CurrentHourCollectDTO();
                item.setTotalAmount(0);
                item.setCurrentDateHour(hour);
                result.add(item);
            }
        }

        //???result??????????????????????????????
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

    //???t_order_detail_meal???????????????
    @TenantAnnotation
    private void saveOrderDetailMealInfo(String orderId){

        //???????????????????????????????????????
        QueryWrapper<OrderDetailEntity> wrapper = new QueryWrapper<>();
        wrapper.lambda().eq(OrderDetailEntity::getOrderId,orderId).eq(OrderDetailEntity::getDishType,2);
        List<OrderDetailEntity> orderDetailSetMealList = orderDetailService.list(wrapper);

        //????????????????????????????????????  orderSetMeal->??????
        orderDetailSetMealList.forEach(orderSetMeal->{

            //????????????id?????????????????????????????????
            List<Dish> dishList = setMealDishService.getAllDishBySetMealId(orderSetMeal.getDishId());

            //?????????orderdetailmeal??????????????????????????????????????????
            OrderDetailMealEntity orderDetailMealEntity = new OrderDetailMealEntity();

            //??????????????????
            BeanUtils.copyProperties(orderSetMeal,orderDetailMealEntity);


            //??????????????????????????? = ?????????????????? / ????????????????????????????????????
            float allDishPriceInSetMeal = dishList.stream().map(d->d.getPrice()*setMealDishService.getDishCopiesInSetMeal(d.getId(),orderSetMeal.getDishId()))
                    .reduce(Integer::sum).get() * orderSetMeal.getDishNumber();
            float rate = orderSetMeal.getDishAmount() / allDishPriceInSetMeal;


            //????????????????????????
            dishList.forEach(d->{
                //???????????????????????????
                orderDetailMealEntity.setDetailId(null);
                orderDetailMealEntity.setShopId(null);
                orderDetailMealEntity.setStoreId(null);

                orderDetailMealEntity.setDishId(d.getId());
                orderDetailMealEntity.setDishName(d.getName());
                orderDetailMealEntity.setDishPrice(d.getPrice());
                orderDetailMealEntity.setDishType(1);
                orderDetailMealEntity.setDishCategoryName(d.getDishCategory().getName());
                //????????????????????????????????????
                Integer dishCopies = setMealDishService.getDishCopiesInSetMeal(d.getId(),orderSetMeal.getDishId());

                //??????????????????????????????????????? * ??????????????????????????????
                orderDetailMealEntity.setDishNumber(orderSetMeal.getDishNumber() * dishCopies);

                orderDetailMealEntity.setDishAmount((int)(d.getPrice()*dishCopies*orderSetMeal.getDishNumber()*rate));

                orderDetailMealService.save(orderDetailMealEntity);
            });

        });
    }
}
