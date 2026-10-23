package com.xfjy.server.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import com.xfjy.service.api.ESUserLocationApi;
import com.xfjy.service.api.QuanZiApi;
import com.xfjy.service.api.UserInfoApi;
import com.xfjy.service.api.VisitorsApi;
import com.xfjy.service.pojo.Publish;
import com.xfjy.service.pojo.UserInfo;
import com.xfjy.service.pojo.Visitors;
import com.xfjy.service.vo.PageInfo;
import com.xfjy.service.vo.UserInfoQueryParam;
import com.xfjy.service.vo.UserLocationVo;
import com.xfjy.server.pojo.User;
import com.xfjy.server.utils.RelativeDateFormat;
import com.xfjy.server.utils.UserThreadLocal;
import com.xfjy.server.vo.Movements;
import com.xfjy.server.vo.PageResult;
import com.xfjy.server.vo.PicUploadResult;
import com.xfjy.server.vo.VisitorsVo;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@Service
public class MovementsService {

    @Autowired
    private QuanZiApi quanZiApi;

    @Autowired
    private VisitorsApi visitorsApi;

    @Autowired
    private PicUploadService picUploadService;

    @Autowired
    private UserInfoApi userInfoApi;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private ESUserLocationApi esUserLocationApi;

    private static final Logger log = LoggerFactory.getLogger(MovementsService.class);


    /**
     * 发布动态
     *
     * @param textContent
     * @param location
     * @param longitude
     * @param latitude
     * @param multipartFile
     * @return
     */
    public String saveMovements(String textContent,
                                String location,
                                String longitude,
                                String latitude,
                                MultipartFile[] multipartFile) {

        User user = UserThreadLocal.get();

        Publish publish = new Publish();
        publish.setUserId(user.getId());
        publish.setText(textContent);
        publish.setLocationName(location);
        publish.setLatitude(latitude);
        publish.setLongitude(longitude);


        //图片上传
        List<String> imageUrls = new ArrayList<>();
        for (MultipartFile file : multipartFile) {
            PicUploadResult uploadResult = this.picUploadService.upload(file);
            imageUrls.add(uploadResult.getName());
        }

        publish.setMedias(imageUrls);

        return this.quanZiApi.savePublish(publish);
    }

    private PageResult queryPublishList(User user, Integer page, Integer pageSize) {
        PageResult pageResult = new PageResult();
        Long userId = null; //默认查询推荐动态

        PageInfo<Publish> pageInfo = null;

        if (null == user) {
            //查询推荐动态
            String key = "QUANZI_PUBLISH_RECOMMEND_" + UserThreadLocal.get().getId();
            String value = this.redisTemplate.opsForValue().get(key);
            if (StringUtils.isNotEmpty(value)) {
                //命中了数据
                String[] pids = StringUtils.split(value, ',');
                int startIndex = (page - 1) * pageSize;
                if (startIndex < pids.length) {
                    int endIndex = startIndex + pageSize - 1;
                    if (endIndex >= pids.length) {
                        endIndex = pids.length - 1;
                    }

                    List<Long> pidList = new ArrayList<>();
                    for (int i = startIndex; i <= endIndex; i++) {
                        pidList.add(Long.valueOf(pids[i]));
                    }

                    List<Publish> publishList = this.quanZiApi.queryPublishByPids(pidList);
                    pageInfo = new PageInfo<>();
                    pageInfo.setRecords(publishList);
                }

            }
        }

        if (pageInfo == null) {
            //默认查询逻辑
            if (user != null) {
                // 查询好友动态
                userId = user.getId();
            }
            pageInfo = this.quanZiApi.queryPublishList(userId, page, pageSize);
        }

        user = UserThreadLocal.get(); //查询完成后，依然还是需要获取到当前的登录用户

        pageResult.setCounts(0);
        pageResult.setPages(0);
        pageResult.setPagesize(pageSize);
        pageResult.setPage(page);

        List<Publish> records = pageInfo.getRecords();

        if (CollectionUtils.isEmpty(records)) {
            // 没有查询到动态数据
            return pageResult;
        }

        pageResult.setItems(this.fillValueToMovements(records));

        return pageResult;
    }

