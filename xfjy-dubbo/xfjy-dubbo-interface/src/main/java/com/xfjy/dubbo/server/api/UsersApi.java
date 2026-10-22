package com.xfjy.dubbo.server.api;

import com.xfjy.dubbo.server.pojo.Users;
import com.xfjy.dubbo.server.vo.PageInfo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.List;

@HttpExchange("/api/users")
public interface UsersApi {

    /**
     * 保存好友
     */
    @PostExchange("/saveUsers")
    @PostMapping("/saveUsers")
    String saveUsers(@RequestBody Users users);

    /**
     * 根据用户id查询Users列表
     */
    @GetExchange("/queryAllUsersList")
    @GetMapping("/queryAllUsersList")
    List<Users> queryAllUsersList(@RequestParam("userId") Long userId);

    /**
     * 根据用户id查询Users列表(分页查询)
     */
    @GetExchange("/queryUsersList")
    @GetMapping("/queryUsersList")
    PageInfo<Users> queryUsersList(@RequestParam("userId") Long userId,
                                   @RequestParam("page") Integer page,
                                   @RequestParam("pageSize") Integer pageSize);
}