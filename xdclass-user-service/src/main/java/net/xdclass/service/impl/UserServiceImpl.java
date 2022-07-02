package net.xdclass.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import net.xdclass.enums.BizCodeEnum;
import net.xdclass.enums.SendCodeEnum;
import net.xdclass.fegin.CouponFeginService;
import net.xdclass.interceptor.LoginInterceptor;
import net.xdclass.mapper.UserMapper;
import net.xdclass.model.LoginUser;
import net.xdclass.model.UserDO;
import net.xdclass.request.UserLoginRequest;
import net.xdclass.request.UserRegisterRequest;
import net.xdclass.service.NotifyService;
import net.xdclass.service.UserService;
import net.xdclass.util.CommonUtil;
import net.xdclass.util.JWTUtil;
import net.xdclass.util.JsonData;
import net.xdclass.vo.NewUserCouponRequest;
import net.xdclass.vo.UserVO;
import org.apache.catalina.User;
import org.apache.commons.codec.digest.Md5Crypt;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

@Service
@Slf4j
public class UserServiceImpl implements UserService {

    @Autowired
    private NotifyService notifyService;

    @Autowired
    private CouponFeginService couponFeginService;

    @Autowired
    private UserMapper userMapper;

    /**
     * 用户注册
     * 邮箱验证码验证
     * 密码加密（TODO）
     * 账号唯一性检查(TODO)
     * 插入数据库
     * 新注册用户福利发放(TODO)
     *
     * @param registerRequest
     * @return
     */
    @Override
    @Transactional(rollbackFor=Exception.class,propagation= Propagation.REQUIRED)
    //@GlobalTransactional
    public JsonData register(UserRegisterRequest registerRequest) {

        //邮箱验证码验证
        boolean checkCode = false;
        if (StringUtils.isNotBlank(registerRequest.getMail())) {
            checkCode = notifyService.checkCode(SendCodeEnum.USER_REGISTER, registerRequest.getMail(), registerRequest.getCode());
        }

        if (!checkCode) {
            return JsonData.buildResult(BizCodeEnum.CODE_ERROR);
        }

        UserDO userDO = new UserDO();
        BeanUtils.copyProperties(registerRequest, userDO);
        userDO.setCreateTime(new Date());
        userDO.setSlogan("道阻且长，行则将至。");
        //设置密码
        //生成密钥
        userDO.setSecret("$1$" + CommonUtil.getStringNumRandom(8));
        //密码+加盐处理
        String cryptPwd = Md5Crypt.md5Crypt(registerRequest.getPwd().getBytes(), userDO.getSecret());
        userDO.setPwd(cryptPwd);

        //账号唯一性检查
        if (checkUnique(userDO.getMail())) {
            int rows = userMapper.insert(userDO);
            log.info("rows:{},注册成功:{}", rows, userDO.toString());

            //新用户注册成功，初始化信息，发放福利等 TODO
            userRegisterInitTask(userDO);

            //模拟异常
            //int b = 1/0;
            return JsonData.buildSuccess();
        } else {
            return JsonData.buildResult(BizCodeEnum.ACCOUNT_REPEAT);
        }
    }

    /**
     * 1.根据mail去找有没有这条记录
     * 2.有的话，则用密钥+用户传递的明文密码，进行加密，再和数据库的密文进行匹配
     *
     * @param userLoginRequest
     * @return
     */
    @Override
    public JsonData login(UserLoginRequest userLoginRequest) {
        List<UserDO> userDOList = userMapper.selectList(new QueryWrapper<UserDO>().eq("mail", userLoginRequest.getMail()));

        if (userDOList != null && userDOList.size() == 1) {
            //已注册
            UserDO userDO = userDOList.get(0);
            String cryptPwd = Md5Crypt.md5Crypt(userLoginRequest.getPwd().getBytes(), userDO.getSecret());
            if (cryptPwd.equals(userDO.getPwd())) {
                //登录成功，生成token TODO
                LoginUser loginUser = LoginUser.builder().build();
                BeanUtils.copyProperties(userDO, loginUser);
                String token = JWTUtil.geneJsonWebToken(loginUser);
                return JsonData.buildSuccess(token);
            } else {
                return JsonData.buildResult(BizCodeEnum.ACCOUNT_PWD_ERROR);
            }
        } else {
            //未注册
            return JsonData.buildResult(BizCodeEnum.ACCOUNT_UNREGISTER);
        }
    }

    @Override
    public UserVO findUserDetail() {
        LoginUser loginUser = LoginInterceptor.threadLocal.get();
        UserDO userDO = userMapper.selectOne(new QueryWrapper<UserDO>().eq("id", loginUser.getId()));
        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(userDO, userVO);
        return userVO;
    }

    /**
     * 校验用户账号唯一
     *
     * @param mail
     * @return
     */
    private boolean checkUnique(String mail) {
        QueryWrapper queryWrapper = new QueryWrapper<UserDO>().eq("mail", mail);
        List<UserDO> list = userMapper.selectList(queryWrapper);
        return list.size() > 0 ? false : true;
    }

    /**
     * 用户注册，初始化福利信息 TODO
     *
     * @param userDO
     */
    private void userRegisterInitTask(UserDO userDO) {
        NewUserCouponRequest newUserCouponRequest = new NewUserCouponRequest();
        newUserCouponRequest.setName(userDO.getName());
        newUserCouponRequest.setUserId(userDO.getId());
        JsonData jsonData = couponFeginService.addNewUserCoupon(newUserCouponRequest);
//        if (jsonData.getCode()!=0){
//            throw new RuntimeException("发放优惠券异常");
//        }
        log.info("发放新用户注册优惠券:{},结果:{}",newUserCouponRequest,jsonData.toString());
    }
}
