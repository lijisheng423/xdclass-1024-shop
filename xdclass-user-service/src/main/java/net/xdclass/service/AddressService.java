package net.xdclass.service;

import net.xdclass.model.AddressDO;
import net.xdclass.request.AddressAddRequest;

public interface AddressService {

    /**
     * 查找指定地址
     *
     * @param id
     * @return
     */
    AddressDO detail(Long id);

    /**
     * 新增收货地址
     *
     * @param addressAddRequest
     * @return
     */
    void add(AddressAddRequest addressAddRequest);
}
