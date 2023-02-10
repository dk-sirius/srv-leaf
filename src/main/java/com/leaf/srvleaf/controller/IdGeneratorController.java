package com.leaf.srvleaf.controller;

import com.leaf.leafcore.Result;
import com.leaf.leafcore.Status;
import com.leaf.srvleaf.config.Leaf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("v1/srv-leaf-id/generator")
public class IdGeneratorController {

    @Autowired
    private Leaf leaf;

    @GetMapping("/id")
    public List<Long> generate(@RequestParam(name = "size", required = true) int size) {
        Result result = leaf.generator().id(size);
        if (result != null && result.getStatus() == Status.SUCCESS) {
            return result.getId();
        }
        return new ArrayList<>();
    }
}
