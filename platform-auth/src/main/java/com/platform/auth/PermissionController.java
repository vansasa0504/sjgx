package com.platform.auth;

import com.platform.common.model.Result;
import com.platform.common.security.PermissionCodes;
import com.platform.common.security.RequirePermission;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/permissions")
public class PermissionController {
    @GetMapping
    @RequirePermission("system:view")
    public Result<List<String>> list() {
        return Result.ok(PermissionCodes.ALL);
    }
}
