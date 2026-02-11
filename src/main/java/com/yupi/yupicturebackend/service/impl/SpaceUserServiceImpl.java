package com.yupi.yupicturebackend.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yupi.yupicturebackend.exception.BusinessException;
import com.yupi.yupicturebackend.exception.ErrorCode;
import com.yupi.yupicturebackend.exception.ThrowUtils;
import com.yupi.yupicturebackend.model.dto.spaceuser.SpaceUserAddRequest;
import com.yupi.yupicturebackend.model.dto.spaceuser.SpaceUserQueryRequest;
import com.yupi.yupicturebackend.model.entity.Space;
import com.yupi.yupicturebackend.model.entity.SpaceUser;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.enums.SpaceRoleEnum;
import com.yupi.yupicturebackend.model.vo.SpaceUserVO;
import com.yupi.yupicturebackend.model.vo.SpaceVO;
import com.yupi.yupicturebackend.service.SpaceService;
import com.yupi.yupicturebackend.service.SpaceUserService;
import com.yupi.yupicturebackend.mapper.SpaceUserMapper;
import com.yupi.yupicturebackend.service.UserService;
import org.springframework.beans.BeanUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
* @author 93758
* @description 针对表【space_user(空间用户关联)】的数据库操作Service实现
* @createDate 2026-02-07 10:56:11
*/
@Service
public class SpaceUserServiceImpl extends ServiceImpl<SpaceUserMapper, SpaceUser>
    implements SpaceUserService{
    @Resource
    private UserService userService;
    @Resource
    @Lazy
    private SpaceService spaceService;
    @Override
    public Long addSpaceUser(SpaceUserAddRequest spaceUserAddRequest){
        // 参数验证
        ThrowUtils.throwIf(spaceUserAddRequest == null, ErrorCode.PARAMS_ERROR);
        SpaceUser spaceUser = new SpaceUser();
        BeanUtils.copyProperties(spaceUserAddRequest, spaceUser);
        validSpaceUser(spaceUser,true);
        // 数据库操作
        Boolean result= this.save(spaceUser);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return spaceUser.getId();
    }
    @Override
    public void validSpaceUser(SpaceUser spaceUser, boolean add) {
        if (spaceUser == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 创建时，空间id和用户id都不能为空
        Long spaceId = spaceUser.getSpaceId();
        Long userId = spaceUser.getUserId();
        if (add) {
            ThrowUtils.throwIf(ObjectUtil.hasEmpty(spaceId, userId), ErrorCode.PARAMS_ERROR);
            User user = userService.getById(userId);
            ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR);
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR);
        }
        // 校验空间角色
        String spaceRole = spaceUser.getSpaceRole();
        SpaceRoleEnum spaceRoleEnum = SpaceRoleEnum.getEnumByValue(spaceRole);
        ThrowUtils.throwIf(spaceRole != null && spaceRoleEnum == null , ErrorCode.PARAMS_ERROR,"空间角色不存在");
    }
    @Override
    public QueryWrapper<SpaceUser> getQueryWrapper(SpaceUserQueryRequest spaceUserQueryRequest){
        QueryWrapper<SpaceUser> queryWrapper = new QueryWrapper<>();
        if (spaceUserQueryRequest == null) {
            return queryWrapper;
        }
        Long id = spaceUserQueryRequest.getId();
        Long spaceId = spaceUserQueryRequest.getSpaceId();
        Long userId = spaceUserQueryRequest.getUserId();
        String spaceRole = spaceUserQueryRequest.getSpaceRole();

        queryWrapper.eq(ObjectUtil.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjectUtil.isNotEmpty(spaceId), "spaceId", spaceId);
        queryWrapper.eq(ObjectUtil.isNotEmpty(userId), "userId", userId);
        queryWrapper.eq(ObjectUtil.isNotEmpty(spaceRole), "spaceRole", spaceRole);
        return queryWrapper;
    }
    @Override
    public SpaceUserVO getSpaceuserVO(SpaceUser spaceUser, HttpServletRequest request){
        // 对象转换为封装对象
        SpaceUserVO spaceUserVO = SpaceUserVO.objToVo(spaceUser);
        // 关联数据查询
        // 查询用户
        Long userId = spaceUser.getUserId();
        User user = userService.getById(userId);
        spaceUserVO.setUser(userService.getUserVO(user));
        // 查询空间
        Long spaceId = spaceUser.getSpaceId();
        Space space = spaceService.getById(spaceId);
        spaceUserVO.setSpace(SpaceVO.objToVo(space));
        return spaceUserVO;
    }
    @Override
    public List<SpaceUserVO> getSpaceUserVOList(List<SpaceUser> spaceUserList){
        // 判断输入列表是否为空
        if(CollUtil.isEmpty(spaceUserList)){
            return Collections.emptyList();
        }
        // 对象列表 => 封装对象列表
        List<SpaceUserVO> spaceUserVOList =  spaceUserList.stream()
                .map(SpaceUserVO :: objToVo)
                .collect(Collectors.toList());
        // 收集需要关联查询的用户ID和空间ID
        Set<Long> userIdSet = spaceUserList.stream()
                .map(SpaceUser::getUserId)
                .collect(Collectors.toSet());
        Set<Long> spaceIdSet = spaceUserList.stream().map(SpaceUser::getSpaceId).collect(Collectors.toSet());

        // 2、批量查询用户和空间
        Map<Long,List<User>> userIdUserListMap = userService.listByIds(userIdSet)
                .stream()
                .collect(Collectors.groupingBy(User::getId));
        Map<Long, List<Space>> spaceIdSpaceListMap = spaceService.listByIds(spaceIdSet)
                .stream()
                .collect(Collectors.groupingBy(Space::getId));
        // 3、填充SpaceUserVO的用户和空间
        spaceUserVOList.forEach(spaceUserVO ->{
            Long userId = spaceUserVO.getUserId();
            Long spaceId = spaceUserVO.getSpaceId();
            User user = new User();
            if(userIdUserListMap.containsKey(userId)){
                user = userIdUserListMap.get(userId).get(0);
            }
            spaceUserVO.setUser(userService.getUserVO(user));
            // 填充空间信息
            Space space = new Space();
            if(spaceIdSpaceListMap.containsKey(spaceId)){
                space = spaceIdSpaceListMap.get(spaceId).get(0);
            }
            spaceUserVO.setSpace(SpaceVO.objToVo(space));
        });
        return spaceUserVOList;
    }
}




