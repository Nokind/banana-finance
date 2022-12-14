package com.jluzh.admin.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.BCUtil;
import cn.hutool.crypto.digest.BCrypt;
import cn.hutool.json.JSONUtil;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jluzh.admin.dto.UmsAdminParam;
import com.jluzh.admin.dto.UpdateAdminPasswordParam;
import com.jluzh.admin.dto.admin.AdminListParam;
import com.jluzh.admin.dto.admin.AdminRoleTransferVo;
import com.jluzh.admin.dto.admin.AdminSuperListVo;
import com.jluzh.admin.mapper.UmsAdminLoginLogMapper;
import com.jluzh.admin.mapper.UmsAdminRoleRelationMapper;
import com.jluzh.admin.mapper.UmsRoleMapper;
import com.jluzh.admin.model.UmsAdmin;
import com.jluzh.admin.mapper.UmsAdminMapper;
import com.jluzh.admin.model.UmsAdminLoginLog;
import com.jluzh.admin.model.UmsAdminRoleRelation;
import com.jluzh.admin.model.UmsRole;
import com.jluzh.admin.service.AuthService;
import com.jluzh.admin.service.UmsAdminRoleRelationService;
import com.jluzh.admin.service.UmsAdminService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jluzh.admin.service.UmsRoleService;
import com.jluzh.api.CommonResult;
import com.jluzh.api.ResultCode;
import com.jluzh.constant.AuthConstant;
import com.jluzh.domain.UserDto;
import com.jluzh.exception.Asserts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * <p>
 * ??????????????? ???????????????
 * </p>
 *
 * @author banana
 * @since 2022-09-15
 */
@Service
public class UmsAdminServiceImpl extends ServiceImpl<UmsAdminMapper, UmsAdmin> implements UmsAdminService {
    private static final Logger LOGGER = LoggerFactory.getLogger(UmsAdminServiceImpl.class);
    @Autowired
    private UmsAdminMapper adminMapper;
    @Autowired
    private UmsRoleMapper umsRoleMapper;
    @Autowired
    private UmsAdminRoleRelationMapper adminRoleRelationMapper;
    @Resource(name = "umsAdminLoginLogMapper")
    private UmsAdminLoginLogMapper loginLogMapper;
    @Resource(name = "umsAdminRoleRelationServiceImpl")
    private UmsAdminRoleRelationService umsAdminRoleRelationService;
    @Autowired
    private AuthService authService;
    @Autowired
    private HttpServletRequest request;

    @Override
    public UmsAdmin getAdminByUsername(String username) {
        // mysql???????????????
        UmsAdmin usrFromDB = baseMapper.selectOne(new QueryWrapper<UmsAdmin>().eq("username", username));
        if (usrFromDB != null) {
            return usrFromDB;
        }
        return null;
    }

    @Override
    public UmsAdmin register(UmsAdminParam umsAdminParam) {
        UmsAdmin umsAdmin = new UmsAdmin();
        BeanUtils.copyProperties(umsAdminParam, umsAdmin);
        umsAdmin.setCreateTime(new Date());
        umsAdmin.setStatus(1);
        // ???????????????????????????????????????
        QueryWrapper<UmsAdmin> umsAdminQueryWrapper = new QueryWrapper<>();
        umsAdminQueryWrapper.eq("username", umsAdminParam.getUsername());
        Long hasSimilar = baseMapper.selectCount(umsAdminQueryWrapper);
        if (hasSimilar > 0) { //  ??????????????????
            return null;
        }
        // ???????????????????????????
        String encodePassword = BCrypt.hashpw(umsAdmin.getPassword());
        umsAdmin.setPassword(encodePassword);
        adminMapper.insertSelective(umsAdmin);
        UmsAdminRoleRelation umsAdminRoleRelation = new UmsAdminRoleRelation();
        // ???????????????????????????????????????
        umsAdminRoleRelation.setRoleId(5L);
        umsAdminRoleRelation.setAdminId(umsAdmin.getId());
        umsAdminRoleRelationService.save(umsAdminRoleRelation);
        return umsAdmin;
    }

