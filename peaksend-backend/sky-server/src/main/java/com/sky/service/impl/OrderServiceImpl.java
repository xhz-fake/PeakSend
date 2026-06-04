package com.sky.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.dto.OrdersPaymentDTO;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.entity.AddressBook;
import com.sky.entity.OrderDetail;
import com.sky.entity.Orders;
import com.sky.entity.ShoppingCart;
import com.sky.entity.User;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.mapper.AddressBookMapper;
import com.sky.mapper.OrderDetailMapper;
import com.sky.mapper.OrdersMapper;
import com.sky.mapper.ShoppingCartMapper;
import com.sky.mapper.UserMapper;
import com.sky.properties.WeChatProperties;
import com.sky.result.PageResult;
import com.sky.service.OrderService;
import com.sky.utils.WeChatPayUtil;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class OrderServiceImpl implements OrderService {

    @Autowired
    private AddressBookMapper addressBookMapper;

    @Autowired
    private ShoppingCartMapper shoppingCartMapper;

    @Autowired
    private OrdersMapper ordersMapper;

    @Autowired
    private OrderDetailMapper orderDetailMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private WeChatProperties weChatProperties;

    @Autowired
    private WeChatPayUtil weChatPayUtil;

    /**
     * 用户提交订单
     *
     * @param ordersSubmitDTO
     * @return
     */
    @Override
    @Transactional
    public OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO) {
        Long userId = BaseContext.getCurrentId();
        //- 当前请求在经过 JWT 拦截器后，后端已经知道“是谁登录了”
        //- 所以下单用户身份，是从当前线程上下文里拿的

        List<ShoppingCart> shoppingCartList = shoppingCartMapper.list(ShoppingCart.builder()
                .userId(userId)
                .build());
        if (shoppingCartList == null || shoppingCartList.isEmpty()) {//- 先查当前用户购物车里有什么，如果购物车为空，直接报业务逻辑的错
            throw new ShoppingCartBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }
        //- 根据当前用户 id 查购物车
        //- 看这个用户现在购物车里到底有没有商品
        //- 如果没有，就直接拦住，不让继续下单

        AddressBook addressBook = addressBookMapper.getById(ordersSubmitDTO.getAddressBookId());
        if (addressBook == null || !addressBook.getUserId().equals(userId)) {
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }
        //两层校验： 地址存在性校验 + 地址归属校验
        //- 这个地址存在不存在
        //- 这个地址是不是当前用户自己的

        User user = userMapper.getById(userId);

        Orders orders = new Orders();//- 新建一个订单实体对象后面要往里面装完整订单信息
        BeanUtils.copyProperties(ordersSubmitDTO, orders);// 先把前端能直接传的字段拷过来

        LocalDateTime now = LocalDateTime.now();
        orders.setNumber(String.valueOf(System.currentTimeMillis()));// 这里用当前时间戳生成订单号,给每张订单一个唯一编号。
        orders.setUserId(userId);// 订单归属当前登录用户
        orders.setStatus(Orders.PENDING_PAYMENT);// 订单刚创建出来时，业务状态是“待付款”
        orders.setPayStatus(Orders.UN_PAID);// 支付状态是“未支付”
        orders.setOrderTime(now);// 记录下单时间
        orders.setCheckoutTime(now); // 当前项目里先记录一个时间点，后面支付成功时还会更新
        //它们在补“前端不能决定、必须由后端决定”的字段。

        orders.setPhone(addressBook.getPhone());
        orders.setAddress(buildFullAddress(addressBook));
        orders.setConsignee(addressBook.getConsignee());
        //这三句非常关键。
        //说明订单里保存的收货信息，不是只保存一个 addressBookId 就完了。
        //而是会把当时的地址信息“固化”进订单里。

        orders.setUserName(user == null ? addressBook.getConsignee() : user.getName());
        ordersMapper.insert(orders);// 真正把订单主表插进数据库。

        // 这段代码本质上把购物车里的每一项商品，转换成订单明细。
        List<OrderDetail> orderDetailList = shoppingCartList.stream().map(item -> OrderDetail.builder()
                .orderId(orders.getId())// - 这条商品明细，属于刚刚那张订单
                // 订单主表和订单明细表，就是靠 orderId 关联起来的
                .name(item.getName())
                .image(item.getImage())
                .dishId(item.getDishId())
                .setmealId(item.getSetmealId())
                .dishFlavor(item.getDishFlavor())
                .number(item.getNumber())
                .amount(item.getAmount())
                .build()).collect(Collectors.toList());
        orderDetailMapper.insertBatch(orderDetailList);

        shoppingCartMapper.deleteByUserId(userId);// 购物车不是历史记录区，它只是下单前的暂存区。

        return OrderSubmitVO.builder()
                .id(orders.getId())
                .orderNumber(orders.getNumber())
                .orderAmount(orders.getAmount())
                .orderTime(orders.getOrderTime())
                .build();
    }

    /**
     * 用户取消订单
     *
     * @param id
     */
    @Override
    @Transactional
    public void cancelOrder(Long id) {
        Long userId = BaseContext.getCurrentId();
        Orders orders = ordersMapper.getById(id);
        if (orders == null || !orders.getUserId().equals(userId)) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        if (!Orders.PENDING_PAYMENT.equals(orders.getStatus())) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Orders updateOrder = new Orders();
        updateOrder.setId(id);
        updateOrder.setStatus(Orders.CANCELLED);
        updateOrder.setPayStatus(Orders.UN_PAID);
        updateOrder.setCancelReason("用户取消");
        updateOrder.setCancelTime(LocalDateTime.now());
        ordersMapper.update(updateOrder);
    }

    /**
     * 查询订单详情
     *
     * @param id
     * @return
     */
    @Override
    public OrderVO details(Long id) {
        Long userId = BaseContext.getCurrentId();
        Orders orders = ordersMapper.getById(id);
        if (orders == null || !orders.getUserId().equals(userId)) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(id);

        OrderVO orderVO = new OrderVO();
        BeanUtils.copyProperties(orders, orderVO);
        orderVO.setOrderDetailList(orderDetailList);
        orderVO.setDeliveryFee(BigDecimal.ZERO);
        return orderVO;
    }

    /**
     * 用户端历史订单分页查询
     *
     * @param page 页码
     * @param pageSize 每页条数
     * @param status 订单状态
     * @return 分页结果
     */
    @Override
    public PageResult pageQuery4User(int page, int pageSize, Integer status) {
        PageHelper.startPage(page, pageSize);

        OrdersPageQueryDTO ordersPageQueryDTO = new OrdersPageQueryDTO();
        ordersPageQueryDTO.setUserId(BaseContext.getCurrentId());
        ordersPageQueryDTO.setStatus(status);

        Page<Orders> orderPage = ordersMapper.pageQuery(ordersPageQueryDTO);
        List<OrderVO> records = new ArrayList<>();

        if (orderPage != null && orderPage.getTotal() > 0) {
            for (Orders orders : orderPage) {
                List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(orders.getId());
                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(orders, orderVO);
                orderVO.setOrderDetailList(orderDetailList);
                orderVO.setDeliveryFee(BigDecimal.ZERO);
                records.add(orderVO);
            }
        }

        return new PageResult(orderPage.getTotal(), records);
    }

    /**
     * 再来一单
     *
     * @param id 订单 id
     */
    @Override
    @Transactional
    public void repetition(Long id) {
        Long userId = BaseContext.getCurrentId();
        Orders orders = ordersMapper.getById(id);
        if (orders == null || !orders.getUserId().equals(userId)) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(id);
        if (orderDetailList == null || orderDetailList.isEmpty()) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        for (OrderDetail orderDetail : orderDetailList) {
            ShoppingCart shoppingCart = new ShoppingCart();
            BeanUtils.copyProperties(orderDetail, shoppingCart, "id");
            shoppingCart.setUserId(userId);
            shoppingCart.setCreateTime(LocalDateTime.now());
            shoppingCartMapper.insert(shoppingCart);
        }
    }

    /**
     * 订单支付
     *
     * @param ordersPaymentDTO 支付参数
     * @return 支付调起参数
     * @throws Exception 调用微信支付异常
     */
    @Override
    public OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception {
        Orders orders = checkPayOrder(ordersPaymentDTO.getOrderNumber(), BaseContext.getCurrentId());
        //后端要先确认：
        //- 这张订单存不存在
        //- 这张订单是不是当前用户自己的
        //- 这张订单现在是不是还允许支付

        orders.setPayMethod(ordersPaymentDTO.getPayMethod());
        ordersMapper.update(orders);
        //它是在把这次选择的支付方式，先写回订单里。
        // 比如当前这里是微信支付，就把支付方式记下来。

        if (!isRealPayEnabled()) {
            return OrderPaymentVO.builder()
                    .mockPay(true)
                    .timeStamp(String.valueOf(System.currentTimeMillis() / 1000))
                    .nonceStr(String.valueOf(System.currentTimeMillis()))
                    .signType("MOCK")
                    .paySign("MOCK_PAY_SIGN")
                    .packageStr("MOCK_PACKAGE")
                    .build();
        }
        // 即使在 mock 模式里，我们也仍然想保留“真实支付的结构”，
        // 只返回一组支付参数，而不直接把订单改成已支付

        User user = userMapper.getById(orders.getUserId());
        JSONObject jsonObject = weChatPayUtil.pay(
                ordersPaymentDTO.getOrderNumber(),
                new BigDecimal("0.01"),
                "PeakSend订单",
                user.getOpenid()
        );

        if ("ORDERPAID".equals(jsonObject.getString("code"))) {
            throw new OrderBusinessException("该订单已支付");
        }

        OrderPaymentVO orderPaymentVO = jsonObject.toJavaObject(OrderPaymentVO.class);
        orderPaymentVO.setMockPay(false);
        orderPaymentVO.setPackageStr(jsonObject.getString("package"));
        return orderPaymentVO;
    }

    /**
     * 支付成功回调后的订单状态更新
     *
     * @param outTradeNo 商户订单号
     */
    @Override
    @Transactional
    public void paySuccess(String outTradeNo) {
        Orders orders = ordersMapper.getByNumber(outTradeNo);
        if (orders == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        //如果订单都不存在，那当然不能继续改。

        if (Orders.PAID.equals(orders.getPayStatus()) && Orders.TO_BE_CONFIRMED.equals(orders.getStatus())) {
            return;
        }
        //- 如果这张订单已经是“已支付 + 待接单”
        //- 那就直接返回，不重复改了
        //目的是：避免对已经支付成功的订单重复执行状态流转。

        //真正改状态：
        Orders updateOrder = new Orders();
        updateOrder.setId(orders.getId());
        updateOrder.setStatus(Orders.TO_BE_CONFIRMED);
        updateOrder.setPayStatus(Orders.PAID);
        updateOrder.setPayMethod(orders.getPayMethod());
        updateOrder.setCheckoutTime(LocalDateTime.now());
        ordersMapper.update(updateOrder);
    }

    /**
     * 开发态模拟支付成功
     *
     * @param orderNumber 订单号
     */
    @Override
    @Transactional
    public void mockPaySuccess(String orderNumber) {
        if (isRealPayEnabled()) {
            throw new OrderBusinessException("当前为真实支付模式，不能使用模拟支付");
        }

        checkPayOrder(orderNumber, BaseContext.getCurrentId());
        paySuccess(orderNumber);
    }

    private Orders checkPayOrder(String orderNumber, Long userId) {
        Orders orders = ordersMapper.getByNumber(orderNumber);
        if (orders == null || !orders.getUserId().equals(userId)) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        if (!Orders.PENDING_PAYMENT.equals(orders.getStatus())) {
            if (Orders.PAID.equals(orders.getPayStatus())) {
                throw new OrderBusinessException("该订单已支付");
            }
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        return orders;
    }

    private boolean isRealPayEnabled() {
        return "real".equalsIgnoreCase(weChatProperties.getPayMode())
                && StringUtils.hasText(weChatProperties.getMchid())
                && StringUtils.hasText(weChatProperties.getMchSerialNo())
                && StringUtils.hasText(weChatProperties.getPrivateKeyFilePath())
                && StringUtils.hasText(weChatProperties.getApiV3Key())
                && StringUtils.hasText(weChatProperties.getWeChatPayCertFilePath())
                && StringUtils.hasText(weChatProperties.getNotifyUrl());
    }

    private String buildFullAddress(AddressBook addressBook) {
        StringBuilder stringBuilder = new StringBuilder();
        if (addressBook.getProvinceName() != null) {
            stringBuilder.append(addressBook.getProvinceName());
        }
        if (addressBook.getCityName() != null) {
            stringBuilder.append(addressBook.getCityName());
        }
        if (addressBook.getDistrictName() != null) {
            stringBuilder.append(addressBook.getDistrictName());
        }
        if (addressBook.getDetail() != null) {
            stringBuilder.append(addressBook.getDetail());
        }
        return stringBuilder.toString();
    }
}