    private List<Movements> fillValueToMovements(List<Publish> records) {
        User user = UserThreadLocal.get();
        List<Movements> movementsList = new ArrayList<>();
        List<Long> userIds = new ArrayList<>();

        // 查询当前登录用户地理位置，用于计算与发布者的真实距离（一次查询，循环内复用）
        UserLocationVo viewerLocation = null;
        if (user != null) {
            try {
                viewerLocation = this.esUserLocationApi.queryByUserId(user.getId());
            } catch (Exception e) {
                log.warn("查询当前用户地理位置失败，动态距离将不展示: userId={}", user.getId(), e);
            }
        }

        // 批量查询评论数（type=2 表示评论，一次远程调用替代循环内逐条调用，消除 N+1）
        List<String> publishIds = new ArrayList<>();
        for (Publish record : records) {
            publishIds.add(record.getId().toHexString());
        }
        Map<String, Long> commentCountMap;
        try {
            commentCountMap = this.quanZiApi.queryCommentCountBatch(publishIds, 2);
            if (commentCountMap == null) {
                commentCountMap = Collections.emptyMap();
            }
        } catch (Exception e) {
            log.warn("批量查询评论数失败，评论数将显示为0", e);
            commentCountMap = Collections.emptyMap();
        }

        for (Publish record : records) {
            Movements movements = new Movements();

            movements.setId(record.getId().toHexString());
            movements.setUserId(record.getUserId());

            if (!userIds.contains(record.getUserId())) {
                userIds.add(record.getUserId());
            }

            String likeUserCommentKey = "QUANZI_COMMENT_LIKE_USER_" + user.getId() + "_" + movements.getId();
            movements.setHasLiked(this.redisTemplate.hasKey(likeUserCommentKey) ? 1 : 0); //是否点赞

            String likeCommentKey = "QUANZI_COMMENT_LIKE_" + movements.getId();
            String value = this.redisTemplate.opsForValue().get(likeCommentKey);
            if (StringUtils.isNotEmpty(value)) {
                movements.setLikeCount(Integer.valueOf(value)); //点赞数
            } else {
                movements.setLikeCount(0); //点赞数
            }

            String loveUserCommentKey = "QUANZI_COMMENT_LOVE_USER_" + user.getId() + "_" + movements.getId();
            movements.setHasLoved(this.redisTemplate.hasKey(loveUserCommentKey) ? 1 : 0); //是否喜欢

            String loveCommentKey = "QUANZI_COMMENT_LOVE_" + movements.getId();
            String loveValue = this.redisTemplate.opsForValue().get(loveCommentKey);
            if (StringUtils.isNotEmpty(loveValue)) {
                movements.setLoveCount(Integer.valueOf(loveValue)); //喜欢数
            } else {
                movements.setLoveCount(0); //喜欢数
            }

            // 真实距离：当前用户与发布者之间的 Geo 距离
            String distance = null;
            if (viewerLocation != null && StringUtils.isNotEmpty(record.getLongitude())
                    && StringUtils.isNotEmpty(record.getLatitude())) {
                try {
                    double meters = haversine(viewerLocation.getLatitude(), viewerLocation.getLongitude(),
                            Double.parseDouble(record.getLatitude()), Double.parseDouble(record.getLongitude()));
                    distance = String.format("%.1f公里", meters / 1000.0);
                } catch (NumberFormatException e) {
                    log.warn("动态发布者经纬度解析失败: publishId={}", movements.getId(), e);
                }
            }
            movements.setDistance(distance);

            // 真实评论数（type=2 表示评论，来自上方批量查询）
            Long commentCount = commentCountMap.get(movements.getId());
            movements.setCommentCount(commentCount == null ? 0 : commentCount.intValue());

            movements.setCreateDate(RelativeDateFormat.format(new Date(record.getCreated()))); //发布时间，如10分钟前
            movements.setTextContent(record.getText());
            movements.setImageContent(record.getMedias().toArray(new String[]{}));

            movementsList.add(movements);
        }


        UserInfoQueryParam queryParam = new UserInfoQueryParam();
        queryParam.setUserIds(userIds);
        List<UserInfo> userInfoList = this.userInfoApi.queryUserInfoList(queryParam);

        for (Movements movements : movementsList) {
            for (UserInfo userInfo : userInfoList) {
                if (movements.getUserId().longValue() == userInfo.getUserId().longValue()) {

                    movements.setTags(StringUtils.split(userInfo.getTags(), ','));
                    movements.setNickname(userInfo.getNickName());
                    movements.setGender(userInfo.getSex().name().toLowerCase());
                    movements.setAvatar(userInfo.getLogo());
                    movements.setAge(userInfo.getAge());

                    break;
                }
            }
        }

        return movementsList;
    }

