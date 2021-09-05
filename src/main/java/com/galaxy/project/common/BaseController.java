package com.galaxy.project.common;

import com.galaxy.project.utils.Constants;
import com.galaxy.project.utils.RedisService;
import com.galaxy.project.vo.SysUserVo;
import org.springframework.beans.factory.annotation.Autowired;

import javax.servlet.http.HttpServletRequest;

public class BaseController {

    @Autowired
    private HttpServletRequest request;

    @Autowired
    private RedisService redisService;

    public String getUserId() {
        String token = request.getHeader(Constants.TOKEN_NAME);
        SysUserVo sysUserVo;
        try {
            sysUserVo = (SysUserVo)redisService.get(Constants.REDIS_KEY_LOGIN+token);
        }catch (Exception e){
            return null;
            //throw new RedisException();
        }
        if (null == sysUserVo) {
            return null;
        }
        return String.valueOf(sysUserVo.getUserId());
    }

    public SysUserVo getUser() {
        String token = request.getHeader(Constants.TOKEN_NAME);
        SysUserVo sysUserVo;
        try {
            sysUserVo = (SysUserVo)redisService.get(Constants.REDIS_KEY_LOGIN + token);
        }catch (Exception e){
            return null;
            //throw new RedisException();
        }
        if (null == sysUserVo) {
            return null;
        }
        return sysUserVo;
    }

}
