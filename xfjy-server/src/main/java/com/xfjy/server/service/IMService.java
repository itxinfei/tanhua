package com.xfjy.server.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xfjy.service.api.WuKongImApi;
import com.xfjy.service.api.QuanZiApi;
import com.xfjy.service.api.UserInfoApi;
import com.xfjy.service.api.UserLikeApi;
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
import com.xfjy.server.vo.ImCredential;
import com.xfjy.server.vo.MessageAnnouncement;
import com.xfjy.server.vo.MessageLike;
import com.xfjy.server.vo.PageResult;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.xfjy.server.graph.Neo4jClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class IMService {

    @Autowired
    private UsersApi usersApi;

    @Autowired
    private UserLikeApi userLikeApi;

    @Autowired
    private WuKongImApi wuKongImApi;

    @Autowired
    private UserInfoApi userInfoApi;

    @Autowired
    private QuanZiApi quanZiApi;

    @Autowired
    private AnnouncementService announcementService;

    @Autowired
    private SysConfigService sysConfigService;

    @Autowired //Neo4j 图数据库（P2 二度好友推荐）：好友关系落 FRIEND 边，不可用降级
    private Neo4jClient neo4jClient;

    private static final Logger LOGGER = LoggerFactory.getLogger(IMService.class);

    /**
     * IM token 派生盐（token = sha256(uid + salt)）。
     *
     * <p>默认值与存量已注册账号一致，生产环境通过环境变量 {@code IM_TOKEN_SALT} 覆盖；
     * 覆盖后存量用户下次取凭证时自动重新注册。</p>
     */
    @Value("${im.token-salt:_xfjy}")
    private String imTokenSalt;

    /** IM 长连接地址，环境变量 {@code IM_WS_URL} 覆盖（默认自建 WuKongIM ws 端口 5200） */
    @Value("${im.ws-url:ws://192.168.20.133:5200}")
    private String imWsUrl;

    /**
     * 获取当前用户的 IM 接入凭证（uid + token + wsUrl + provider）
     *
     * <p>token 由服务端派生并注册到 IM 通道（WuKongIM 幂等注册 / 环信 user-level token），
     * 盐值不出服务端；客户端拿到即可直连，不感知派生规则。</p>
     */
    public ImCredential getImCredential() {
        User user = UserThreadLocal.get();
        String uid = String.valueOf(user.getId());
        String password = DigestUtils.sha256Hex(uid + this.imTokenSalt);

        String token = null;
        try {
            // 先取 token（WuKongIM 取 token 即注册，幂等）；取不到说明通道侧无此账号，补注册后重试
            token = this.wuKongImApi.getUserToken(uid, password);
            if (StringUtils.isBlank(token)) {
                this.wuKongImApi.registerUser(uid, password);
                token = this.wuKongImApi.getUserToken(uid, password);
            }
        } catch (Exception e) {
            // WuKongIM 不可用时不能让 /messages/im/credential 直接 500：
            // 与客服凭证一致降级为 token=null，客户端据此走直连/离线降级，聊天之外的功能不受影响
            LOGGER.warn("获取 IM 凭证失败，已降级为空 token: uid={}", uid, e);
            token = null;
        }

        ImCredential credential = new ImCredential();
        credential.setUsername(uid);
        credential.setToken(token);
        credential.setProvider("wukongim");
        credential.setWsUrl(this.imWsUrl);
        return credential;
    }

    /**
     * 获取当前用户与某用户的单聊历史消息（IM 通道漫游，token 不出服务端）
     *
     * <p>FR-IM-03：由服务端用 IM 凭证拉取，避免前端持有 IM 管理员 token；
     * 单聊频道对称，login_uid 取当前用户即可看到双方消息。</p>
     *
     * @param peerUid 对方用户ID（字符串）
     * @param cursor  翻页游标（首次传 null 或空串）
     * @param limit   返回条数上限
     * @return {messages:[{from,content,time,type,extra}], cursor:"..."}
     */
    public Map<String, Object> getChatHistory(String peerUid, String cursor, Integer limit) {
        User user = UserThreadLocal.get();
        String fromUid = String.valueOf(user.getId());
        return this.wuKongImApi.getChatMessages(fromUid, peerUid, limit, cursor);
    }

    public Boolean contactUser(Long userId) {
        //需要做2件事：1、保存数据到MongoDB，2、好友关系注册到 IM

        User user = UserThreadLocal.get();
        // FR-IM-02 验收口径：未互相喜欢不得建立会话
        if (!Boolean.TRUE.equals(this.userLikeApi.isMutualLike(user.getId(), userId))) {
            return false;
        }

        Users users = new Users();
        users.setUserId(user.getId());
        users.setFriendId(userId);

        // 1、保存数据到MongoDB
        this.usersApi.saveUsers(users);

        // 1.5、同步好友关系到 Neo4j（FRIEND 边，幂等 MERGE 双方 User 节点 + 双向边）
        // 失败仅日志，绝不阻断好友注册主链路（Neo4jClient 内部已降级，外层再包一层兜底）
        try {
            this.neo4jClient.addFriendship(users.getUserId(), users.getFriendId());
        } catch (Exception e) {
            LOGGER.warn("Neo4j FRIEND 关系同步失败（降级跳过）, userId={}, friendId={}",
                    users.getUserId(), users.getFriendId(), e);
        }

        // 2、好友关系注册到 IM（契约调用 message-service）
        this.wuKongImApi.registerContact(users.getUserId(), users.getFriendId());

        return true;
    }

    /**
     * 当前用户能否与某用户单聊。
     *
     * <p>判定口径（「老会话放行，只拦新建」）：</p>
     * <ol>
     *   <li>双方互相喜欢 → 允许（与「添加联系人」同一口径）；</li>
     *   <li>否则查 WuKongIM 会话数据：该单聊会话已存在（历史聊过，含规则漏洞期间建立的会话）→ 允许；</li>
     *   <li>会话不存在（从未聊过）→ 拒绝。</li>
     * </ol>
     *
     * <p>会话存在性由服务端用当前登录 uid 向 WuKongIM 查询，不信任前端传参。
     * 查询失败时（依赖不可用）保持前端既有放行行为，但后端必须留痕，
     * 便于事后定位「规则失效」的时间段。</p>
     *
     * @param peerId 对方用户ID
     * @return {allowed:boolean, reason:string}
     */
    public Map<String, Object> chatPermission(Long peerId) {
        User user = UserThreadLocal.get();
        if (Boolean.TRUE.equals(this.userLikeApi.isMutualLike(user.getId(), peerId))) {
            return Map.of("allowed", true, "reason", "");
        }

        boolean conversationExists;
        try {
            conversationExists = this.wuKongImApi.conversationExists(
                    String.valueOf(user.getId()), String.valueOf(peerId));
        } catch (Exception e) {
            // fail-open：接口返回异常，前端 catch 后按放行处理；这里留 ERROR 日志，事后可据此定位规则失效时段
            LOGGER.error("[chat-permission] 会话存在性查询失败，本次接口将返回异常、前端按放行处理（规则失效）: self={}, peer={}",
                    user.getId(), peerId, e);
            throw e;
        }

        return Map.of(
                "allowed", conversationExists,
                "reason", conversationExists ? "" : "需要先互相喜欢才能开始聊天"
        );
    }

    public PageResult queryContactsList(Integer page, Integer pageSize, String keyword) {

        User user = UserThreadLocal.get();

        // 联系人关系存 Mongo、资料存 MySQL，昵称过滤必须先取全量关系再查资料，
        // 故分页在内存完成（单用户联系人量级为百，可接受）
        List<Users> usersList = this.usersApi.queryAllUsersList(user.getId());
        List<Long> friendIds = new ArrayList<>();
        for (Users users : usersList) {
            friendIds.add(users.getFriendId());
        }

        List<Contacts> contactsList = new ArrayList<>();
        if (!friendIds.isEmpty()) {
            UserInfoQueryParam queryParam = new UserInfoQueryParam();
            queryParam.setUserIds(friendIds);
            if (StringUtils.isNotEmpty(keyword)) {
                queryParam.setNickName(keyword);
            }
            List<UserInfo> userInfoList = this.userInfoApi.queryUserInfoList(queryParam);

            // 按 userId（业务ID）配对，资料表自增主键 id 不是用户ID
            // 嵌套循环匹配改 Map 查找：O(n×m) → O(n)，外层仍按 friendIds 顺序遍历，行为等价
            if (!CollectionUtils.isEmpty(userInfoList)) {
                Map<Long, UserInfo> userInfoMap = userInfoList.stream()
                        .filter(u -> u != null && u.getUserId() != null)
                        .collect(Collectors.toMap(UserInfo::getUserId, u -> u, (a, b) -> a));
                for (Long friendId : friendIds) {
                    UserInfo userInfo = userInfoMap.get(friendId);
                    if (userInfo != null) {
                        contactsList.add(toContacts(userInfo));
                    }
                }
            }
        }

        int total = contactsList.size();
        int from = Math.max(0, (page - 1) * pageSize);
        int to = Math.min(total, from + pageSize);
        List<Contacts> items = from >= to ? new ArrayList<>() : new ArrayList<>(contactsList.subList(from, to));

        PageResult pageResult = new PageResult();
        pageResult.setPage(page);
        pageResult.setPagesize(pageSize);
        pageResult.setCounts(total);
        pageResult.setPages(pageSize > 0 ? (int) Math.ceil(total / (double) pageSize) : 0);
        pageResult.setItems(items);

        return pageResult;
    }

    private Contacts toContacts(UserInfo userInfo) {
        Contacts contacts = new Contacts();
        contacts.setCity(StringUtils.substringBefore(userInfo.getCity(), "-"));
        contacts.setUserId(userInfo.getUserId().toString());
        contacts.setNickname(userInfo.getNickName());
        contacts.setGender(userInfo.getSex() == null ? "" : userInfo.getSex().name().toLowerCase());
        contacts.setAvatar(userInfo.getLogo());
        contacts.setAge(userInfo.getAge());
        return contacts;
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
        int total = pageInfo.getTotal() != null ? pageInfo.getTotal() : 0;
        pageResult.setPages(pageSize != null && pageSize > 0 ? (int) Math.ceil(total / (double) pageSize) : 0);
        pageResult.setCounts(total);
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
        if (CollectionUtils.isEmpty(userInfoList)) {
            pageResult.setItems(messageLikeList);
            return pageResult;
        }
        // 嵌套循环匹配改 Map 查找：O(n×m) → O(n)，行为等价
        Map<Long, UserInfo> userInfoMap = userInfoList.stream()
                .filter(u -> u != null && u.getUserId() != null)
                .collect(Collectors.toMap(UserInfo::getUserId, u -> u, (a, b) -> a));
        for (Comment record : records) {
            if (record.getUserId() == null) {
                continue;
            }
            UserInfo userInfo = userInfoMap.get(record.getUserId());
            if (userInfo != null) {
                MessageLike messageLike = new MessageLike();
                messageLike.setId(record.getId().toHexString());
                messageLike.setAvatar(userInfo.getLogo());
                messageLike.setNickname(userInfo.getNickName());
                messageLike.setUserId(record.getUserId());
                messageLike.setCreateDate(LocalDateTime.ofInstant(Instant.ofEpochMilli(record.getCreated()), ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));

                messageLikeList.add(messageLike);
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
        pageResult.setCounts((int) announcementPage.getTotal());
        pageResult.setPages((int) announcementPage.getPages());
        pageResult.setPagesize(pageSize);
        pageResult.setItems(messageAnnouncementList);

        return pageResult;
    }
}
