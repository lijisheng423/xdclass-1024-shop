package net.xdclass.controller;


import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import net.xdclass.service.BannerService;
import net.xdclass.util.JsonData;
import net.xdclass.vo.BannerVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 二当家小D
 * @since 2022-06-26
 */
@Api(tags = "轮播图模块")
@RestController
@RequestMapping("/api/banner/v1")
public class BannerController {

    @Autowired
    private BannerService bannerService;



    @ApiOperation("轮播图列表接口")
    @GetMapping("list")
    private JsonData list(){
        List<BannerVO> list = bannerService.list();
        return JsonData.buildSuccess(list);
    }

}

