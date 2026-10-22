package com.xfjy.server.utils;

import com.xfjy.server.pojo.Admin;

public class AdminThreadLocal {

    private static final ThreadLocal<Admin> LOCAL = new ThreadLocal<>();

    private AdminThreadLocal() {

    }

    public static void set(Admin admin) {
        LOCAL.set(admin);
    }

    public static Admin get() {
        return LOCAL.get();
    }
}