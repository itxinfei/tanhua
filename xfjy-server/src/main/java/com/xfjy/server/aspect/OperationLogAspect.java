package com.xfjy.server.aspect;

import com.xfjy.server.mapper.OperationLogMapper;
import com.xfjy.server.pojo.Admin;
import com.xfjy.server.pojo.OperationLog;
import com.xfjy.server.utils.AdminThreadLocal;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import java.lang.reflect.Method;
import java.util.Date;

/**
 * 后台操作日志切面：自动记录 Admin*Controller 的写操作（POST/PUT/DELETE）
 *
 * <p>排除项：AdminController 的 login/logout（非业务操作）。
 * module 从类名推断（AdminUserController → USER），action 从 HTTP 方法+方法名推断。
 * 日志记录失败不影响业务流程。</p>
 */
@Aspect
@Component
public class OperationLogAspect {

    private static final Logger LOGGER = LoggerFactory.getLogger(OperationLogAspect.class);

    @Autowired
    private OperationLogMapper operationLogMapper;

    /**
     * 拦截 controller 包下所有方法，在成功返回后判断是否需要记录
     */
    @AfterReturning("execution(* com.xfjy.server.controller.*Controller.*(..))")
    public void recordOperationLog(JoinPoint joinPoint) {
        try {
            Method method = getMethod(joinPoint);
            if (method == null) {
                return;
            }

            // 只记录写操作
            String httpMethod = resolveHttpMethod(method);
            if (httpMethod == null) {
                return;
            }

            // 排除登录/登出
            String methodName = method.getName();
            if ("login".equals(methodName) || "logout".equals(methodName)) {
                return;
            }

            // 只记录 Admin 开头的 Controller（后台操作）
            String className = joinPoint.getTarget().getClass().getSimpleName();
            if (!className.startsWith("Admin")) {
                return;
            }

            OperationLog log = new OperationLog();

            // 管理员信息
            Admin admin = AdminThreadLocal.get();
            if (admin != null) {
                log.setAdminId(admin.getId());
                log.setAdminUsername(admin.getUsername());
            } else {
                log.setAdminId(0L);
                log.setAdminUsername("unknown");
            }

            // 模块：AdminUserController → USER
            log.setModule(resolveModule(className));

            // 操作类型
            log.setAction(resolveAction(httpMethod, methodName));

            // 操作对象ID：从 URI 路径末段提取
            HttpServletRequest request = getRequest();
            if (request != null) {
                String uri = request.getRequestURI();
                log.setTargetId(extractTargetId(uri));
                log.setIp(resolveIp(request));
                log.setDetail(methodName + " " + uri);
            } else {
                log.setDetail(methodName);
            }

            log.setCreated(new Date());
            this.operationLogMapper.insert(log);
        } catch (Exception e) {
            // 日志记录失败不影响业务
            LOGGER.warn("操作日志记录失败: {}", joinPoint.getSignature().toShortString(), e);
        }
    }

    /**
     * 获取当前方法（处理代理场景）
     */
    private Method getMethod(JoinPoint joinPoint) {
        try {
            String methodName = joinPoint.getSignature().getName();
            Class<?>[] paramTypes = new Class[joinPoint.getArgs().length];
            for (int i = 0; i < joinPoint.getArgs().length; i++) {
                paramTypes[i] = joinPoint.getArgs()[i] != null
                        ? joinPoint.getArgs()[i].getClass() : Object.class;
            }
            return joinPoint.getTarget().getClass().getMethod(methodName, paramTypes);
        } catch (NoSuchMethodException e) {
            // 参数类型匹配失败（泛型擦除等），尝试无参/单参回退
            try {
                String methodName = joinPoint.getSignature().getName();
                for (Method m : joinPoint.getTarget().getClass().getMethods()) {
                    if (m.getName().equals(methodName) && m.getParameterCount() == joinPoint.getArgs().length) {
                        return m;
                    }
                }
            } catch (Exception ignored) {
            }
            return null;
        }
    }

