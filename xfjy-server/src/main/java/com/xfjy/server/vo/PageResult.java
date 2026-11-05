package com.xfjy.server.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PageResult {

    private Integer counts = 0;//总记录数
    private Integer pagesize;//页大小
    private Integer pages = 0;//总页数
    private Integer page;//当前页码
    private List<?> items = Collections.emptyList(); //列表

    /**
     * 「附近」无坐标/半径内无数据时回退全站热门标记。
     * 前端据此展示「未开启定位，正在展示全站热门」，而不是把回退数据当成附近内容或显示空页。
     */
    private Boolean fallback;

}
