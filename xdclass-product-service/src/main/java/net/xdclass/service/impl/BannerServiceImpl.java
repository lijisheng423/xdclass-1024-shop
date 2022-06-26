package net.xdclass.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import net.xdclass.mapper.BannerMapper;
import net.xdclass.model.BannerDO;
import net.xdclass.service.BannerService;
import net.xdclass.vo.BannerVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.Banner;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class BannerServiceImpl implements BannerService {

    @Autowired
    private BannerMapper bannerMapper;

    @Override
    public List<BannerVO> list() {
        List<BannerDO> bannerDOList = bannerMapper.selectList(new QueryWrapper<BannerDO>().orderByAsc("weight"));
        List<BannerVO> bannerVOList = bannerDOList.stream().map(obj -> beanProcess(obj)).collect(Collectors.toList());
        return bannerVOList;
    }

    private BannerVO beanProcess(BannerDO bannerDO) {
        BannerVO bannerVO = new BannerVO();
        BeanUtils.copyProperties(bannerDO,bannerVO);
        return bannerVO;
    }


}