    /**
     * 判断 HTTP 方法：只有写操作返回非 null
     */
    private String resolveHttpMethod(Method method) {
        if (method.isAnnotationPresent(PostMapping.class)) {
            return "POST";
        }
        if (method.isAnnotationPresent(PutMapping.class)) {
            return "PUT";
        }
        if (method.isAnnotationPresent(DeleteMapping.class)) {
            return "DELETE";
        }
        return null;
    }

    /**
     * 类名转模块名：AdminUserController → USER，AdminMovementController → MOVEMENT
     */
    private String resolveModule(String className) {
        String name = className;
        if (name.startsWith("Admin")) {
            name = name.substring(5);
        }
        if (name.endsWith("Controller")) {
            name = name.substring(0, name.length() - 10);
        }
        // 驼峰转大写下划线
        StringBuilder sb = new StringBuilder();
        for (char c : name.toCharArray()) {
            if (Character.isUpperCase(c) && sb.length() > 0) {
                sb.append('_');
            }
            sb.append(Character.toUpperCase(c));
        }
        return sb.toString();
    }

    /**
     * 推断操作类型
     */
    private String resolveAction(String httpMethod, String methodName) {
        String lower = methodName.toLowerCase();
        if (lower.contains("freeze") || lower.contains("disable")) {
            return "FREEZE";
        }
        if (lower.contains("unfreeze") || lower.contains("enable")) {
            return "UNFREEZE";
        }
        if (lower.contains("approve")) {
            return "APPROVE";
        }
        if (lower.contains("reject")) {
            return "REJECT";
        }
        if (lower.contains("handle")) {
            return "HANDLE";
        }
        if (lower.contains("publish") && !"POST".equals(httpMethod)) {
            return "PUBLISH";
        }
        if (lower.contains("offline")) {
            return "OFFLINE";
        }
        if (lower.contains("reset")) {
            return "RESET_PASSWORD";
        }
        if (lower.contains("status")) {
            return "TOGGLE_STATUS";
        }
        if (lower.contains("refresh")) {
            return "REFRESH";
        }
        if (lower.contains("reply")) {
            return "REPLY";
        }
        if (lower.contains("close")) {
            return "CLOSE";
        }
        switch (httpMethod) {
            case "POST":
                return "CREATE";
            case "DELETE":
                return "DELETE";
            default:
                return "UPDATE";
        }
    }

    /**
     * 从 URI 提取操作对象ID（最后一个路径段）
     * 如 /admin/users/123/freeze → 123
     */
    private String extractTargetId(String uri) {
        if (uri == null) {
            return null;
        }
        // 去掉查询参数
        int queryIdx = uri.indexOf('?');
        if (queryIdx > 0) {
            uri = uri.substring(0, queryIdx);
        }
        String[] segments = uri.split("/");
        // 从后往前找第一个非动作段（数字或非动作词）
        for (int i = segments.length - 1; i >= 0; i--) {
            String seg = segments[i];
            if (seg.isEmpty()) {
                continue;
            }
            // 跳过动作段
            if (isActionSegment(seg)) {
                continue;
            }
            return seg;
        }
        return null;
    }

    private boolean isActionSegment(String seg) {
        return "freeze".equals(seg) || "unfreeze".equals(seg) || "approve".equals(seg)
                || "reject".equals(seg) || "handle".equals(seg) || "publish".equals(seg)
                || "offline".equals(seg) || "reset-password".equals(seg) || "status".equals(seg)
                || "refresh".equals(seg) || "reply".equals(seg) || "close".equals(seg)
                || "batch-approve".equals(seg) || "batch-reject".equals(seg)
                || "batch-handle".equals(seg);
    }

    /**
     * 获取当前请求
     */
    private HttpServletRequest getRequest() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs != null ? attrs.getRequest() : null;
    }

    /**
     * 解析客户端IP（支持代理转发）
     */
    private String resolveIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            int commaIdx = ip.indexOf(',');
            return commaIdx > 0 ? ip.substring(0, commaIdx).trim() : ip.trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }
        return request.getRemoteAddr();
    }
}