    public PageResult queryRecommendPublishList(Integer page, Integer pageSize) {
        return this.queryPublishList(null, page, pageSize);
    }

    public PageResult queryUserPublishList(Integer page, Integer pageSize) {
        return this.queryPublishList(UserThreadLocal.get(), page, pageSize);
    }

    public Long likeComment(String publishId) {
        User user = UserThreadLocal.get();

        boolean bool = this.quanZiApi.saveLikeComment(user.getId(), publishId);
        if (!bool) {
            //保存失败
            return null;
        }

        // 保存成功，获取点赞数
        Long likeCount = 0L;
        String likeCommentKey = "QUANZI_COMMENT_LIKE_" + publishId;
        if (!this.redisTemplate.hasKey(likeCommentKey)) {
            Long count = this.quanZiApi.queryCommentCount(publishId, 1);
            likeCount = count;
            this.redisTemplate.opsForValue().set(likeCommentKey, String.valueOf(likeCount));
        } else {
            likeCount = this.redisTemplate.opsForValue().increment(likeCommentKey);
        }

        // 记录当前用于已经点赞
        String likeUserCommentKey = "QUANZI_COMMENT_LIKE_USER_" + user.getId() + "_" + publishId;
        this.redisTemplate.opsForValue().set(likeUserCommentKey, "1");

        return likeCount;
    }

    public Long disLikeComment(String publishId) {

        User user = UserThreadLocal.get();
        boolean bool = this.quanZiApi.removeComment(user.getId(), publishId, 1);
        if (!bool) {
            return null;
        }

        // redis中的点赞数需要减1
        String likeCommentKey = "QUANZI_COMMENT_LIKE_" + publishId;
        Long count = this.redisTemplate.opsForValue().decrement(likeCommentKey);

        // 删除该用户的标记点赞
        String likeUserCommentKey = "QUANZI_COMMENT_LIKE_USER_" + user.getId() + "_" + publishId;
        this.redisTemplate.delete(likeUserCommentKey);

        return count;
    }

    public Long loveComment(String publishId) {
        User user = UserThreadLocal.get();

        boolean bool = this.quanZiApi.saveLoveComment(user.getId(), publishId);
        if (!bool) {
            //保存失败
            return null;
        }

        // 保存成功，获取喜欢数
        Long loveCount = 0L;
        String likeCommentKey = "QUANZI_COMMENT_LOVE_" + publishId;
        if (!this.redisTemplate.hasKey(likeCommentKey)) {
            Long count = this.quanZiApi.queryCommentCount(publishId, 1);
            loveCount = count;
            this.redisTemplate.opsForValue().set(likeCommentKey, String.valueOf(loveCount));
        } else {
            loveCount = this.redisTemplate.opsForValue().increment(likeCommentKey);
        }

        // 记录当前用于已经喜欢
        String likeUserCommentKey = "QUANZI_COMMENT_LOVE_USER_" + user.getId() + "_" + publishId;
        this.redisTemplate.opsForValue().set(likeUserCommentKey, "1");

        return loveCount;
    }