    @Override
    public CommonResult login(String username, String password) {
        if(StrUtil.isEmpty(username) || StrUtil.isEmpty(password)) {
            Asserts.fail("?????????????????????????????????");
        }
        Map<String, String> params = new HashMap<>();
        // ???????????????PORTAL_CLIENT_ID ??????ADMIN_CLIENT_ID
        params.put("client_id", AuthConstant.PORTAL_CLIENT_ID);
        params.put("client_secret","123456");
        params.put("grant_type","password");
        params.put("username",username);
        params.put("password",password);
        CommonResult restResult = authService.getAccessToken(params);
        // ??????200??????????????????Data??????
        if(ResultCode.SUCCESS.getCode() == restResult.getCode() && restResult.getData() != null) {
            updateLoginTimeByUsername(username);
            insertLoginLog(username);
        }
        // ??????Token
        return restResult;
    }

    /**
     * ????????????id??????????????????id
     * @param adminId
     * @return
     */
    @Override
    public List<Long> getRoleIdsByAdminId(Long adminId) {
        return adminMapper.getRoleIdsByAdminId(adminId);
    }

    /**
     * ??????????????????
     * @param username ?????????
     */

    private void insertLoginLog(String username) {
        UmsAdmin admin = getAdminByUsername(username);
        if(admin==null) return;
        UmsAdminLoginLog loginLog = new UmsAdminLoginLog();
        loginLog.setAdminId(admin.getId());
        loginLog.setCreateTime(new Date());
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attributes.getRequest();
        // ?????????requestContext?????????ip??????
        loginLog.setIp(request.getRemoteAddr());
        loginLogMapper.insert(loginLog);
    }


    /**
     * ?????????????????????????????????
     * @param username ?????????
     */

    private void updateLoginTimeByUsername(String username) {
        UmsAdmin record = new UmsAdmin();
        record.setLoginTime(new Date());
        baseMapper.update(record, new UpdateWrapper<UmsAdmin>().eq("username", username));
    }

//    @Override
//    public UmsAdmin getItem(Long id) {
//        return adminMapper.selectByPrimaryKey(id);
//    }
//
    @Override
    public Page<UmsAdmin> list(AdminListParam param) {
        Page<UmsAdmin> pageSetting = new Page<>(param.getPageNum(), param.getPageSize());
        UmsAdmin umsAdmin = new UmsAdmin();
        BeanUtils.copyProperties(param, umsAdmin);
        Page<UmsAdmin> page = adminMapper.listPage(pageSetting, umsAdmin);
        return page;
    }

    @Override
    public Page<AdminSuperListVo> superList(AdminListParam param) {
        Page<UmsAdmin> pageSetting = new Page<>(param.getPageNum(), param.getPageSize());
        UmsAdmin umsAdmin = new UmsAdmin();
        BeanUtils.copyProperties(param, umsAdmin);
        Page<UmsAdmin> page = adminMapper.listPage(pageSetting, umsAdmin);
        List<UmsAdmin> records = page.getRecords();
        Page<AdminSuperListVo> voPage= new Page<>();
        List<AdminSuperListVo> collect = records.stream().map(item -> convertToSuperListVo(item)).collect(Collectors.toList());
        BeanUtils.copyProperties(page, voPage);
        voPage.setRecords(collect);
        return voPage;
    }

    private AdminSuperListVo convertToSuperListVo(UmsAdmin admin) {
        AdminSuperListVo listVo = AdminSuperListVo.builder()
                .email(admin.getEmail())
                .icon(admin.getIcon())
                .note(admin.getNote())
                .createTime(admin.getCreateTime())
                .loginTime(admin.getLoginTime())
                .status(admin.getStatus())
                .nickName(admin.getNickName())
                .innerData(new ArrayList())
                .username(admin.getUsername())
                .id(admin.getId())
                .build();
        return listVo;

    }
//
    @Override
    public int update(Long id, UmsAdmin admin) {
        admin.setId(id);
        UmsAdmin rawAdmin = baseMapper.selectById(admin.getId());
        if(BCrypt.checkpw(admin.getPassword(), rawAdmin.getPassword())){
            // ??????????????????????????????????????????
            admin.setPassword(null);
        }else{
            // ??????????????????
            if(StrUtil.isEmpty(admin.getPassword())){
                admin.setPassword(null);
            // ?????????????????????????????????????????????
            }else{
                admin.setPassword(BCrypt.hashpw(admin.getPassword()));
            }
        }
        int count = adminMapper.updateByPrimaryKeySelective(admin);
        // TODO ???????????????????????????Redis ????????????????????????????????????????????????
        return count;
    }

