package com.xfjy.server.controller;

import com.xfjy.server.service.UsersService;
import com.xfjy.server.vo.CountsVo;
import com.xfjy.server.vo.PageResult;
import com.xfjy.server.vo.SettingsVo;
import com.xfjy.server.vo.UserInfoVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("users")
public class UsersController {

    private static final Logger LOGGER = LoggerFactory.getLogger(UsersController.class);

    @Autowired
    private UsersService usersService;

    /**
     * 用户资料 - 读取
     *
     * @param userID
     * @param huanxinID
     * @return
     */
    @GetMapping
    public ResponseEntity<UserInfoVo> queryUserInfo(@RequestParam(value = "userID", required = false) String userID,
                                                    @RequestParam(value = "huanxinID", required = false) String huanxinID) {
        try {
            //用户第一次接收到环信推来的信息，不带用户信息
            UserInfoVo userInfoVo = this.usersService.queryUserInfo(userID, huanxinID);
            if (null != userInfoVo) {
                return ResponseEntity.ok(userInfoVo);
            }
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    /**
     * 更新用户信息
     *
     * @param userInfoVo
     * @return
     */
    @PutMapping
    public ResponseEntity<Void> updateUserInfo(@RequestBody UserInfoVo userInfoVo){
        try {
            Boolean bool = this.usersService.updateUserInfo(userInfoVo);
            if(bool){
                return ResponseEntity.ok(null);
            }
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    /**
     * 互相喜欢，喜欢，粉丝 - 统计
     *
     * @return
     */
    @GetMapping("counts")
    public ResponseEntity<CountsVo> queryCounts(){
        try {
            CountsVo countsVo = this.usersService.queryCounts();
            if(null != countsVo){
                return ResponseEntity.ok(countsVo);
            }
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    /**
     * 互相关注、我关注、粉丝、谁看过我 - 翻页列表
     *
     * @param type     1 互相关注 2 我关注 3 粉丝 4 谁看过我
     * @param page
     * @param pageSize
     * @param nickname
     * @return
     */
    @GetMapping("friends/{type}")
    public ResponseEntity<PageResult> queryLikeList(@PathVariable("type") String type,
                                                    @RequestParam(value = "page", defaultValue = "1") Integer page,
                                                    @RequestParam(value = "pagesize", defaultValue = "10") Integer pageSize,
                                                    @RequestParam(value = "nickname", required = false) String nickname) {
        try {
            page = Math.max(1, page);
            PageResult pageResult = this.usersService.queryLikeList(Integer.valueOf(type), page, pageSize, nickname);
            return ResponseEntity.ok(pageResult);
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    /**
     * 取消喜欢
     *
     * @param userId
     * @return
     */
    @DeleteMapping("like/{uid}")
    public ResponseEntity<Void> disLike(@PathVariable("uid") Long userId) {
        try {
            this.usersService.disLike(userId);
            return ResponseEntity.ok(null);
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    /**
     * 关注粉丝
     *
     * @param userId
     * @return
     */
    @PostMapping("fans/{uid}")
    public ResponseEntity<Void> likeFan(@PathVariable("uid") Long userId){
        try {
            this.usersService.likeFan(userId);
            return ResponseEntity.ok(null);
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    /**
     * 查询配置
     *
     * @return
     */
    @GetMapping("settings")
    public ResponseEntity<SettingsVo> querySettings() {
        try {
            SettingsVo settingsVo = this.usersService.querySettings();
            if (null != settingsVo) {
                return ResponseEntity.ok(settingsVo);
            }
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    /**
     * 设置陌生人问题
     *
     * @return
     */
    @PostMapping("questions")
    public ResponseEntity<Void> saveQuestions(@RequestBody Map<String, String> param) {
        try {
            String content = param.get("content");
            this.usersService.saveQuestions(content);
            return ResponseEntity.ok(null);
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    /**
     * 查询黑名单
     *
     * @param page
     * @param pagesize
     * @return
     */
    @GetMapping("blacklist")
    public ResponseEntity<PageResult> queryBlacklist(@RequestParam(value = "page", defaultValue = "1") Integer page,
                                                     @RequestParam(value = "pagesize", defaultValue = "10") Integer pagesize) {
        try {
            PageResult pageResult = this.usersService.queryBlacklist(page, pagesize);
            return ResponseEntity.ok(pageResult);
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    /**
     * 移除黑名单
     *
     * @return
     */
    @DeleteMapping("blacklist/{uid}")
    public ResponseEntity<Void> delBlacklist(@PathVariable("uid") Long userId) {
        try {
            this.usersService.delBlacklist(userId);
            return ResponseEntity.ok(null);
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    /**
     * 更新通知设置
     *
     * @param param
     * @return
     */
    @PostMapping("notifications/setting")
    public ResponseEntity<Void> updateNotification(@RequestBody Map<String, Boolean> param) {
        try {
            Boolean likeNotification = param.get("likeNotification");
            Boolean pinglunNotification = param.get("pinglunNotification");
            Boolean gonggaoNotification = param.get("gonggaoNotification");

            this.usersService.updateNotification(likeNotification, pinglunNotification, gonggaoNotification);
            return ResponseEntity.ok(null);
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    /**
     * 提交举报
     *
     * @param param targetUserId-被举报用户ID, targetType-USER/POST/COMMENT/MESSAGE,
     *              targetId-举报对象ID, reason-举报原因, description-详细描述, evidence-证据URL
     * @return 200=提交成功；400=参数无效（如举报自己）；500=服务异常
     */
    @PostMapping("report")
    public ResponseEntity<Void> submitReport(@RequestBody Map<String, Object> param) {
        try {
            Object targetUserIdObj = param.get("targetUserId");
            if (targetUserIdObj == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            Long targetUserId = Long.valueOf(targetUserIdObj.toString());
            String targetType = param.get("targetType") != null ? param.get("targetType").toString() : null;
            Long targetId = param.get("targetId") != null ? Long.valueOf(param.get("targetId").toString()) : null;
            String reason = param.get("reason") != null ? param.get("reason").toString() : null;
            String description = param.get("description") != null ? param.get("description").toString() : null;
            String evidence = param.get("evidence") != null ? param.get("evidence").toString() : null;

            if (reason == null || reason.trim().isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }

            Boolean success = this.usersService.submitReport(targetUserId, targetType, targetId,
                    reason, description, evidence);
            return Boolean.TRUE.equals(success) ? ResponseEntity.ok().build()
                    : ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    /**
     * 注销账号（软删除，需短信验证码校验）
     *
     * @param param code-短信验证码（需先调 POST /user/sendCode 获取）
     * @return 200=注销成功；400=验证码错误；500=服务异常
     */
    @PostMapping("logoff")
    public ResponseEntity<Void> logoff(@RequestBody Map<String, String> param) {
        try {
            String code = param.get("code");
            Boolean success = this.usersService.logoff(code);
            return Boolean.TRUE.equals(success) ? ResponseEntity.ok().build()
                    : ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            LOGGER.error("接口处理异常", e);
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

}