    public Long unLoveComment(String publishId) {
        User user = UserThreadLocal.get();
        boolean bool = this.quanZiApi.removeComment(user.getId(), publishId, 3);
        if (!bool) {
            return null;
        }

        // redis中的喜欢数需要减1
        String likeCommentKey = "QUANZI_COMMENT_LOVE_" + publishId;
        Long count = this.redisTemplate.opsForValue().decrement(likeCommentKey);

        // 删除该用户的标记喜欢
        String likeUserCommentKey = "QUANZI_COMMENT_LOVE_USER_" + user.getId() + "_" + publishId;
        this.redisTemplate.delete(likeUserCommentKey);

        return count;
    }

    public Movements queryMovementsById(String publishId) {
        Publish publish = this.quanZiApi.queryPublishById(publishId);
        if (null == publish) {
            return null;
        }

        //查询到动态数据，数据的填充
        List<Movements> movementsList = this.fillValueToMovements(Arrays.asList(publish));
        return movementsList.get(0);
    }

    /**
     * 记录动态浏览（评分事件 type=2，由圈子服务内部发送 xfjy-quanzi 消息）
     */
    public void recordPublishView(String publishId) {
        User user = UserThreadLocal.get();
        this.quanZiApi.recordPublishView(user.getId(), publishId);
    }

    public List<VisitorsVo> queryVisitorsList() {
        User user = UserThreadLocal.get();

        // 如果redis中存在上一次查询的时间点的话就按照时间查询，否则就按照数量查询
        String redisKey = "visitors_" + user.getId();
        String data = this.redisTemplate.opsForValue().get(redisKey);
        List<Visitors> visitors = null;
        if (StringUtils.isEmpty(data)) {
            //按照数量查询
            visitors = this.visitorsApi.topVisitor(user.getId(), 6);
        } else {
            //按照时间查询
            visitors = this.visitorsApi.topVisitor(user.getId(), Long.valueOf(data));
        }

        if(CollectionUtils.isEmpty(visitors)){
            return Collections.emptyList();
        }

        List<Long> userIds = new ArrayList<>();
        for (Visitors visitor : visitors) {
            userIds.add(visitor.getVisitorUserId());
        }

        UserInfoQueryParam queryParam = new UserInfoQueryParam();
        queryParam.setUserIds(userIds);
        List<UserInfo> userInfoList = this.userInfoApi.queryUserInfoList(queryParam);

        List<VisitorsVo> result = new ArrayList<>();

        for (Visitors visitor : visitors) {
            for (UserInfo userInfo : userInfoList) {
                if(visitor.getVisitorUserId().longValue() == userInfo.getUserId().longValue()){
                    VisitorsVo visitorsVo = new VisitorsVo();

                    visitorsVo.setAge(userInfo.getAge());
                    visitorsVo.setAvatar(userInfo.getLogo());
                    visitorsVo.setGender(userInfo.getSex().name().toLowerCase());
                    visitorsVo.setId(userInfo.getUserId());
                    visitorsVo.setNickname(userInfo.getNickName());
                    visitorsVo.setTags(StringUtils.split(userInfo.getTags(), ','));
                    visitorsVo.setFateValue(visitor.getScore().intValue());

                    result.add(visitorsVo);
                    break;
                }
            }
        }

        return result;
    }

    public PageResult queryAlbumList(Long userId, Integer page, Integer pageSize) {
        PageResult pageResult = new PageResult();
        pageResult.setPage(page);
        pageResult.setPagesize(pageSize);

        PageInfo<Publish> albumPageInfo = this.quanZiApi.queryAlbumList(userId, page, pageSize);
        List<Publish> records = albumPageInfo.getRecords();

        if(CollectionUtils.isEmpty(records)){
            return pageResult;
        }
        pageResult.setItems(this.fillValueToMovements(records));
        return pageResult;
    }

    /**
     * Haversine 公式：计算两点间球面距离（单位：米）
     */
    private double haversine(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