    @Override
    public int deleteById(Long id) {
        int count = adminMapper.deleteById(id);
        QueryWrapper<UmsAdminRoleRelation> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("admin_id", id);
        int count1 = adminRoleRelationMapper.delete(queryWrapper);
        return count + count1;
    }

    @Override
    public int updateRole(Long adminId, List<Long> roleIds) {
        int count = (roleIds == null || roleIds.isEmpty()) ? 0 : roleIds.size();
        // ????????????????????????
        QueryWrapper<UmsAdminRoleRelation> umsAdminRoleRelationQueryWrapper = new QueryWrapper<>();
        umsAdminRoleRelationQueryWrapper.eq("admin_id", adminId);
        adminRoleRelationMapper.delete(umsAdminRoleRelationQueryWrapper);
        // ???????????????
        if (!CollectionUtils.isEmpty(roleIds)) {
            List<UmsAdminRoleRelation> list = new ArrayList<>();
            for (Long roleId : roleIds) {
                UmsAdminRoleRelation roleRelation = new UmsAdminRoleRelation();
                roleRelation.setAdminId(adminId);
                roleRelation.setRoleId(roleId);
                list.add(roleRelation);
            }
            count = adminRoleRelationMapper.insertList(list);
        }
        return count;
    }

    @Override
    public int updateRoleByUsername(String username, List<Long> roleIds) {
        int count = (roleIds == null || roleIds.isEmpty()) ? 0 : roleIds.size();
        QueryWrapper<UmsAdmin> umsAdminQueryWrapper = new QueryWrapper<>();
        umsAdminQueryWrapper.eq("username", username).select("id");
        UmsAdmin umsAdmin = baseMapper.selectOne(umsAdminQueryWrapper);
        // ????????????????????????
        QueryWrapper<UmsAdminRoleRelation> umsAdminRoleRelationQueryWrapper = new QueryWrapper<>();
        umsAdminRoleRelationQueryWrapper.eq("admin_id", umsAdmin.getId());
        adminRoleRelationMapper.delete(umsAdminRoleRelationQueryWrapper);
        if(count != 0) {
            // ???????????????
            if (!CollectionUtils.isEmpty(roleIds)) {
                List<UmsAdminRoleRelation> list = new ArrayList<>();
                for (Long roleId : roleIds) {
                    UmsAdminRoleRelation roleRelation = new UmsAdminRoleRelation();
                    roleRelation.setAdminId(umsAdmin.getId());
                    roleRelation.setRoleId(roleId);
                    list.add(roleRelation);
                }
                count = adminRoleRelationMapper.insertList(list);
            }
        }
        return count;
    }

    @Override
    public List<UmsRole> getRoleList(Long adminId) {
        return adminRoleRelationMapper.getRoleList(adminId);
    }

    @Override
    public List<UmsRole> getRoleListByAdminName(String adminName) {
        return adminRoleRelationMapper.getRoleListByAdminName(adminName);
    }

    @Override
    public List<AdminRoleTransferVo> getAdminRoleTransferVo(String adminName) {
        List<UmsRole> roleListByAdminName = adminRoleRelationMapper.getRoleListByAdminName(adminName);
        List<UmsRole> allList = umsRoleMapper.selectList(null);
        List<AdminRoleTransferVo> roleListByAdminVo = roleListByAdminName.stream().map(item -> convertToTransferVo(item)).collect(Collectors.toList());
        List<AdminRoleTransferVo> allListVo = allList.stream().map(item -> convertToTransferVo(item)).collect(Collectors.toList());
        // ????????????????????????disable????????????
        List<AdminRoleTransferVo> finalResult = allListVo.stream().map(item -> setChosenProp(item, roleListByAdminVo)).collect(Collectors.toList());
        return finalResult;
    }

