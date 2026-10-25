package com.xfjy.service.api;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.xfjy.service.pojo.UserLocation;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@RestController
@RequestMapping("/api/user-location")
public class UserLocationApiImpl implements UserLocationApi {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Override
    @PostMapping("/updateUserLocation")
    public String updateUserLocation(@RequestParam("userId") Long userId,
                                     @RequestParam("longitude") Double longitude,
                                     @RequestParam("latitude") Double latitude,
                                     @RequestParam("address") String address) {

        UserLocation userLocation = new UserLocation();
        userLocation.setAddress(address);
        userLocation.setUserId(userId);
        userLocation.setLocation(new GeoJsonPoint(longitude, latitude));

        // 查询用户的地理位置
        Query query = Query.query(Criteria.where("userId").is(userId));
        UserLocation ul = this.mongoTemplate.findOne(query, UserLocation.class);
        if (null == ul) {
            //新增用户位置数据
            userLocation.setId(ObjectId.get());
            userLocation.setCreated(System.currentTimeMillis());
            userLocation.setUpdated(userLocation.getCreated());
            userLocation.setLastUpdated(userLocation.getCreated());

            this.mongoTemplate.save(userLocation);

            return userLocation.getId().toHexString();
        } else {
            //更新
            Update update = Update.update("location", userLocation.getLocation())
                    .set("updated", System.currentTimeMillis())
                    .set("lastUpdated", ul.getUpdated())
                    .set("address", userLocation.getAddress());
            this.mongoTemplate.updateFirst(query, update, UserLocation.class);
        }

        return ul.getId().toHexString();
    }

}