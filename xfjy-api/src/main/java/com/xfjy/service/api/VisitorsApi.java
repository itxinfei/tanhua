package com.xfjy.service.api;

import com.xfjy.service.pojo.Visitors;
import com.xfjy.service.vo.PageInfo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

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

    /**
     * 记录访客（查看他人详情时写入，隐身访问权益用户不写入）
     */
    @PostExchange("/saveVisitor")
    @PostMapping("/saveVisitor")
    void saveVisitor(@RequestBody Visitors visitors);
}