    // ???condition???Key???????????????Vo???Key???????????????chosen?????????false
    private AdminRoleTransferVo setChosenProp(AdminRoleTransferVo vo, List<AdminRoleTransferVo> condition) {
        String key = vo.getKey();
        for(AdminRoleTransferVo vo1 : condition) {
            if(vo1.getKey().equals(key)) {
                vo.setChosen(true);
            }
        }
        return vo;
    }

    private AdminRoleTransferVo convertToTransferVo(UmsRole role) {
        AdminRoleTransferVo vo = AdminRoleTransferVo.builder()
                .key(role.getId().toString())
                .title(role.getName())
                .description(role.getDescription())
                .chosen(false)
                .build();
        return vo;
    }
//
//    @Override
//    public List<UmsResource> getResourceList(Long adminId) {
//        return adminRoleRelationDao.getResourceList(adminId);
//    }
//
    @Override
    public int updatePassword(UpdateAdminPasswordParam param) {
        if(StrUtil.isEmpty(param.getUsername())
                ||StrUtil.isEmpty(param.getOldPassword())
                ||StrUtil.isEmpty(param.getNewPassword())){
            return -1;
        }
        QueryWrapper<UmsAdmin> umsAdminQueryWrapper = new QueryWrapper<>();
        umsAdminQueryWrapper.eq("username", param.getUsername());
        List<UmsAdmin> umsAdmins = baseMapper.selectList(umsAdminQueryWrapper);
        if(CollUtil.isEmpty(umsAdmins)){
            return -2;
        }
        UmsAdmin umsAdmin = umsAdmins.get(0);
        if(!BCrypt.checkpw(param.getOldPassword(),umsAdmin.getPassword())){
            return -3;
        }
        umsAdmin.setPassword(BCrypt.hashpw(param.getNewPassword()));
        adminMapper.updateByPrimaryKeySelective(umsAdmin);
        return 1;
    }
//
    @Override
    public UserDto loadUserByUsername(String username){
        // ??????????????????
        UmsAdmin admin = getAdminByUsername(username);
        if (admin != null) {
            List<UmsRole> roleList = getRoleList(admin.getId());
            UserDto userDTO = new UserDto();
            BeanUtils.copyProperties(admin,userDTO);
            if(CollUtil.isNotEmpty(roleList)){
                // ?????????Role???????????????Dto
                // Exp:id_roleName 1_???????????????
                List<String> roleStrList = roleList.stream().map(item -> item.getId() + "_" + item.getName()).collect(Collectors.toList());
                userDTO.setRoles(roleStrList);
            }
            return userDTO;
        }
        return null;
    }

    @Override
    public UmsAdmin getCurrentAdmin() {
        String userStr = request.getHeader(AuthConstant.USER_TOKEN_HEADER);
        if(StrUtil.isEmpty(userStr)){
            // ??????????????????????????????????????????
            Asserts.fail(ResultCode.UNAUTHORIZED);
        }
        UserDto userDto = JSONUtil.toBean(userStr, UserDto.class);
        // TODO ????????????????????????????????????????????????????????????
        QueryWrapper<UmsAdmin> umsAdminQueryWrapper = new QueryWrapper<>();
        umsAdminQueryWrapper
                .eq("username", userDto.getUsername())
                .or()
                .eq("id", userDto.getId());
        UmsAdmin admin = baseMapper.selectOne(umsAdminQueryWrapper);
        if(admin != null){
            return admin;
        }else {
            // TODO ?????????????????????Redis??????????????????????????????????????????
            return null;
        }
    }
//
//    @Override
//    public UmsAdminCacheService getCacheService() {
//        return SpringUtil.getBean(UmsAdminCacheService.class);
//    }
//*/
}
