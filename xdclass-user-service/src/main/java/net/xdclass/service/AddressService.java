package net.xdclass.service;

import net.xdclass.model.AddressDO;
import net.xdclass.request.AddressAddRequest;
import net.xdclass.vo.AddressVO;

import java.util.List;

public interface AddressService {

    /**
     * 查找指定地址
     *
     * @param id
     * @return
     */
    AddressVO detail(Long id);

    /**
     * 新增收货地址
     *
     * @param addressAddRequest
     * @return
     */
    void add(AddressAddRequest addressAddRequest);

    int del(Long addressId);

    /**
     * 查找用户全部收货地址
     *
     * @return
     */
    List<AddressVO> listUserAllAddress();
}
