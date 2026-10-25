package com.xfjy.service.api;

import com.xfjy.service.pojo.Visitors;
import com.xfjy.service.vo.PageInfo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import java.util.List;

@HttpExchange("/api/visitors")
public interface VisitorsApi {

    /**
     * 按照时间倒序排序，查询最近的访客信息（指定数量）
     */
    @GetExchange("/topVisitorByNum")
    @GetMapping("/topVisitorByNum")
    List<Visitors> topVisitor(@RequestParam("userId") Long userId,
                              @RequestParam("num") Integer num);

    /**
     * 按照时间倒序排序，查询指定日期之后的访客信息
     */
    @GetExchange("/topVisitorByDate")
    @GetMapping("/topVisitorByDate")
    List<Visitors> topVisitor(@RequestParam("userId") Long userId,
                              @RequestParam("date") Long date);

    /**
     * 按照时间倒序排序，分页查询访客信息
     */
    @GetExchange("/topVisitorByPage")
    @GetMapping("/topVisitorByPage")
    PageInfo<Visitors> topVisitor(@RequestParam("userId") Long userId,
                                  @RequestParam("page") Integer page,
                                  @RequestParam("pageSize") Integer pageSize);
}