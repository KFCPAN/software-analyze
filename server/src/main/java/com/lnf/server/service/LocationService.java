package com.lnf.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lnf.server.dto.LocationVO;
import com.lnf.server.entity.Location;
import com.lnf.server.mapper.LocationMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 地点词表服务
 */
@Service
public class LocationService extends ServiceImpl<LocationMapper, Location> {

    /**
     * 地点两级树（level1 校区 → children level2 楼栋/区域），campus 可选过滤
     */
    public List<LocationVO> tree(String campus) {
        List<Location> all = list(new LambdaQueryWrapper<Location>()
                .eq(Location::getEnabled, true)
                .eq(StringUtils.hasText(campus), Location::getCampus, campus)
                .orderByAsc(Location::getLevel)
                .orderByAsc(Location::getId));

        Map<Long, List<Location>> childrenMap = all.stream()
                .filter(l -> l.getParentId() != null)
                .collect(Collectors.groupingBy(Location::getParentId));

        return all.stream()
                .filter(l -> l.getLevel() != null && l.getLevel() == 1)
                .map(l -> {
                    LocationVO vo = toVO(l);
                    vo.setChildren(childrenMap.getOrDefault(l.getId(), List.of())
                            .stream().map(this::toVO).toList());
                    return vo;
                })
                .toList();
    }

    /**
     * 地点是否存在且启用
     */
    public boolean existsEnabled(Long locationId) {
        return locationId != null && count(new LambdaQueryWrapper<Location>()
                .eq(Location::getId, locationId)
                .eq(Location::getEnabled, true)) > 0;
    }

    private LocationVO toVO(Location location) {
        LocationVO vo = new LocationVO();
        vo.setId(location.getId());
        vo.setName(location.getName());
        vo.setCampus(location.getCampus());
        vo.setLevel(location.getLevel());
        return vo;
    }
}
