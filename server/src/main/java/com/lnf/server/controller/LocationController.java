package com.lnf.server.controller;

import com.lnf.server.common.Result;
import com.lnf.server.dto.LocationVO;
import com.lnf.server.service.LocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 基础数据：地点词表
 */
@RestController
@RequestMapping("/api/locations")
@RequiredArgsConstructor
public class LocationController {

    private final LocationService locationService;

    @GetMapping
    public Result<List<LocationVO>> tree(@RequestParam(required = false) String campus) {
        return Result.ok(locationService.tree(campus));
    }
}
