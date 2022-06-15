package net.xdclass.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import net.xdclass.enums.AddressStatusEnum;
import net.xdclass.interceptor.LoginInterceptor;
import net.xdclass.mapper.AddressMapper;
import net.xdclass.model.AddressDO;
import net.xdclass.model.LoginUser;
import net.xdclass.request.AddressAddRequest;
import net.xdclass.service.AddressService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
@Slf4j
public class AddressServiceImpl implements AddressService {

    @Autowired
    private AddressMapper addressMapper;

    @Override
    public AddressDO detail(Long id) {
        AddressDO addressDO = addressMapper.selectOne(new QueryWrapper<AddressDO>().eq("id", id));
        return addressDO;
    }

    /**
     * 新增收货地址
     *
     * @param addressAddRequest
     * @return
     */
    @Override
    public void add(AddressAddRequest addressAddRequest) {
        LoginUser loginUser = LoginInterceptor.threadLocal.get();
        AddressDO addressDO = new AddressDO();
        addressDO.setCreateTime(new Date());
        addressDO.setUserId(loginUser.getId());
        BeanUtils.copyProperties(addressAddRequest, addressDO);

        //是否有默认收货地址
        if (addressDO.getDefaultStatus() == AddressStatusEnum.DEFAULT_STATUS.getStatus()) {
            //查找数据库是否有默认地址
            AddressDO defaultAddressDo = addressMapper.selectOne(new QueryWrapper<AddressDO>()
                    .eq("user_id", loginUser.getId())
                    .eq("default_status", AddressStatusEnum.DEFAULT_STATUS.getStatus()));
            if (defaultAddressDo != null) {
                //修改为非默认收货地址
                defaultAddressDo.setDefaultStatus(AddressStatusEnum.COMMON_STATUS.getStatus());
                addressMapper.update(defaultAddressDo, new QueryWrapper<AddressDO>()
                        .eq("id", defaultAddressDo.getId()));
            }
        }

        int rows = addressMapper.insert(addressDO);

        log.info("新增收货地址:rows={},data={}", rows, addressDO);
    }
}
