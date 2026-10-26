package com.xfjy.server.service;

import org.springframework.beans.factory.annotation.Autowired;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xfjy.service.api.HuanXinApi;
import com.xfjy.service.api.QuanZiApi;
import com.xfjy.service.api.UserInfoApi;
import com.xfjy.service.api.UsersApi;
import com.xfjy.service.pojo.Comment;
import com.xfjy.service.pojo.UserInfo;
import com.xfjy.service.pojo.Users;
import com.xfjy.service.vo.PageInfo;
import com.xfjy.service.vo.UserInfoQueryParam;
import com.xfjy.server.pojo.Announcement;
import com.xfjy.server.pojo.User;
import com.xfjy.server.utils.UserThreadLocal;
import com.xfjy.server.vo.Contacts;
import com.xfjy.server.vo.MessageAnnouncement;
import com.xfjy.server.vo.MessageLike;
import com.xfjy.server.vo.PageResult;
import org.apache.commons.lang3.StringUtils;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;

@Service
public class IMService {

    @Autowired
    private UsersApi usersApi;

    @Autowired
    private HuanXinApi huanXinApi;

    @Autowired
    private UserInfoApi userInfoApi;

    @Autowired
    private QuanZiApi quanZiApi;

    @Autowired
    private AnnouncementService announcementService;

    public Boolean contactUser(Long userId) {
        //需要做2件事：1、保存数据到MongoDB，2、好友关系注册到环信

        Users users = new Users();
        users.setUserId(UserThreadLocal.get().getId());
        users.setFriendId(userId);

        // 1、保存数据到MongoDB
        this.usersApi.saveUsers(users);

        // 2、好友关系注册到环信（契约调用 message-service）
        this.huanXinApi.registerContact(users.getUserId(), users.getFriendId());

        return true;
    }

    public PageResult queryContactsList(Integer page, Integer pageSize, String keyword) {

        User user = UserThreadLocal.get();

        List<Users> usersList = null;

        if (StringUtils.isNotEmpty(keyword)) {
            usersList = this.usersApi.queryAllUsersList(user.getId());
        } else {
            PageInfo<Users> pageInfo = this.usersApi.queryUsersList(user.getId(), page, pageSize);
            usersList = pageInfo.getRecords();
        }

        List<Long> fUserIds = new ArrayList<>();
        for (Users users : usersList) {
            fUserIds.add(users.getFriendId());
        }

        //查询用户（好友）的信息
        UserInfoQueryParam queryParam = new UserInfoQueryParam();
        queryParam.setUserIds(fUserIds);
        if (StringUtils.isNotEmpty(keyword)) {
            queryParam.setNickName(keyword);
        }
        List<UserInfo> userInfoList = this.userInfoApi.queryUserInfoList(queryParam);

        List<Contacts> contactsList = new ArrayList<>();

        if(StringUtils.isEmpty(keyword)){
            for (Users users : usersList) {
                for (UserInfo userInfo : userInfoList) {
                    if(users.getFriendId().longValue() == userInfo.getId().longValue()){

                        Contacts contacts = new Contacts();
                        contacts.setCity(StringUtils.substringBefore(userInfo.getCity(),"-"));
                        contacts.setUserId(userInfo.getUserId().toString());
                        contacts.setNickname(userInfo.getNickName());
                        contacts.setGender(userInfo.getSex().name().toLowerCase());
                        contacts.setAvatar(userInfo.getLogo());
                        contacts.setAge(userInfo.getAge());

                        contactsList.add(contacts);

                        break;
                    }
                }
            }
        }else {
            for (UserInfo userInfo : userInfoList) {
                Contacts contacts = new Contacts();
                contacts.setCity(StringUtils.substringBefore(userInfo.getCity(),"-"));
                contacts.setUserId(userInfo.getUserId().toString());
                contacts.setNickname(userInfo.getNickName());
                contacts.setGender(userInfo.getSex().name().toLowerCase());
                contacts.setAvatar(userInfo.getLogo());
                contacts.setAge(userInfo.getAge());

                contactsList.add(contacts);
            }
        }

        PageResult pageResult = new PageResult();
        pageResult.setPagesize(pageSize);
        pageResult.setPage(page);
        pageResult.setPages(0);
        pageResult.setCounts(0);
        pageResult.setItems(contactsList);

        return pageResult;
    }

    public PageResult queryMessageLikeList(Integer page, Integer pageSize) {
        return this.messageCommentList(1, page, pageSize);
    }

    public PageResult queryMessageCommentList(Integer page, Integer pageSize) {
        return this.messageCommentList(2, page, pageSize);
    }

    public PageResult queryMessageLoveList(Integer page, Integer pageSize) {
        return this.messageCommentList(3, page, pageSize);
    }

    private PageResult messageCommentList(Integer type, Integer page, Integer pageSize) {
        User user = UserThreadLocal.get();
        PageInfo<Comment> pageInfo = this.quanZiApi.queryCommentListByUser(user.getId(), type, page, pageSize);

        PageResult pageResult = new PageResult();
        pageResult.setPage(page);
        pageResult.setPages(0);
        pageResult.setCounts(0);
        pageResult.setPagesize(pageSize);

        List<Comment> records = pageInfo.getRecords();
        if(CollectionUtils.isEmpty(records)){
            return pageResult;
        }

        List<Long> userIds = new ArrayList<>();
        for (Comment comment : records) {
            userIds.add(comment.getUserId());
        }

        UserInfoQueryParam queryParam = new UserInfoQueryParam();
        queryParam.setUserIds(userIds);
        List<UserInfo> userInfoList = this.userInfoApi.queryUserInfoList(queryParam);

        List<MessageLike> messageLikeList = new ArrayList<>();
        for (Comment record : records) {
            for (UserInfo userInfo : userInfoList) {
                if (userInfo.getUserId().longValue() == record.getUserId().longValue()) {

                    MessageLike messageLike = new MessageLike();
                    messageLike.setId(record.getId().toHexString());
                    messageLike.setAvatar(userInfo.getLogo());
                    messageLike.setNickname(userInfo.getNickName());
                    messageLike.setUserId(record.getUserId());
                    messageLike.setCreateDate(LocalDateTime.ofInstant(Instant.ofEpochMilli(record.getCreated()), ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));

                    messageLikeList.add(messageLike);
                    break;
                }
            }
        }

        pageResult.setItems(messageLikeList);
        return pageResult;
    }


    public PageResult queryMessageAnnouncementList(Integer page, Integer pageSize) {
        IPage<Announcement> announcementPage = this.announcementService.queryList(page, pageSize);

        List<MessageAnnouncement> messageAnnouncementList = new ArrayList<>();

        for (Announcement record : announcementPage.getRecords()) {
            MessageAnnouncement messageAnnouncement = new MessageAnnouncement();
            messageAnnouncement.setId(record.getId().toString());
            messageAnnouncement.setTitle(record.getTitle());
            messageAnnouncement.setDescription(record.getDescription());
            messageAnnouncement.setCreateDate(LocalDateTime.ofInstant(record.getCreated().toInstant(), ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));

            messageAnnouncementList.add(messageAnnouncement);
        }

        PageResult pageResult = new PageResult();
        pageResult.setPage(page);
        pageResult.setPages(0);
        pageResult.setCounts(0);
        pageResult.setPagesize(pageSize);
        pageResult.setItems(messageAnnouncementList);

        return pageResult;
    }
}
