package com.qgyun.hltgq.hltgqsite.controller;

import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qgyun.hltgq.hltgqsite.entity.StStinfo;
import com.qgyun.hltgq.hltgqsite.service.StStinfoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/st-stinfo")
public class StStinfoController {

    @Autowired
    private StStinfoService stStinfoService;

    @GetMapping("/list")
    public List<StStinfo> list() {
        return stStinfoService.list();
    }

    @GetMapping("/page")
    public Page<StStinfo> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return (Page<StStinfo>) stStinfoService.page(new Page<StStinfo>(page, size).addOrder(OrderItem.asc("STCD")));
    }

    @GetMapping("/{stcd}")
    public StStinfo getById(@PathVariable String stcd) {
        return stStinfoService.getById(stcd);
    }

    @PostMapping
    public boolean save(@RequestBody StStinfo stStinfo) {
        return stStinfoService.saveOrUpdate(stStinfo);
    }

    @PutMapping("/{stcd}")
    public boolean update(@PathVariable String stcd, @RequestBody StStinfo stStinfo) {
        stStinfo.setStcd(stcd);
        return stStinfoService.updateById(stStinfo);
    }

    @DeleteMapping("/{stcd}")
    public boolean delete(@PathVariable String stcd) {
        return stStinfoService.removeById(stcd);
    }
}
