package com.galaxy.project.web;

import com.galaxy.project.annotation.VerifyIdempotent;
import com.galaxy.project.common.BaseController;
import com.galaxy.project.core.Result;
import com.galaxy.project.core.ResultGenerator;
import com.galaxy.project.model.User;
import com.galaxy.project.service.UserService;
import com.galaxy.project.utils.Logger;
import com.galaxy.project.vo.LoginVo;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;

/**
* 用户管理模块
*/
@RestController
@RequestMapping("/gateway/ucenter/user")
@Api(tags = {"/gateway/ucenter/user"}, description = "用户管理模块")
public class UserController extends BaseController {

    @Autowired
    private UserService userService;

    @ApiOperation(value = "用户退出", notes = "用户退出")
    @RequestMapping(value = "/logout", method = {RequestMethod.POST, RequestMethod.GET})
    public Result logout(@RequestParam Long userId) {
        Logger.info(this, "/galaxy/user/logout 用户退出接口入参 : " + userId);
        return userService.logout(userId);
    }

    @ApiOperation(value = "生成验证码", notes = "生成验证码")
    @RequestMapping(value = "/captcha", method = {RequestMethod.POST, RequestMethod.GET})
    public Result captcha(HttpServletRequest request, HttpServletResponse response) throws Exception {
        return userService.captcha();
    }

    @ApiOperation(value = "用户登录", notes = "用户登录")
    @RequestMapping(value = "/login", method = {RequestMethod.POST, RequestMethod.GET})
    public Result login(@RequestBody LoginVo vo, HttpServletRequest request) {
        Logger.info(this, "/galaxy/user/login 用户登录接口入参 :" + vo);
        return userService.login(vo);
    }

    @ApiOperation(value = "用户注册", notes = "用户注册")
    @RequestMapping(value = "/register", method = {RequestMethod.POST, RequestMethod.GET})
    @VerifyIdempotent(value = "/user/register", expireMillis = 10000L)
    public Result add(@RequestBody User user) {
        Logger.info(this, "/galaxy/user/add 用户注册接口入参 :" + user);
        return userService.add(user);
    }

    @ApiOperation(value = "逻辑删除用户", notes = "逻辑删除用户")
    @RequestMapping(value = "/delete", method = {RequestMethod.POST, RequestMethod.GET})
    public Result delete(@RequestParam Long id) {
        User user = new User();
        user.setId(id);
        user.setIsDelete(true);
        userService.update(user);
        return ResultGenerator.genSuccessResult();
    }

    @ApiOperation(value = "修改用户", notes = "修改用户")
    @RequestMapping(value = "/update", method = {RequestMethod.POST, RequestMethod.GET})
    public Result update(@RequestBody User user) {
        userService.update(user);
        return ResultGenerator.genSuccessResult(user);
    }

    @ApiOperation(value = "获取用户详情", notes = "获取用户详情")
    @RequestMapping(value = "/detail", method = {RequestMethod.POST, RequestMethod.GET})
    public Result detail(@RequestParam Long id) {
        User user = userService.findById(id);
        return ResultGenerator.genSuccessResult(user);
    }

    @ApiOperation(value = "分页模糊查询用户", notes = "分页模糊查询用户")
    @RequestMapping(value = "/findByModal", method = {RequestMethod.POST,RequestMethod.GET})
    public Result list(@RequestParam(defaultValue="1",required=false) Integer page,@RequestParam(defaultValue="20",required=false) Integer size,
                       @RequestBody(required =false) User user) {
        PageHelper.startPage(page, size);
        user.setIsDelete(false);
        List<User> list = userService.findByModel(user);
        PageInfo pageInfo = new PageInfo(list);
        return ResultGenerator.genSuccessResult(pageInfo);
    }

}
